package com.project.doctorpay.ui.map

import NonPaymentItem
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
import com.project.doctorpay.db.FavoriteRepository
import kotlinx.coroutines.flow.collectLatest
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.widget.AppCompatButton
import androidx.core.location.LocationManagerCompat.getCurrentLocation
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.project.doctorpay.MainActivity
import com.project.doctorpay.db.HospitalTimeInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
        checkGooglePlayServices() // Google Play Services 체크 먼저 수행
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }
    private var locationCallback: LocationCallback? = null

    private var cachedHospitals: MutableMap<String, List<HospitalInfo>> = mutableMapOf()
    private var cacheExpirationTime: Long = 5 * 60 * 1000 // 5분

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
    private var lastCacheTime: MutableMap<String, Long> = mutableMapOf()

    private var currentVisibleRegion: LatLngBounds? = null
    private var isDataLoading = false

    private val loadingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLoadingJob: Job? = null
    private val visibleHospitalCache = mutableMapOf<String, List<HospitalInfo>>()
    private val CACHE_DURATION = 5 * 60 * 1000 // 5분


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

    private fun checkGooglePlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(requireContext())

        return when (resultCode) {
            ConnectionResult.SUCCESS -> true
            else -> {
                Log.w("MapViewFragment", "Google Play Services not available")
                googleApiAvailability.getErrorDialog(requireActivity(), resultCode, 0)?.show()
                loadDefaultLocation()
                false
            }
        }
    }


    override fun onMapReady(map: NaverMap) {
        if (!isAdded) return

        try {
            naverMap = map
            naverMap.locationSource = locationSource

            setupMapUI()

            // 위치 서비스 초기화 및 권한 체크
            if (checkGooglePlayServices()) {
                checkLocationPermission()
            }

            locationOverlay = naverMap.locationOverlay.apply {
                isVisible = true
            }

            setupMapListeners()
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error in onMapReady", e)
            loadDefaultLocation()
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

    private fun loadHospitalsForVisibleRegion() {
        if (isDataLoading) return

        currentLoadingJob?.cancel()
        currentLoadingJob = loadingScope.launch {
            try {
                isDataLoading = true

                val visibleBounds = naverMap.contentBounds
                val center = visibleBounds.center
                val cacheKey = "${center.latitude.round(4)},${center.longitude.round(4)}"

                // 캐시 확인
                val cachedData = visibleHospitalCache[cacheKey]?.takeIf {
                    System.currentTimeMillis() - (lastCacheTime[cacheKey] ?: 0) < CACHE_DURATION
                }

                if (cachedData != null) {
                    updateHospitalsList(cachedData)
                    updateMarkers(cachedData)
                    return@launch
                }

                // 병원 기본 정보와 부가 정보를 병렬로 로드
                val hospitals = supervisorScope {
                    val baseDataDeferred = async {
                        viewModel.fetchNearbyHospitals(
                            viewId = HospitalViewModel.MAP_VIEW,
                            latitude = center.latitude,
                            longitude = center.longitude,
                            radius = calculateRadius(visibleBounds).toInt().coerceAtMost(5000)
                        )
                    }

                    val hospitals = baseDataDeferred.await()

                    // 추가 정보를 병렬로 로드 (20개씩 청크로 나눠서 처리)
                    hospitals.chunked(20).flatMap { chunk ->
                        chunk.map { hospital ->
                            async {
                                try {
                                    val timeInfo = withTimeout(3000) {
                                        viewModel.fetchHospitalTimeInfo(hospital.ykiho)
                                    }
                                    hospital.copy(timeInfo = timeInfo)
                                } catch (e: Exception) {
                                    Log.w("MapViewFragment", "Failed to load additional info for ${hospital.name}", e)
                                    hospital
                                }
                            }
                        }.awaitAll()
                    }
                }

                // 캐시 업데이트
                visibleHospitalCache[cacheKey] = hospitals
                lastCacheTime[cacheKey] = System.currentTimeMillis()

                // 마커 업데이트 최적화
                val visibleHospitals = hospitals.filter {
                    visibleBounds.contains(LatLng(it.latitude, it.longitude))
                }

                withContext(Dispatchers.Main) {
                    updateHospitalsList(hospitals)
                    updateMarkersOptimized(visibleHospitals)
                }

            } catch (e: Exception) {
                Log.e("MapViewFragment", "Error loading hospitals", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "데이터 로드 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isDataLoading = false
            }
        }
    }

    private fun updateMarkersOptimized(hospitals: List<HospitalInfo>) {
        val currentMarkers = markers.toList()
        val newMarkerPositions = hospitals.map { LatLng(it.latitude, it.longitude) }

        // 제거할 마커 찾기
        val markersToRemove = currentMarkers.filter { marker ->
            !newMarkerPositions.contains(marker.position)
        }

        // 새로 추가할 마커 찾기
        val positionsToAdd = newMarkerPositions.filter { position ->
            !currentMarkers.any { it.position == position }
        }

        // 마커 제거
        markersToRemove.forEach { marker ->
            marker.map = null
            recycleMarker(marker)
            markers.remove(marker)
        }

        // 새 마커 추가
        positionsToAdd.forEach { position ->
            val hospital = hospitals.find {
                LatLng(it.latitude, it.longitude) == position
            } ?: return@forEach

            getMarkerFromPool().apply {
                this.position = position
                this.captionText = hospital.name
                this.tag = hospital
                this.map = naverMap
                setOnClickListener {
                    showHospitalDetail(it.tag as HospitalInfo)
                    true
                }
                markers.add(this)
            }
        }
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }

    private suspend fun loadHospitals(): List<HospitalInfo> {
        val location = userLocation ?: return emptyList()
        return viewModel.fetchNearbyHospitals(
            viewId = HospitalViewModel.MAP_VIEW,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    private suspend fun loadNonPaymentInfo(): List<NonPaymentItem> {
        return viewModel.getNonPaymentInfo()
    }

    private suspend fun loadTimeInfo(): List<HospitalTimeInfo> {
        // 병원별 시간 정보 로드
        val hospitals = viewModel.getHospitals(HospitalViewModel.MAP_VIEW).value
        return hospitals.map { hospital ->
            viewModel.fetchHospitalTimeInfo(hospital.ykiho)
        }
    }

    private fun processResults(results: List<Any>) {
        val (hospitals, nonPaymentItems, timeInfoList) = results

        // 결과 처리 및 UI 업데이트
        viewLifecycleOwner.lifecycleScope.launch {
            if (hospitals is List<*> && hospitals.isNotEmpty()) {
                val typedHospitals = hospitals as List<HospitalInfo>
                updateHospitalsList(typedHospitals)
                updateMarkers(typedHospitals)
            }
        }
    }

    private fun handleClusterClick(cluster: HospitalCluster) {
        if (cluster.size == 1) {
            showHospitalDetail(cluster.hospitals.first())
        } else {
            // 여러 병원이 있는 경우 목록 표시
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            updateBottomSheet(cluster.hospitals)

            // 해당 위치로 카메라 이동
            naverMap.moveCamera(
                CameraUpdate.scrollAndZoomTo(
                    cluster.position,
                    naverMap.cameraPosition.zoom + 1f
                )
            )
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

            // 클러스터링 적용
            val clusteredHospitals = clusterHospitals(
                hospitals.filter { hospital ->
                    val position = LatLng(hospital.latitude, hospital.longitude)
                    visibleBounds.contains(position) &&
                            isValidCoordinate(hospital.latitude, hospital.longitude)
                }
            )

            withContext(Dispatchers.Main) {
                markers.forEach { recycleMarker(it) }
                markers.clear()

                clusteredHospitals.forEach { cluster ->
                    getMarkerFromPool().apply {
                        position = cluster.position
                        captionText = if (cluster.size > 1) {
                            "${cluster.size}개의 병원"
                        } else {
                            cluster.hospitals.first().name
                        }
                        tag = cluster
                        map = naverMap
                        setOnClickListener {
                            handleClusterClick(it.tag as HospitalCluster)
                            true
                        }
                        markers.add(this)
                    }
                }
            }
        }
    }

    private fun clusterHospitals(hospitals: List<HospitalInfo>): List<HospitalCluster> {
        val clusters = mutableListOf<HospitalCluster>()
        val clusterDistance = 100.0 // 클러스터링 거리 (미터)

        hospitals.forEach { hospital ->
            val hospitalPosition = LatLng(hospital.latitude, hospital.longitude)
            val existingCluster = clusters.find { cluster ->
                cluster.position.distanceTo(hospitalPosition) <= clusterDistance
            }

            if (existingCluster != null) {
                existingCluster.hospitals.add(hospital)
            } else {
                clusters.add(HospitalCluster(hospitalPosition, mutableListOf(hospital)))
            }
        }

        return clusters
    }

    data class HospitalCluster(
        val position: LatLng,
        val hospitals: MutableList<HospitalInfo>
    ) {
        val size: Int get() = hospitals.size
    }
    private fun getCachedHospitals(key: String): List<HospitalInfo>? {
        return cachedHospitals[key]?.takeIf {
            System.currentTimeMillis() - (lastCacheTime[key] ?: 0) < cacheExpirationTime
        }
    }

    private fun cacheHospitals(key: String, hospitals: List<HospitalInfo>) {
        cachedHospitals[key] = hospitals
        lastCacheTime[key] = System.currentTimeMillis()
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
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(15000)
                .build()

            // 위치 콜백 초기화
            locationCallback = object : LocationCallback() {
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
            locationCallback?.let { callback ->
                if (hasLocationPermission()) {
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        callback,
                        Looper.getMainLooper()
                    ).addOnFailureListener { e ->
                        Log.e("MapViewFragment", "Location updates failed", e)
                        loadDefaultLocation()
                    }

                    // 초기 위치 한 번 가져오기
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location ->
                            location?.let {
                                val newUserLocation = LatLng(it.latitude, it.longitude)
                                userLocation = newUserLocation
                                adapter.updateUserLocation(newUserLocation)
                                if (!isInitialLocationSet) {
                                    isInitialLocationSet = true
                                    naverMap.moveCamera(CameraUpdate.scrollTo(newUserLocation))
                                    updateHospitalsBasedOnLocation(newUserLocation)
                                }
                            } ?: loadDefaultLocation()
                        }
                        .addOnFailureListener {
                            Log.e("MapViewFragment", "Failed to get last location", it)
                            loadDefaultLocation()
                        }
                } else {
                    loadDefaultLocation()
                }
            }

        } catch (e: SecurityException) {
            Log.e("MapViewFragment", "Location permission denied", e)
            loadDefaultLocation()
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error enabling location tracking", e)
            loadDefaultLocation()
        }
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
    private fun clearMarkers() {
        markers.forEach { recycleMarker(it) }
        markers.clear()
        markerPool.clear()
    }

    private fun clearCache() {
        cachedHospitals.clear()
        lastCacheTime.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 위치 업데이트 중지
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
        } catch (e: Exception) {
            Log.e("MapViewFragment", "Error removing location updates", e)
        }
        clearMarkers()
        clearCache()
        binding.hospitalRecyclerView.adapter = null
        _binding = null
        locationCallback = null
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
        // locationCallback 제거
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