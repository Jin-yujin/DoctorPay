package com.project.doctorpay.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.databinding.FragmentHomeBinding
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.Detail.HospitalDetailFragment
import com.project.doctorpay.ui.hospitalList.HospitalListFragment
import com.project.doctorpay.ui.hospitalList.HospitalSearchFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HospitalViewModel
    private var userLocation: LatLng? = null

    companion object {
        fun newInstance(viewModel: HospitalViewModel) = HomeFragment().apply {
            this.viewModel = viewModel
        }
    }

    private lateinit var adapter: HospitalAdapter


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                loadHospitals()
            }
            else -> {
                loadDefaultLocation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!::viewModel.isInitialized) {
            viewModel = (requireActivity() as MainActivity).hospitalViewModel
        }


    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchButton()
        setupCategoryButtons()
        setupObservers()
        loadHospitals()
    }


    private fun loadHospitals() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            // 위치 권한이 있는 경우
            getCurrentLocation()
        } else {
            // 위치 권한 요청
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {

                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val newLocation = LatLng(it.latitude, it.longitude)
                        userLocation = newLocation  // userLocation 업데이트
                        adapter.updateUserLocation(newLocation)
                        viewModel.fetchNearbyHospitals(
                            viewId = HospitalViewModel.HOME_VIEW,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            radius = HospitalViewModel.DEFAULT_RADIUS
                        )
                    } ?: loadDefaultLocation()
                }
            } else {
                loadDefaultLocation()
            }
        } catch (e: SecurityException) {
            loadDefaultLocation()
        } catch (e: Exception) {
            loadDefaultLocation()
        }
    }

    private fun loadDefaultLocation() {
        val defaultLat = 37.5666805
        val defaultLng = 127.0784147
        val defaultLocation = LatLng(defaultLat, defaultLng)
        userLocation = defaultLocation
        adapter.updateUserLocation(defaultLocation)
        viewModel.fetchNearbyHospitals(
            viewId = HospitalViewModel.HOME_VIEW,
            latitude = defaultLat,
            longitude = defaultLng,
            radius = HospitalViewModel.DEFAULT_RADIUS
        )
    }

    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalId = hospital.name,
            isFromMap = false,
            category = ""
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()

        detailFragment.setHospitalInfo(hospital)
    }

    private fun setupRecyclerView() {

        adapter = HospitalAdapter(
            onItemClick = { hospital -> navigateToHospitalDetail(hospital) },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }


    private fun setupSearchButton() {
        binding.searchCard.setOnClickListener {
            // 현재 위치 정보를 HospitalSearchFragment에 전달
            val searchFragment = HospitalSearchFragment().apply {
                arguments = Bundle().apply {
                    userLocation?.let { location ->
                        putDouble("latitude", location.latitude)
                        putDouble("longitude", location.longitude)
                    }
                }
            }

            parentFragmentManager.beginTransaction()
                .add(R.id.fragment_container, searchFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupCategoryButtons() {
        val categoryButtons = mapOf(
            binding.btnInternalMedicine to DepartmentCategory.INTERNAL_MEDICINE,
            binding.btnGeneralSurgery to DepartmentCategory.SURGERY,
            binding.btnDentistry to DepartmentCategory.DENTISTRY,
            binding.btnREHABILITATION to DepartmentCategory.REHABILITATION,
            binding.btnOTOLARYNGOLOGY to DepartmentCategory.OTOLARYNGOLOGY,
            binding.btnOPHTHALMOLOGY to DepartmentCategory.OPHTHALMOLOGY,
            binding.btnMENTALNEUROLOGY to DepartmentCategory.MENTAL_NEUROLOGY,
            binding.btnOBSTETRICS to DepartmentCategory.OBSTETRICS,
            binding.btnDERMATOLOGY to DepartmentCategory.DERMATOLOGY,
            binding.btnORIENTALMEDICINE to DepartmentCategory.ORIENTAL_MEDICINE,
            binding.btnOTHERSPECIALTIES to DepartmentCategory.OTHER_SPECIALTIES,
            binding.btnGENERALMEDICINE to DepartmentCategory.GENERAL_MEDICINE
        )

        categoryButtons.forEach { (buttonLayout, category) ->
            buttonLayout.setOnClickListener {
                navigateToHospitalList(category)
            }
        }
    }


    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getHospitals(HospitalViewModel.LIST_VIEW).collectLatest { hospitals ->
                if (hospitals.isEmpty()) {
                    loadHospitals()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getHospitals(HospitalViewModel.HOME_VIEW).collectLatest { hospitals ->
                if (hospitals.isNotEmpty()) {
                    val sortedHospitals = sortHospitalsByDistance(hospitals)
                    adapter.submitList(sortedHospitals)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.HOME_VIEW).collectLatest { error ->
                error?.let { showError(it) }
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
                    Float.MAX_VALUE
                }
            }
        } else {
            hospitals
        }
    }

    private fun navigateToHospitalList(category: DepartmentCategory) {
        val hospitalListFragment = HospitalListFragment.newInstance(category.name)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, hospitalListFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showError(error: String) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
