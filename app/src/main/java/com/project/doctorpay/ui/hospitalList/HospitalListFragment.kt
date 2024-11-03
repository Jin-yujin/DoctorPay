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
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.home.HomeFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
                    // 백스택을 전부 비우고 홈화면으로 이동
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
        checkLocationPermission()
        updateHeaderText()
    }


    private fun setupRecyclerView() {
        adapter = HospitalAdapter { hospital ->
            navigateToHospitalDetail(hospital)
        }
        binding.mListView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HospitalListFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredHospitals.collectLatest { hospitals ->
                Log.d(TAG, "Received hospitals: ${hospitals.size}")
                val filteredHospitals = viewModel.filterHospitalsByCategory(hospitals, category)
                Log.d(TAG, "Filtered hospitals: ${filteredHospitals.size}")
                // 거리 기준으로 정렬된 병원 목록을 사용
                val sortedHospitals = sortHospitalsByDistance(filteredHospitals)
                updateUI(sortedHospitals)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                error?.let {
                    showError(it)
                }
            }
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
                    Log.e(TAG, "Error calculating distance for ${hospital.name}", e)
                    Float.MAX_VALUE // 오류 발생시 가장 멀리 정렬
                }
            }
        } else {
            hospitals
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
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    Log.d(TAG, "Current location: ${it.latitude}, ${it.longitude}")
                    userLocation = LatLng(it.latitude, it.longitude)
                    adapter.updateUserLocation(userLocation!!)
                    viewModel.fetchNearbyHospitals(it.latitude, it.longitude)
                } ?: loadDefaultLocationData()
            }.addOnFailureListener {
                Log.e(TAG, "Error getting location", it)
                loadDefaultLocationData()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while getting location", e)
            loadDefaultLocationData()
        }
    }


    private fun loadDefaultLocationData() {
        // 서울 시청 좌표 (기본값)
        val defaultLocation = LatLng(37.5666805, 126.9784147)
        userLocation = defaultLocation
        adapter.updateUserLocation(defaultLocation)
        viewModel.fetchNearbyHospitals(defaultLocation.latitude, defaultLocation.longitude, 5000)
    }

    private fun showLocationPermissionRationale() {
        Toast.makeText(context, "주변 병원 검색을 위해 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        loadDefaultLocationData()
    }

    private fun updateUI(hospitals: List<HospitalInfo>) {
        adapter.submitList(hospitals)
        binding.swipeRefreshLayout.isRefreshing = false

        if (hospitals.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.mListView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.mListView.visibility = View.VISIBLE
        }
        Log.d(TAG, "UI 업데이트 완료. 병원 수: ${hospitals.size}")
    }

    private fun showError(error: String) {
        binding.errorView.apply {
            visibility = View.VISIBLE
            text = error
        }
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    private fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            // 강제 새로고침 시에는 forceRefresh = true
            getCurrentLocationAndLoadData(forceRefresh = true)
        }
    }


    private fun getCurrentLocationAndLoadData(forceRefresh: Boolean = false) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    userLocation = LatLng(it.latitude, it.longitude)
                    adapter.updateUserLocation(userLocation!!)
                    viewModel.fetchNearbyHospitals(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        forceRefresh = forceRefresh
                    )
                } ?: loadDefaultLocationData(forceRefresh)
            }
        } catch (e: SecurityException) {
            loadDefaultLocationData(forceRefresh)
        }
    }

    private fun loadDefaultLocationData(forceRefresh: Boolean = false) {
        val defaultLocation = LatLng(37.5666805, 126.9784147)
        userLocation = defaultLocation
        adapter.updateUserLocation(defaultLocation)
        viewModel.fetchNearbyHospitals(
            latitude = defaultLocation.latitude,
            longitude = defaultLocation.longitude,
            radius = 5000,
            forceRefresh = forceRefresh
        )
    }

    private fun updateHeaderText() {
        binding.tvCategoryTitle.text = when (category) {
            null -> getString(R.string.hospital_list_header)
            else -> getString(R.string.category_header_format, category?.categoryName)
        }
    }
    private fun filterHospitals(onlyAvailable: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hospitals = viewModel.hospitals.value
            val filteredHospitals = viewModel.filterHospitalsByCategory(hospitals, category)
                .filter { if (onlyAvailable) it.state == "영업중" else true }
            // 필터링 후에도 거리 순으로 정렬
            val sortedHospitals = sortHospitalsByDistance(filteredHospitals)
            updateUI(sortedHospitals)
        }
    }

    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        Log.d(TAG, "Navigating to detail for hospital: ${hospital.name}")
        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalId = hospital.name,
            isFromMap = false,
            category = category?.name ?: ""
        ).apply {
            arguments = Bundle().apply {
                putParcelable("hospital_info", hospital)
            }
        }

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
}