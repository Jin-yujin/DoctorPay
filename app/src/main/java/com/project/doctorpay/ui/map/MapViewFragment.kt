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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import com.project.doctorpay.databinding.FragmentMapviewBinding
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MapViewFragment : Fragment(), OnMapReadyCallback, HospitalDetailFragment.HospitalDetailListener {

    private var _binding: FragmentMapviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var naverMap: NaverMap
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }
    private lateinit var adapter: HospitalAdapter
    private lateinit var locationSource: FusedLocationSource
    private lateinit var locationOverlay: LocationOverlay

    private var userLocation: LatLng? = null


    private var isInitialLocationSet = false
    private var isMapMoved = false
    private var isLoadingMore = false

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


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapviewBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)

        setupBottomSheet()
        setupRecyclerView()
        setupObservers()
        setupReturnToLocationButton()
        setupResearchButton()
    }



    override fun onMapReady(map: NaverMap) {
        naverMap = map
        naverMap.locationSource = locationSource
        naverMap.locationTrackingMode = LocationTrackingMode.Follow

        locationOverlay = naverMap.locationOverlay
        locationOverlay.isVisible = true

        checkLocationPermission()

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

    private fun loadHospitalsForVisibleRegion() {
        val center = naverMap.cameraPosition.target
        adapter.updateUserLocation(center)
        viewModel.resetPagination()
        viewModel.fetchNearbyHospitals(center.latitude, center.longitude, HospitalViewModel.DEFAULT_RADIUS)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredHospitals.collectLatest { hospitals ->
                Log.d("MapViewFragment", "Received ${hospitals.size} hospitals")
                userLocation?.let { currentLocation ->
                    // 거리 기준으로 정렬
                    val sortedHospitals = sortHospitalsByDistance(hospitals)
                    adapter.submitList(sortedHospitals)
                    addHospitalMarkers(sortedHospitals)
                    updateBottomSheet(sortedHospitals)
                } ?: run {
                    // 현재 위치가 없는 경우 정렬하지 않고 표시
                    adapter.submitList(hospitals)
                    addHospitalMarkers(hospitals)
                    updateBottomSheet(hospitals)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                isLoadingMore = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                error?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun calculateRadius(bounds: LatLngBounds): Double {
        val center = bounds.center
        val northeast = bounds.northEast
        return center.distanceTo(northeast)
    }



    private fun enableLocationTracking() {
        try {
            naverMap.locationTrackingMode = LocationTrackingMode.Follow
            binding.returnToLocationButton.visibility = View.VISIBLE
            locationOverlay.isVisible = true

            locationSource.activate { location ->
                val newUserLocation = LatLng(location!!.latitude, location.longitude)
                userLocation = newUserLocation

                // 어댑터에 현재 위치 전달
                adapter.updateUserLocation(newUserLocation)

                if (!isInitialLocationSet) {
                    isInitialLocationSet = true
                    naverMap.moveCamera(CameraUpdate.scrollTo(newUserLocation))
                    updateHospitalsBasedOnLocation(newUserLocation)
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "위치 서비스를 활성화해주세요.", Toast.LENGTH_SHORT).show()
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
        markers.forEach { it.map = null }
        markers.clear()

        hospitals.forEachIndexed { index, hospital ->
            if (isValidCoordinate(hospital.latitude, hospital.longitude)) {
                val marker = Marker().apply {
                    position = LatLng(hospital.latitude, hospital.longitude)
                    map = naverMap
                    captionText = hospital.name
                    tag = index
                    setOnClickListener {
                        showHospitalDetail(hospitals[it.tag as Int])
                        true
                    }
                }
                markers.add(marker)
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

        val bundle = Bundle().apply {
            putParcelable("hospital_info", hospital)
        }
        hospitalDetailFragment.arguments = bundle

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
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
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

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                latitude >= -90 && latitude <= 90 &&
                longitude >= -180 && longitude <= 180
    }


    override fun onDestroyView() {
        super.onDestroyView()
        // 프래그먼트가 파괴될 때 모든 마커 제거
        markers.forEach { it.map = null }
        markers.clear()
        _binding = null
    }

    // Lifecycle methods for MapView
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
    }

}