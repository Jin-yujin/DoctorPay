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
import kotlinx.coroutines.withTimeout

class MapViewFragment : Fragment(), OnMapReadyCallback, HospitalDetailFragment.HospitalDetailListener {
    private var _binding: FragmentMapviewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }
    private lateinit var naverMap: NaverMap
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
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                enableLocationTracking()
            }
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
    }

    private fun setupViews(savedInstanceState: Bundle?) {
        binding.apply {
            // 로딩 프로그레스 바 추가
            loadingProgress.visibility = View.GONE

            // 에러 메시지 뷰 추가
            errorView.apply {
                visibility = View.GONE
                setOnClickListener {
                    // 에러 뷰 클릭 시 재시도
                    visibility = View.GONE
                    loadHospitalsForVisibleRegion()
                }
            }

            // 빈 데이터 메시지 뷰 추가
            emptyView.visibility = View.GONE
        }

        // 기존 setupViews 로직
        try {
            binding.mapView.onCreate(savedInstanceState)
            binding.mapView.getMapAsync(this)
            setupBottomSheet()
            setupRecyclerView()
            setupReturnToLocationButton()
            setupResearchButton()
        } catch (e: IllegalStateException) {
            Log.e("MapViewFragment", "Failed to initialize MapView", e)
            handleError(e)
        }
    }


    override fun onMapReady(map: NaverMap) {
        if (!isAdded) return

        try {
            naverMap = map
            naverMap.locationSource = locationSource

            setupMapUI()
            checkLocationPermission() // 위치 권한 체크 추가

            locationOverlay = naverMap.locationOverlay.apply {
                isVisible = true
            }

            setupMapListeners()
            checkLocationPermission()
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error in onMapReady", e)
        }
    }

    private fun loadInitialData() {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        userLocation = LatLng(it.latitude, it.longitude)
                        adapter.updateUserLocation(userLocation!!)
                        viewModel.fetchNearbyHospitals(
                            viewId = HospitalViewModel.MAP_VIEW,
                            latitude = it.latitude,
                            longitude = it.longitude
                        )
                    } ?: loadDefaultLocation()
                }.addOnFailureListener {
                    loadDefaultLocation()
                }
            } catch (e: SecurityException) {
                loadDefaultLocation()
            }
        } else {
            loadDefaultLocation()
        }
    }

    private fun loadHospitalsForVisibleRegion() {
        if (isDataLoading) return

        val visibleBounds = naverMap.contentBounds
        val center = visibleBounds.center

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withTimeout(30000) { // 30초 타임아웃 추가
                    binding.loadingProgress.visibility = View.VISIBLE // 로딩 인디케이터 표시

                    // 현재 위치 업데이트
                    adapter.updateUserLocation(center)

                    // 데이터 초기화 및 새로운 데이터 로드 시도
                    viewModel.resetPagination(HospitalViewModel.MAP_VIEW)

                    val radius = calculateRadius(visibleBounds).toInt()
                        .coerceAtMost(5000) // 최대 5km로 제한

                    // 재시도 로직 추가
                    var retryCount = 0
                    var success = false

                    while (retryCount < 3 && !success) {
                        try {
                            viewModel.fetchNearbyHospitals(
                                viewId = HospitalViewModel.MAP_VIEW,
                                latitude = center.latitude,
                                longitude = center.longitude,
                                radius = radius
                            )
                            success = true
                        } catch (e: Exception) {
                            retryCount++
                            if (retryCount < 3) delay(2000) // 재시도 전 2초 대기
                        }
                    }

                    if (!success) {
                        throw Exception("Failed to fetch hospitals after 3 attempts")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleError(e)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.loadingProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun handleError(error: Exception) {
        val errorMessage = when {
            error.message?.contains("failed to connect") == true ->
                "서버 연결에 실패했습니다. 네트워크 상태를 확인해주세요."
            error.message?.contains("timeout") == true ->
                "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
            else -> "데이터를 불러오는 중 오류가 발생했습니다."
        }

        binding.errorView.apply {
            visibility = View.VISIBLE
            text = errorMessage
        }

        // 오류 발생 시 캐시된 데이터 사용
        viewModel.getFilteredHospitals(HospitalViewModel.MAP_VIEW).value?.let { cachedHospitals ->
            if (cachedHospitals.isNotEmpty()) {
                updateHospitalsList(cachedHospitals)
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
        naverMap.addOnCameraIdleListener {
            if (isInitialLocationSet) {
                showResearchButton()
                loadHospitalsForVisibleRegion()
            }
        }

        naverMap.addOnCameraChangeListener { _, _ ->
            if (isInitialLocationSet) {
                isMapMoved = true
                hideResearchButton()
            }
        }
    }
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getFilteredHospitals(HospitalViewModel.MAP_VIEW).collect { hospitals ->
                if (hospitals.isNotEmpty()) {
                    // 거리순으로 정렬하고 즐겨찾기 상태 확인
                    val sortedHospitals = sortHospitalsByDistance(hospitals)
                    updateHospitalsList(sortedHospitals)
                    updateMarkers(sortedHospitals)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getIsLoading(HospitalViewModel.MAP_VIEW).collect { isLoading ->
                isDataLoading = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.MAP_VIEW).collect { error ->
                error?.let { showError(it) }
            }
        }
    }


    private fun showNoDataMessage() {
        Toast.makeText(context, "주변에 병원 정보가 없습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun showError(error: String) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    private fun updateMarkers(hospitals: List<HospitalInfo>) {
        if (!this::naverMap.isInitialized || !shouldUpdateMarkers()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val visibleBounds = withContext(Dispatchers.Main) {
                naverMap.contentBounds
            }

            // 화면에 보이는 병원만 필터링 (최대 100개로 제한)
            val visibleHospitals = hospitals
                .distinctBy { it.ykiho }
                .filter { hospital ->
                    val position = LatLng(hospital.latitude, hospital.longitude)
                    visibleBounds.contains(position) &&
                            isValidCoordinate(hospital.latitude, hospital.longitude)
                }
                .take(100)

            withContext(Dispatchers.Main) {
                try {
                    // 기존 마커 재활용
                    markers.forEach { recycleMarker(it) }
                    markers.clear()

                    // 배치 처리로 마커 추가
                    visibleHospitals.chunked(20).forEach { batch ->
                        batch.forEach { hospital ->
                            getMarkerFromPool().apply {
                                position = LatLng(hospital.latitude, hospital.longitude)
                                captionText = hospital.name
                                tag = hospital
                                map = naverMap
                                setOnClickListener {
                                    showHospitalDetail(it.tag as HospitalInfo)
                                    true
                                }
                                markers.add(this)
                            }
                        }
                        delay(16) // 프레임 드롭 방지
                    }
                } catch (e: Exception) {
                    Log.e("MapViewFragment", "Error updating markers", e)
                }
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

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180
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
            val mapCenter = naverMap.cameraPosition.target
            adapter.updateUserLocation(mapCenter)
            viewModel.resetPagination(HospitalViewModel.MAP_VIEW)  // viewId 추가
            viewModel.fetchNearbyHospitals(
                viewId = HospitalViewModel.MAP_VIEW,  // viewId 추가
                latitude = mapCenter.latitude,
                longitude = mapCenter.longitude
            )
            hideResearchButton()
            isMapMoved = false
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
        if (!isAdded) return

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
        if (!isAdded || !hasLocationPermission()) return

        try {
            naverMap.locationTrackingMode = LocationTrackingMode.Follow
            binding.returnToLocationButton.visibility = View.VISIBLE
            locationOverlay?.isVisible = true

            // 위치 요청 설정
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000 // 10초
                fastestInterval = 5000 // 5초
                maxWaitTime = 15000 // 15초
            }

            // 위치 콜백
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val newUserLocation = LatLng(location.latitude, location.longitude)
                        userLocation = newUserLocation
                        adapter.updateUserLocation(newUserLocation)

                        if (!isInitialLocationSet) {
                            isInitialLocationSet = true
                            naverMap.moveCamera(CameraUpdate.scrollTo(newUserLocation))
                            updateHospitalsBasedOnLocation(newUserLocation)
                        }
                    }
                }
            }

            // 안전하게 위치 업데이트 요청
            if (hasLocationPermission()) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener { e ->
                    Log.e("MapViewFragment", "Location updates failed", e)
                    loadDefaultLocation()
                }
            } else {
                loadDefaultLocation()
            }

        } catch (e: SecurityException) {
            Log.e("MapViewFragment", "Location permission denied", e)
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

                // 기본 위치에서 데이터 로드
                withTimeout(30000) { // 30초 타임아웃
                    viewModel.fetchNearbyHospitals(
                        viewId = HospitalViewModel.MAP_VIEW,
                        latitude = defaultLocation.latitude,
                        longitude = defaultLocation.longitude,
                        radius = 3000 // 반경 줄임
                    )
                }
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

    private fun requestLocationPermissions() {
        if (isAdded) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
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
                        binding.errorView.visibility = View.GONE // 성공 시 에러 뷰 숨김
                        adapter.submitList(sortedHospitals)
                        updateBottomSheetState(sortedHospitals)

                        // 데이터가 없는 경우 처리
                        if (sortedHospitals.isEmpty()) {
                            binding.emptyView.apply {
                                visibility = View.VISIBLE
                                text = "주변에 병원 정보가 없습니다."
                            }
                        } else {
                            binding.emptyView.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error updating hospitals list", e)
                handleError(e)
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        locationOverlay = null  // locationOverlay 해제
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
        return Marker().apply {
            width = 40
            height = 60
            captionTextSize = 14f
            captionMinZoom = 12.0
            captionMaxZoom = 16.0
            isHideCollidedCaptions = true
            isHideCollidedMarkers = true
            minZoom = 10.0
        }
    }

    private fun getMarkerFromPool(): Marker {
        return if (markerPool.isEmpty()) {
            createMarkerStyle()
        } else {
            markerPool.removeAt(0)
        }
    }

    private fun recycleMarker(marker: Marker) {
        marker.map = null
        markerPool.add(marker)
    }

    private fun shouldUpdateMarkers(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            lastUpdateTime = currentTime
            return true
        }
        return false
    }
}