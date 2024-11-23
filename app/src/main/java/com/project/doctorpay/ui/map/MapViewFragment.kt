package com.project.doctorpay.ui.map

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.FusedLocationSource
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.FragmentMapviewBinding
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.Detail.HospitalDetailFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.project.doctorpay.db.FavoriteRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.withTimeout

class MapViewFragment : Fragment(), OnMapReadyCallback, HospitalDetailFragment.HospitalDetailListener {
    private var _binding: FragmentMapviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }
    private var _naverMap: NaverMap? = null
    private val naverMap get() = _naverMap!!

    private var pendingLocationUpdate: LatLng? = null
    private var isMapReady = false

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var adapter: HospitalAdapter

    // locationSource를 lazy로 초기화
    private val locationSource: FusedLocationSource by lazy {
        FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
    }

    // FusedLocationProviderClient 초기화 추가
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    // locationOverlay를 nullable로 변경
    private var locationOverlay: LocationOverlay? = null
    private var userLocation: LatLng? = null
    private var isInitialLocationSet = false
    private var isInitialDataLoaded = false
    private var isMapMoved = false

    private val markers = mutableListOf<Marker>()
    private val markerPool = mutableListOf<Marker>()
    private var lastUpdateTime = 0L

    private val favoriteRepository = FavoriteRepository()
    private var selectedMarkerBottomSheet: BottomSheetDialog? = null
    private val UPDATE_INTERVAL = 100L


    private var currentVisibleRegion: LatLngBounds? = null
    private var isDataLoading = false
    private val loadingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 데이터 중복 방지를 위한 캐시
    private var lastLoadedHospitals = mutableSetOf<String>() // ykiho 기준

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                enableLocationTracking()
            }
            else -> {
                showLocationPermissionRationale()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapviewBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(savedInstanceState)
        setupObservers()

        // 초기 데이터 로드 추가
        if (!isInitialDataLoaded) {
            loadInitialData()
        }
    }

    private fun setupViews(savedInstanceState: Bundle?) {
        try {
            binding.mapView.onCreate(savedInstanceState)
            binding.mapView.getMapAsync(this)

            // 마커 풀 초기화
            repeat(50) { markerPool.add(createMarkerStyle()) }

            setupBottomSheet()
            setupRecyclerView()
            setupReturnToLocationButton()
            setupResearchButton()
        } catch (e: IllegalStateException) {
            Log.e("MapViewFragment", "Failed to initialize MapView", e)
        }
    }


    override fun onMapReady(map: NaverMap) {
        if (!isAdded) return

        try {
            _naverMap = map
            isMapReady = true

            // 기본 지도 설정
            map.apply {
                // 기본 줌 레벨을 약간 낮춰서 더 넓은 영역 표시
                moveCamera(CameraUpdate.zoomTo(14.0))

                // 최소/최대 줌 레벨 설정
                minZoom = 10.0  // 너무 멀리 축소되지 않도록
                maxZoom = 19.0  // 충분히 확대 가능하도록

                // UI 설정
                uiSettings.apply {
                    isZoomControlEnabled = true
                    isCompassEnabled = true
                    isLocationButtonEnabled = false
                }

                // 위치 추적 모드 설정
                locationTrackingMode = LocationTrackingMode.Follow
            }

            setupMapListeners()

            if (hasLocationPermission()) {
                enableLocationTracking()
            } else {
                checkLocationPermission()
            }

            locationOverlay = map.locationOverlay.apply {
                isVisible = true
            }

            // 지도가 준비되면 대기 중인 위치 업데이트 처리
            pendingLocationUpdate?.let { location ->
                moveToLocation(location)
                pendingLocationUpdate = null
            }

            // 현재 데이터가 있다면 마커 업데이트
            viewModel.getFilteredHospitals(HospitalViewModel.MAP_VIEW).value?.let { hospitals ->
                if (hospitals.isNotEmpty()) {
                    updateMarkers(hospitals)
                }
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error in onMapReady", e)
        }
    }


    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadInitialData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!hasLocationPermission()) {
                    Log.d("MapViewFragment", "No location permission, loading default location")
                    loadDefaultLocation()
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    try {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    userLocation = LatLng(location.latitude, location.longitude)
                                    loadDataWithLocation(userLocation!!)
                                } else {
                                    Log.d("MapViewFragment", "Location is null, loading default")
                                    loadDefaultLocation()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("MapViewFragment", "Failed to get location", e)
                                loadDefaultLocation()
                            }
                    } catch (e: SecurityException) {
                        Log.e("MapViewFragment", "Security exception getting location", e)
                        loadDefaultLocation()
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error in loadInitialData", e)
                loadDefaultLocation()
            }
        }
    }

    private fun loadDataWithLocation(location: LatLng) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!hasLocationPermission()) {
                    Log.d("MapViewFragment", "No location permission when loading data")
                    return@launch
                }

                adapter.updateUserLocation(location)

                // 지도가 준비되지 않았으면 위치 업데이트를 대기 상태로 저장
                if (!isMapReady) {
                    Log.d("MapViewFragment", "Map not ready, saving pending location update")
                    pendingLocationUpdate = location
                    return@launch
                }

                moveToLocation(location)

                // 데이터 로드는 IO 스레드에서 실행
                withContext(Dispatchers.IO) {
                    viewModel.fetchNearbyHospitals(
                        viewId = HospitalViewModel.MAP_VIEW,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radius = 3000,
                        forceRefresh = true
                    )
                }
                isInitialDataLoaded = true
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error loading data with location", e)
                showError("데이터를 불러오는 중 오류가 발생했습니다")
            }
        }
    }

    private fun moveToLocation(location: LatLng) {
        try {
            if (!isMapReady) {
                Log.d("MapViewFragment", "Map not ready for camera move")
                pendingLocationUpdate = location
                return
            }

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    _naverMap?.moveCamera(CameraUpdate.scrollTo(location))
                } catch (e: Exception) {
                    Log.e("MapViewFragment", "Error moving camera to location", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error in moveToLocation", e)
        }
    }

    private fun loadHospitalsForVisibleRegion() {
        if (isDataLoading) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val visibleBounds = naverMap.contentBounds
                val center = visibleBounds.center

                // 현재 위치 업데이트
                adapter.updateUserLocation(center)

                // 데이터 로드 전 상태 초기화
                viewModel.resetPagination(HospitalViewModel.MAP_VIEW)

                val radius = calculateRadius(visibleBounds).toInt()
                    .coerceAtMost(5000) // 최대 5km로 제한

                withContext(Dispatchers.IO) {
                    viewModel.fetchNearbyHospitals(
                        viewId = HospitalViewModel.MAP_VIEW,
                        latitude = center.latitude,
                        longitude = center.longitude,
                        radius = radius,
                        forceRefresh = true
                    )
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error loading hospitals for region", e)
                showError("데이터를 불러오는 중 오류가 발생했습니다")
            }
        }
    }

    private fun setupMapUI() {
        naverMap.apply {
            locationTrackingMode = LocationTrackingMode.Follow
            uiSettings.apply {
                isLocationButtonEnabled = false
                isZoomControlEnabled = true
                isCompassEnabled = true
            }
        }
    }


    private fun updateHospitalsBasedOnLocation(location: LatLng) {
        viewModel.fetchNearbyHospitals(
            viewId = HospitalViewModel.MAP_VIEW,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private fun setupMapListeners() {
        if (!isMapReady) return

        var lastLoadTime = 0L
        val loadDebounceTime = 1000L // 1초 디바운스

        _naverMap?.apply {
            addOnCameraIdleListener {
                if (isInitialLocationSet) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLoadTime > loadDebounceTime) {
                        lastLoadTime = currentTime
                        showResearchButton()
                        loadHospitalsForVisibleRegion()
                    }
                }
            }

            addOnCameraChangeListener { _, _ ->
                if (isInitialLocationSet) {
                    isMapMoved = true
                    hideResearchButton()
                }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 필터링된 병원 데이터 관찰
            viewModel.getFilteredHospitals(HospitalViewModel.MAP_VIEW).collect { hospitals ->
                if (hospitals.isNotEmpty()) {
                    Log.d("MapViewFragment", "Received ${hospitals.size} hospitals")
                    // 거리순으로 정렬
                    val sortedHospitals = sortHospitalsByDistance(hospitals)
                    // 병원 목록 업데이트
                    updateHospitalsList(sortedHospitals)
                    // 마커 업데이트 명시적 호출
                    updateMarkers(sortedHospitals)
                }
            }
        }

        // 로딩 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getIsLoading(HospitalViewModel.MAP_VIEW).collect { isLoading ->
                isDataLoading = isLoading
            }
        }

        // 에러 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.MAP_VIEW).collect { error ->
                error?.let { showError(it) }
            }
        }
    }



    private fun showMessage(message: String) {
        if (!isAdded) return  // Fragment가 액티비티에 붙어있지 않으면 리턴

        view?.let { view ->
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showToast(message: String) {
        try {
            activity?.runOnUiThread {
                Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Failed to show toast", e)
        }
    }

    private fun showError(message: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            view?.let { view ->
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }



    private fun updateMarkers(hospitals: List<HospitalInfo>) {
        if (!isMapReady || !shouldUpdateMarkers()) {
            Log.d("MapViewFragment", "Map not ready or should not update markers")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val currentMap = _naverMap ?: run {
                Log.d("MapViewFragment", "NaverMap is null")
                return@launch
            }

            try {
                val visibleBounds = withContext(Dispatchers.Main) {
                    currentMap.contentBounds
                }

                Log.d("MapViewFragment", "Processing ${hospitals.size} hospitals for markers")

                // 화면에 보이는 병원만 필터링 (최대 100개로 제한)
                val visibleHospitals = hospitals
                    .distinctBy { it.ykiho }
                    .filter { hospital ->
                        val position = LatLng(hospital.latitude, hospital.longitude)
                        isValidCoordinate(hospital.latitude, hospital.longitude) &&
                                visibleBounds.contains(position)
                    }
                    .take(100)

                Log.d("MapViewFragment", "Filtered to ${visibleHospitals.size} visible hospitals")

                withContext(Dispatchers.Main) {
                    // 기존 마커 제거
                    markers.forEach { marker ->
                        marker.map = null
                    }
                    markers.clear()

                    // 새 마커 추가
                    visibleHospitals.forEach { hospital ->
                        try {
                            val marker = Marker().apply {
                                position = LatLng(hospital.latitude, hospital.longitude)
                                captionText = hospital.name
                                tag = hospital
                                map = currentMap
                                width = 40
                                height = 60
                                captionTextSize = 14f
                                setOnClickListener {
                                    showHospitalDetail(it.tag as HospitalInfo)
                                    true
                                }
                            }
                            markers.add(marker)
                        } catch (e: Exception) {
                            Log.e("MapViewFragment", "Error creating marker for hospital: ${hospital.name}", e)
                        }
                    }

                    Log.d("MapViewFragment", "Added ${markers.size} markers to map")
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error updating markers", e)
            }
        }
    }


    private fun calculateRadius(bounds: LatLngBounds): Double {
        val center = bounds.center
        val northeast = bounds.northEast
        return center.distanceTo(northeast)
    }

    private fun updateBottomSheetState(hospitals: List<HospitalInfo>) {
        try {
            binding.apply {
                hospitalRecyclerView.isVisible = hospitals.isNotEmpty()
            }

            if (hospitals.isNotEmpty() &&
                bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error updating bottom sheet state", e)
        }
    }

    // 거리 계산 유틸리티 함수
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    // 좌표 유효성 검사 함수 업데이트
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        val isValid = latitude != 0.0 && longitude != 0.0 &&
                latitude >= 33.0 && latitude <= 43.0 && // 한국 위도 범위
                longitude >= 124.0 && longitude <= 132.0 // 한국 경도 범위

        if (!isValid) {
            Log.d("MapViewFragment", "Invalid coordinate: lat=$latitude, lng=$longitude")
        }
        return isValid
    }


    private fun showResearchButton() {
        binding.researchButton.visibility = View.VISIBLE
    }

    private fun hideResearchButton() {
        binding.researchButton.visibility = View.GONE
    }

    private fun setupReturnToLocationButton() {
        binding.returnToLocationButton.setOnClickListener {
            userLocation?.let { position ->
                adapter.updateUserLocation(position)  // 현재 위치로 돌아갈 때도 어댑터에 위치 전달
                naverMap.moveCamera(CameraUpdate.scrollTo(position))
                isMapMoved = false
                hideResearchButton()
                updateHospitalsBasedOnLocation(position)
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.apply {
            isFitToContents = false
            halfExpandedRatio = 0.5f
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateExpandButtonIcon(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.expandButton.setOnClickListener { toggleBottomSheetState() }
    }

    private fun showHospitalDetail(hospital: HospitalInfo) {
        val hospitalDetailFragment = HospitalDetailFragment.newInstance(
            hospitalId = hospital.name,
            isFromMap = true,
            category = hospital.departmentCategories.firstOrNull() ?: ""
        )

        // Bundle 대신 직접 HospitalInfo 설정
        hospitalDetailFragment.setHospitalInfo(hospital)
        hospitalDetailFragment.setHospitalDetailListener(this)

        childFragmentManager.beginTransaction()
            .replace(R.id.hospitalDetailContainer, hospitalDetailFragment)
            .addToBackStack(null)
            .commit()

        binding.hospitalRecyclerView.visibility = View.GONE
        binding.hospitalDetailContainer.visibility = View.VISIBLE

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
    }

    override fun onBackFromHospitalDetail() {
        showHospitalList()
    }

    private fun showHospitalList() {
        binding.hospitalRecyclerView.visibility = View.VISIBLE
        binding.hospitalDetailContainer.visibility = View.GONE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupResearchButton() {
        binding.researchButton.setOnClickListener {
            try {
                val mapCenter = naverMap.cameraPosition.target
                Log.d("MapViewFragment", "Research button clicked. Center: ${mapCenter.latitude}, ${mapCenter.longitude}")

                // 현재 위치 업데이트
                adapter.updateUserLocation(mapCenter)
                userLocation = mapCenter // userLocation도 업데이트

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // 페이지네이션 초기화
                        viewModel.resetPagination(HospitalViewModel.MAP_VIEW)

                        // 현재 보이는 영역의 반경 계산
                        val radius = calculateRadius(naverMap.contentBounds)
                            .toInt()
                            .coerceAtMost(5000) // 최대 5km로 제한

                        // 데이터 로드는 IO 스레드에서 실행
                        withContext(Dispatchers.IO) {
                            viewModel.fetchNearbyHospitals(
                                viewId = HospitalViewModel.MAP_VIEW,
                                latitude = mapCenter.latitude,
                                longitude = mapCenter.longitude,
                                radius = radius,
                                forceRefresh = true // 강제 새로고침
                            )
                        }

                        // UI 상태 업데이트
                        hideResearchButton()
                        isMapMoved = false

                    } catch (e: Exception) {
                        Log.e("MapViewFragment", "Error refreshing hospitals", e)
                        showError("데이터를 불러오는 중 오류가 발생했습니다")
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error in research button click", e)
            }
        }
    }


    private fun updateBottomSheet(hospitals: List<HospitalInfo>) {
        adapter.submitList(hospitals)
        if (hospitals.isEmpty()) {
            binding.hospitalRecyclerView.visibility = View.GONE
        } else {
            binding.hospitalRecyclerView.visibility = View.VISIBLE
        }
    }



    private fun sortHospitalsByDistance(hospitals: List<HospitalInfo>): List<HospitalInfo> {
        val currentLocation = userLocation
        return if (currentLocation != null) {
            hospitals.sortedBy { hospital ->
                val results = FloatArray(1)
                try {
                    Location.distanceBetween(
                        currentLocation.latitude, currentLocation.longitude,
                        hospital.latitude, hospital.longitude,
                        results
                    )
                    results[0]
                } catch (e: Exception) {
                    Log.e("MapViewFragment", "Error calculating distance for ${hospital.name}", e)
                    Float.MAX_VALUE // 오류 발생시 가장 멀리 정렬
                }
            }
        } else {
            hospitals
        }
    }


    private fun checkLocationPermission() {
        when {
            hasLocationPermission() -> {
                enableLocationTracking()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            else -> {
                requestLocationPermissions()
            }
        }
    }


    private fun enableLocationTracking() {
        if (!isAdded || !hasLocationPermission()) {
            Log.d("MapViewFragment", "Cannot enable tracking: no permission or fragment not added")
            return
        }

        try {
            naverMap.locationTrackingMode = LocationTrackingMode.Follow
            binding.returnToLocationButton.visibility = View.VISIBLE
            locationOverlay?.isVisible = true

            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000
                fastestInterval = 5000
                maxWaitTime = 15000
            }

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (!hasLocationPermission()) {
                        Log.d("MapViewFragment", "Location permission lost during tracking")
                        return
                    }

                    try {
                        locationResult.lastLocation?.let { location ->
                            val newUserLocation = LatLng(location.latitude, location.longitude)
                            userLocation = newUserLocation

                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                try {
                                    adapter.updateUserLocation(newUserLocation)
                                    if (!isInitialLocationSet) {
                                        isInitialLocationSet = true
                                        naverMap.moveCamera(CameraUpdate.scrollTo(newUserLocation))
                                        loadDataWithLocation(newUserLocation)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MapViewFragment", "Error updating UI with new location", e)
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e("MapViewFragment", "Security exception during location update", e)
                    }
                }
            }

            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener { e ->
                    Log.e("MapViewFragment", "Failed to request location updates", e)
                    loadDefaultLocation()
                }
            } catch (e: SecurityException) {
                Log.e("MapViewFragment", "Security exception requesting location updates", e)
                loadDefaultLocation()
            }

        } catch (e: SecurityException) {
            Log.e("MapViewFragment", "Security exception in enableLocationTracking", e)
            loadDefaultLocation()
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error enabling location tracking", e)
            loadDefaultLocation()
        }
    }


    private fun loadDefaultLocation() {
        val defaultLocation = LatLng(37.5666805, 127.0784147) // 서울 중심부
        userLocation = defaultLocation

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                adapter.updateUserLocation(defaultLocation)
                naverMap.moveCamera(CameraUpdate.scrollTo(defaultLocation))

                withTimeout(30000) { // 30초 타임아웃
                    viewModel.fetchNearbyHospitals(
                        viewId = HospitalViewModel.MAP_VIEW,
                        latitude = defaultLocation.latitude,
                        longitude = defaultLocation.longitude,
                        radius = 3000,
                        forceRefresh = true // 강제 새로고침 추가
                    )
                }
                isInitialDataLoaded = true
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error loading default location data", e)
                showErrorDialog()
            }
        }
    }

    private fun showErrorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("오류")
            .setMessage("위치 정보를 불러오는데 실패했습니다. 다시 시도하시겠습니까?")
            .setPositiveButton("재시도") { _, _ ->
                checkLocationPermission()
            }
            .setNegativeButton("기본 위치 사용") { _, _ ->
                loadDefaultLocation()
            }
            .show()
    }

    private fun requestLocationPermissions() {
        if (!isAdded) return

        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("위치 권한 필요")
            .setMessage("주변 병원 확인을 위해 위치 권한이 필요합니다.")
            .setPositiveButton("권한 요청") { _, _ ->
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter(
            onItemClick = { hospital ->
                showHospitalDetail(hospital)
            },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )

        binding.hospitalRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MapViewFragment.adapter
            setHasFixedSize(true)
        }

        userLocation?.let { location ->
            adapter.updateUserLocation(location)
        }
    }

    private fun addHospitalMarkers(hospitals: List<HospitalInfo>) {
        if (!shouldUpdateMarkers()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val visibleBounds = withContext(Dispatchers.Main) {
                naverMap.contentBounds
            }

            val visibleHospitals = hospitals.filter {
                val position = LatLng(it.latitude, it.longitude)
                visibleBounds.contains(position)
            }

            withContext(Dispatchers.Main) {
                markers.forEach { recycleMarker(it) }
                markers.clear()

                visibleHospitals.forEachIndexed { index, hospital ->
                    if (isValidCoordinate(hospital.latitude, hospital.longitude)) {
                        getMarkerFromPool().apply {
                            position = LatLng(hospital.latitude, hospital.longitude)
                            captionText = hospital.name
                            tag = hospital
                            map = naverMap
                            setOnClickListener {
                                val selectedHospital = it.tag as HospitalInfo
                                // 마커 클릭 시 해당 병원을 리스트의 맨 위로 스크롤하고 bottom sheet를 펼침
                                scrollToHospital(selectedHospital)
                                true
                            }
                            markers.add(this)
                        }
                    }
                }
            }
        }
    }

    private fun scrollToHospital(hospital: HospitalInfo) {
        // 현재 표시된 병원 목록에서 선택된 병원의 위치를 찾음
        val currentList = adapter.currentList
        val position = currentList.indexOfFirst { it.ykiho == hospital.ykiho }

        if (position != -1) {
            // Bottom sheet를 반 펼침 상태로 확장
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

            // 해당 위치로 스크롤
            (binding.hospitalRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)

            // 스크롤 후 약간의 딜레이를 주고 해당 아이템을 하이라이트
            binding.hospitalRecyclerView.postDelayed({
                binding.hospitalRecyclerView.findViewHolderForAdapterPosition(position)?.itemView?.apply {
                    alpha = 0.5f
                    animate().alpha(1.0f).setDuration(500).start()
                }
            }, 100)
        }
    }


    private fun updateHospitalsList(hospitals: List<HospitalInfo>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val sortedHospitals = userLocation?.let { currentLocation ->
                        hospitals.sortedBy { hospital ->
                            calculateDistance(
                                currentLocation.latitude, currentLocation.longitude,
                                hospital.latitude, hospital.longitude
                            )
                        }
                    } ?: hospitals

                    withContext(Dispatchers.Main) {
                        adapter.submitList(sortedHospitals)
                        updateBottomSheetState(sortedHospitals)
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error updating hospitals list", e)
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _naverMap = null
        isMapReady = false
        pendingLocationUpdate = null
        locationOverlay = null
        markers.forEach { recycleMarker(it) }
        markers.clear()
        markerPool.clear()
        _binding = null
    }

    private fun toggleBottomSheetState() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            BottomSheetBehavior.STATE_HALF_EXPANDED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun updateExpandButtonIcon(state: Int) {
        binding.expandButton.setImageResource(
            when (state) {
                BottomSheetBehavior.STATE_EXPANDED -> android.R.drawable.arrow_down_float
                BottomSheetBehavior.STATE_HALF_EXPANDED -> android.R.drawable.arrow_up_float
                else -> android.R.drawable.arrow_up_float
            }
        )
    }

    // Lifecycle methods
    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
        private const val UPDATE_INTERVAL = 100L
    }


    private fun createMarkerStyle(): Marker {
        return try {
            Marker().apply {
                width = 40
                height = 60
                captionTextSize = 14f
                captionMinZoom = 12.0
                captionMaxZoom = 16.0
                isHideCollidedCaptions = true
                isHideCollidedMarkers = true
                minZoom = 10.0
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error creating marker style", e)
            Marker() // 기본 마커라도 반환
        }
    }

    private fun getMarkerFromPool(): Marker {
        return try {
            if (markerPool.isEmpty()) {
                createMarkerStyle()
            } else {
                markerPool.removeAt(0)
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error getting marker from pool", e)
            createMarkerStyle() // 풀에서 가져오기 실패시 새로 생성
        }
    }

    private fun recycleMarker(marker: Marker) {
        try {
            marker.map = null
            markerPool.add(marker)
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error recycling marker", e)
        }
    }


    // shouldUpdateMarkers 함수 수정
    private fun shouldUpdateMarkers(): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            lastUpdateTime = currentTime
            true
        } else {
            false
        }
    }

}