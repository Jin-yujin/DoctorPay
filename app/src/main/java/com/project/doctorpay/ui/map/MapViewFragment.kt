package com.project.doctorpay.ui.map

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewFragment : Fragment(), OnMapReadyCallback, HospitalDetailFragment.HospitalDetailListener {

    private var _binding: FragmentMapviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var naverMap: NaverMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }
    private lateinit var adapter: HospitalAdapter

    // locationSource를 lazy로 초기화
    private val locationSource: FusedLocationSource by lazy {
        FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
    }

    private lateinit var locationOverlay: LocationOverlay

    private var userLocation: LatLng? = null
    private var currentVisibleRegion: LatLngBounds? = null
    private var isDataLoading = false
    private val loadingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 데이터 중복 방지를 위한 캐시
    private var lastLoadedHospitals = mutableSetOf<String>() // ykiho 기준


    private var isInitialLocationSet = false
    private var isMapMoved = false

    private val markers = mutableListOf<Marker>()

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

    // 마커 스타일 관련 추가
    private val markerPool = mutableListOf<Marker>()
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL = 100L


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(savedInstanceState)
        setupObservers()
    }

    private fun setupViews(savedInstanceState: Bundle?) {
        try {
            binding.mapView.onCreate(savedInstanceState)
            binding.mapView.getMapAsync(this)

            // 마커 풀 초기화 - 성능 최적화
            repeat(50) { markerPool.add(createMarkerStyle()) }
            
            locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
            setupBottomSheet()
            setupRecyclerView()
            setupObservers()
            setupReturnToLocationButton()
            setupResearchButton()
        } catch (e: IllegalStateException) {
            Log.e("MapViewFragment", "Failed to initialize MapView", e)
        }
    }

    override fun onMapReady(map: NaverMap) {
        if (!isAdded) return

        try {
            naverMap = map
            naverMap.locationSource = locationSource  // 안전하게 접근 가능

        try {
            // 위치 추적 모드 설정 전에 안전성 체크
            if (isAdded && activity != null) {
                naverMap.locationTrackingMode = LocationTrackingMode.Follow
            }
        } catch (e: IllegalStateException) {
            Log.e("MapViewFragment", "Failed to set location tracking mode", e)
        }

            // 위치 추적 모드 설정 전에 안전성 체크
            if (isAdded && activity != null) {
                naverMap.locationTrackingMode = LocationTrackingMode.Follow
            }

            locationOverlay = naverMap.locationOverlay
            locationOverlay.isVisible = true

            setupMapListeners()
            checkLocationPermission()
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error in onMapReady", e)
        }
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
            viewModel.filteredHospitals.collect { hospitals ->
                if (hospitals.isNotEmpty()) {
                    updateHospitalsList(hospitals)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                isDataLoading = isLoading
            }
        }
    }


    private fun updateHospitalsList(hospitals: List<HospitalInfo>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // ykiho 기준으로 중복 제거
                val distinctHospitals = hospitals.distinctBy { it.ykiho }
                    .filter { hospital ->
                        isValidCoordinate(hospital.latitude, hospital.longitude)
                    }

                // 정렬 및 UI 업데이트
                withContext(Dispatchers.Default) {
                    val sortedHospitals = userLocation?.let { currentLocation ->
                        distinctHospitals.sortedBy { hospital ->
                            calculateDistance(
                                currentLocation.latitude, currentLocation.longitude,
                                hospital.latitude, hospital.longitude
                            )
                        }
                    } ?: distinctHospitals

                    withContext(Dispatchers.Main) {
                        adapter.submitList(sortedHospitals)
                        updateMarkers(sortedHospitals)  // 통합된 마커 업데이트 함수 사용
                        updateBottomSheetState(sortedHospitals)
                    }
                }
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error updating hospitals list", e)
            }
        }
    }

    private fun loadHospitalsForVisibleRegion() {
        if (isDataLoading) return

        val visibleBounds = naverMap.contentBounds
        val center = visibleBounds.center

        // 이전에 로드한 영역과 중복되는지 확인
        if (currentVisibleRegion?.contains(center) == true) {
            return
        }

        loadingScope.launch {
            try {
                currentVisibleRegion = visibleBounds

                // 현재 위치 업데이트
                adapter.updateUserLocation(center)

                // 기존 데이터 초기화
                viewModel.resetPagination()
                lastLoadedHospitals.clear()

                // 새 데이터 로드
                val radius = calculateRadius(visibleBounds).toInt()
                viewModel.fetchNearbyHospitals(
                    center.latitude,
                    center.longitude,
                    radius.coerceAtMost(5000) // 최대 5km로 제한
                )
            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error loading hospitals", e)
            }
        }
    }


    private fun updateMarkers(hospitals: List<HospitalInfo>) {
        if (!shouldUpdateMarkers()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val visibleBounds = withContext(Dispatchers.Main) {
                naverMap.contentBounds
            }

            // 화면에 보이는 병원만 필터링 (최대 100개로 제한)
            val visibleHospitals = hospitals
                .distinctBy { it.ykiho }
                .filter { hospital ->
                    val position = LatLng(hospital.latitude, hospital.longitude)
                    visibleBounds.contains(position) && isValidCoordinate(hospital.latitude, hospital.longitude)
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

    private fun enableLocationTracking() {
        // Fragment가 유효한 상태인지 확인
        if (!isAdded || activity == null) return

        try {
            // 위치 권한이 있는지 한번 더 확인
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                naverMap.locationTrackingMode = LocationTrackingMode.Follow
                binding.returnToLocationButton.visibility = View.VISIBLE
                locationOverlay.isVisible = true

                locationSource.activate { location ->
                    if (location != null && isAdded) {  // isAdded 체크 추가
                        val newUserLocation = LatLng(location.latitude, location.longitude)
                        userLocation = newUserLocation
                        // 어댑터에 현재 위치 전달
                        adapter.updateUserLocation(newUserLocation)

                        if (!isInitialLocationSet) {
                            isInitialLocationSet = true
                            naverMap.moveCamera(CameraUpdate.scrollTo(newUserLocation))
                            updateHospitalsBasedOnLocation(newUserLocation)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("MapViewFragment", "Location permission denied", e)
            Toast.makeText(context, "위치 서비스를 활성화해주세요.", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Log.e("MapViewFragment", "Fragment not attached to activity", e)
        }
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

    private fun addHospitalMarkers(hospitals: List<HospitalInfo>) {
        if (!shouldUpdateMarkers()) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val visibleBounds = withContext(Dispatchers.Main) {
                naverMap.contentBounds
            }

            // 화면에 보이는 병원만 필터링
            val visibleHospitals = hospitals.filter {
                val position = LatLng(it.latitude, it.longitude)
                visibleBounds.contains(position)
            }

            withContext(Dispatchers.Main) {
                // 기존 마커 재활용
                markers.forEach { recycleMarker(it) }
                markers.clear()

                // 새 마커 생성 및 추가
                visibleHospitals.forEachIndexed { index, hospital ->
                    if (isValidCoordinate(hospital.latitude, hospital.longitude)) {
                        getMarkerFromPool().apply {
                            position = LatLng(hospital.latitude, hospital.longitude)
                            captionText = hospital.name
                            tag = index
                            map = naverMap
                            setOnClickListener {
                                showHospitalDetail(visibleHospitals[it.tag as Int])
                                true
                            }
                            markers.add(this)
                        }
                    }
                }
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
            adapter.updateUserLocation(mapCenter)  // 재검색 시에도 어댑터에 위치 전달
            viewModel.resetPagination()
            viewModel.fetchNearbyHospitals(mapCenter.latitude, mapCenter.longitude)
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
        if (!isAdded) return  // Fragment가 attached 되어있는지 확인

        try {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    enableLocationTracking()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                    showLocationPermissionRationale()
                }
                else -> {
                  if (isAdded) {  // 권한 요청 전 한번 더 확인
                        requestPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                  requestLocationPermissions()
                }
            }
        } catch (e: IllegalStateException) {
            Log.e("MapViewFragment", "Failed to check location permission", e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
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

    private fun updateHospitalsBasedOnLocation(location: LatLng) {
        adapter.updateUserLocation(location)  // 위치 업데이트시 어댑터에도 전달
        viewModel.fetchNearbyHospitals(location.latitude, location.longitude)
    }


    private fun setupRecyclerView() {
        adapter = HospitalAdapter { hospital ->
            showHospitalDetail(hospital)
        }
        binding.hospitalRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MapViewFragment.adapter
            setHasFixedSize(true)
        }

        // 현재 위치가 있다면 어댑터에 전달
        userLocation?.let { location ->
            adapter.updateUserLocation(location)
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        loadingScope.cancel()
        markers.forEach { recycleMarker(it) }
        markers.clear()
        markerPool.clear()
        lastLoadedHospitals.clear()
        _binding = null
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