package com.project.doctorpay.ui.hospitalList

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.ViewHospitalListBinding
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.OperationState
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.home.HomeFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.os.Looper

class HospitalListFragment : Fragment() {

    private var _binding: ViewHospitalListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HospitalAdapter
    private var category: DepartmentCategory? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: LatLng? = null


    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                getCurrentLocation()
            }
            else -> {
                Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                loadDefaultLocationData()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val categoryName = it.getString(ARG_CATEGORY)
            category = DepartmentCategory.values().find { it.name == categoryName }
            Log.d(TAG, "Category set to: ${category?.name}")
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ViewHospitalListBinding.inflate(inflater, container, false)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().supportFragmentManager.apply {
                        popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                        beginTransaction()
                            .replace(R.id.fragment_container, HomeFragment())
                            .commit()
                    }
                }
            }
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupListeners()

        // ViewState의 isDataLoaded 체크
        if (!viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded) {
            checkLocationPermission()
        }

        updateHeaderText()
    }

    private fun setupRecyclerView() {

        adapter = HospitalAdapter(
            onItemClick = { hospital -> navigateToHospitalDetail(hospital) },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )

        binding.mListView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HospitalListFragment.adapter
            setHasFixedSize(true)
        }

        binding.checkFilter.apply {
            text = "영업중인 병원만 보기"
            setOnCheckedChangeListener { _, isChecked ->
                filterHospitals(isChecked)
            }
        }
    }

    private fun setupObservers() {
        // 로딩 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getIsLoading(HospitalViewModel.LIST_VIEW).collectLatest { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
                // 로딩 중일 때는 빈 화면 메시지 숨기기
                if (isLoading) {
                    binding.emptyView.visibility = View.GONE
                }
            }
        }

        // 병원 데이터 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            // filteredHospitals 대신 hospitals를 먼저 관찰
            viewModel.getHospitals(HospitalViewModel.LIST_VIEW).collectLatest { hospitals ->
                Log.d(TAG, "Received base hospitals: ${hospitals.size}")
                if (hospitals.isNotEmpty()) {
                    val filteredHospitals = viewModel.filterHospitalsByCategory(hospitals, category)
                    Log.d(TAG, "Filtered by category: ${filteredHospitals.size}")

                    val showOnlyAvailable = binding.checkFilter.isChecked
                    val finalHospitals = if (showOnlyAvailable) {
                        filteredHospitals.filter { hospital ->
                            when (hospital.operationState) {
                                OperationState.OPEN, OperationState.EMERGENCY -> true
                                else -> false
                            }
                        }
                    } else {
                        filteredHospitals
                    }

                    // 정렬: 거리순으로 정렬
                    val sortedHospitals = sortHospitalsByDistance(finalHospitals)
                    Log.d(TAG, "Final sorted hospitals: ${sortedHospitals.size}")

                    updateUI(sortedHospitals)
                } else {
                    updateUI(emptyList())
                }
            }
        }

        // 에러 상태 관찰
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.LIST_VIEW).collectLatest { error ->
                error?.let {
                    showError(it)
                    // 에러 발생 시 로딩 표시 제거
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    // 거리 기준 정렬 함수 추가
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
                    Log.e(TAG, "Error calculating distance for ${hospital.name}", e)
                    Float.MAX_VALUE
                }
            }
        } else {
            hospitals
        }
    }

    // UI 업데이트 함수 수정
    private fun updateUI(hospitals: List<HospitalInfo>) {
        Log.d(TAG, "Updating UI with ${hospitals.size} hospitals")

        if (hospitals.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.mListView.visibility = View.GONE
            binding.emptyView.text = if (binding.checkFilter.isChecked) {
                "현재 영업중인 병원이 없습니다"
            } else {
                "주변에 병원이 없습니다"
            }
        } else {
            binding.emptyView.visibility = View.GONE
            binding.mListView.visibility = View.VISIBLE
            adapter.submitList(hospitals) {
                // 리스트 갱신 완료 후 실행될 콜백
                binding.mListView.scrollToPosition(0)
            }
        }

        // 로딩 상태가 아닐 때만 새로고침 인디케이터 숨기기
        if (!viewModel.getIsLoading(HospitalViewModel.LIST_VIEW).value) {
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                showLocationPermissionRationale()
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            // ViewModel의 isDataLoaded 체크
            if (viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded && userLocation != null) {
                Log.d(TAG, "Using cached location data")
                return
            }

            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000
            }

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Location update received: ${location.latitude}, ${location.longitude}")
                        userLocation = LatLng(location.latitude, location.longitude)
                        adapter.updateUserLocation(userLocation!!)

                        if (!viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded) {
                            viewModel.fetchNearbyHospitals(
                                viewId = HospitalViewModel.LIST_VIEW,
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
                    userLocation = LatLng(location.latitude, location.longitude)
                    adapter.updateUserLocation(userLocation!!)

                    if (!viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded) {
                        viewModel.fetchNearbyHospitals(
                            viewId = HospitalViewModel.LIST_VIEW,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    }
                } else {
                    Log.d(TAG, "Requesting location updates")
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error getting location", e)
                loadDefaultLocationData()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while getting location", e)
            loadDefaultLocationData()
        }
    }

    private fun loadDefaultLocationData() {
        if (viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded) {
            Log.d(TAG, "Using cached default location data")
            return
        }

        val defaultLocation = LatLng(37.5666805, 127.0784147)
        userLocation = defaultLocation
        adapter.updateUserLocation(defaultLocation)

        if (!viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded) {
            viewModel.fetchNearbyHospitals(
                viewId = HospitalViewModel.LIST_VIEW,
                latitude = defaultLocation.latitude,
                longitude = defaultLocation.longitude,
                radius = 5000
            )
        }
    }

    private fun getCurrentLocationAndLoadData(forceRefresh: Boolean = false) {
        if (!forceRefresh && viewModel.getViewState(HospitalViewModel.LIST_VIEW).isDataLoaded) {
            Log.d(TAG, "Skipping data reload - already loaded")
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    userLocation = LatLng(it.latitude, it.longitude)
                    adapter.updateUserLocation(userLocation!!)
                    viewModel.fetchNearbyHospitals(
                        viewId = HospitalViewModel.LIST_VIEW,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        forceRefresh = forceRefresh
                    )
                } ?: loadDefaultLocationData()
            }
        } catch (e: SecurityException) {
            loadDefaultLocationData()
        }
    }

    // SwipeRefresh 리스너에서는 forceRefresh를 true로 설정
    private fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.clearCache(HospitalViewModel.LIST_VIEW)  // 캐시 초기화
            getCurrentLocationAndLoadData(forceRefresh = true)
        }
    }

    private fun showLocationPermissionRationale() {
        Toast.makeText(context, "주변 병원 검색을 위해 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        loadDefaultLocationData()
    }


    private fun getDistanceFromUser(hospital: HospitalInfo): Float {
        val currentLocation = userLocation
        return if (currentLocation != null) {
            val results = FloatArray(1)
            try {
                Location.distanceBetween(
                    currentLocation.latitude, currentLocation.longitude,
                    hospital.latitude, hospital.longitude,
                    results
                )
                results[0]
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating distance for ${hospital.name}", e)
                Float.MAX_VALUE
            }
        } else {
            Float.MAX_VALUE
        }
    }


    private fun showError(error: String) {
        binding.errorView.apply {
            visibility = View.VISIBLE
            text = error
        }
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    private fun updateHeaderText() {
        binding.tvCategoryTitle.text = when (category) {
            null -> getString(R.string.hospital_list_header)
            else -> getString(R.string.category_header_format, category?.categoryName)
        }
    }

    private fun filterHospitals(onlyAvailable: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hospitals = viewModel.getHospitals(HospitalViewModel.LIST_VIEW).value
            // 1. 카테고리 필터링
            val categoryFiltered = viewModel.filterHospitalsByCategory(hospitals, category)

            // 2. 운영 상태 필터링
            val stateFiltered = categoryFiltered.filter { hospital ->
                if (onlyAvailable) {
                    when (hospital.operationState) {
                        OperationState.OPEN, OperationState.EMERGENCY -> true
                        else -> false
                    }
                } else {
                    true
                }
            }

            // 3. 거리순 정렬
            val sortedHospitals = stateFiltered.sortedWith(
                compareBy<HospitalInfo> { hospital ->
                    val currentLocation = userLocation
                    if (currentLocation != null) {
                        val results = FloatArray(1)
                        try {
                            Location.distanceBetween(
                                currentLocation.latitude, currentLocation.longitude,
                                hospital.latitude, hospital.longitude,
                                results
                            )
                            results[0]
                        } catch (e: Exception) {
                            Log.e(TAG, "Error calculating distance for ${hospital.name}", e)
                            Float.MAX_VALUE
                        }
                    } else {
                        Float.MAX_VALUE
                    }
                }
            )

            updateUI(sortedHospitals)
        }
    }



    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        Log.d(TAG, "Navigating to detail for hospital: ${hospital.name}")
        val detailFragment = HospitalDetailFragment().apply {
            setHospitalInfo(hospital) // 먼저 hospital 객체를 설정
            arguments = Bundle().apply {
                putString(HospitalDetailFragment.ARG_HOSPITAL_ID, hospital.ykiho)
                putBoolean(HospitalDetailFragment.ARG_IS_FROM_MAP, false)
                putString(HospitalDetailFragment.ARG_CATEGORY, category?.name ?: "")
            }
        }

        parentFragmentManager.beginTransaction()
            .hide(this) // 현재 HospitalListFragment를 숨김
            .add(R.id.fragment_container, detailFragment) // DetailFragment를 추가
            .addToBackStack(null)
            .commit()
    }

    companion object {
        private const val TAG = "HospitalListFragment"
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: String) = HospitalListFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CATEGORY, category)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}