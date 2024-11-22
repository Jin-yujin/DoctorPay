package com.project.doctorpay.ui.hospitalList


import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.Manifest
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.databinding.FragmentHospitalSearchBinding
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.ui.Detail.HospitalDetailFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HospitalSearchFragment : Fragment() {
    private var _binding: FragmentHospitalSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HospitalAdapter
    private lateinit var viewModel: HospitalViewModel
    private var userLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = (requireActivity() as MainActivity).hospitalViewModel

        // 전달받은 위치 정보로 userLocation 초기화
        arguments?.let { args ->
            if (args.containsKey("latitude") && args.containsKey("longitude")) {
                val lat = args.getDouble("latitude")
                val lng = args.getDouble("longitude")
                userLocation = LatLng(lat, lng)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = (requireActivity() as MainActivity).hospitalViewModel
        setupRecyclerView()
        setupSearchView()
        setupListeners()
        setupObservers()

        updateLocationIfNeeded()

        // adapter에 위치 정보 전달
        userLocation?.let { location ->
            adapter.updateUserLocation(location)
        }

        // 포커스 요청 및 키보드 표시
        binding.etSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    // 위치 정보 업데이트
    private fun updateLocationIfNeeded() {
        if (userLocation == null) {
            loadLocation()
        } else {
            adapter.updateUserLocation(userLocation!!)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getHospitals(HospitalViewModel.LIST_VIEW).collectLatest { hospitals ->
                if (hospitals.isNotEmpty()) {
                    val query = binding.etSearch.text.toString()
                    if (query.isEmpty()) {
                        val sortedHospitals = sortHospitalsByDistance(hospitals)  // 거리순 정렬 추가
                        adapter.submitList(sortedHospitals)
                        binding.emptyView.visibility = View.GONE
                    } else {
                        val filteredHospitals = hospitals.filter {
                            it.name.contains(query, ignoreCase = true)
                        }
                        val sortedFilteredHospitals = sortHospitalsByDistance(filteredHospitals)  // 거리순 정렬 추가
                        adapter.submitList(sortedFilteredHospitals)
                        binding.emptyView.visibility = if (filteredHospitals.isEmpty()) View.VISIBLE else View.GONE
                        binding.emptyView.text = "검색 결과가 없습니다"
                    }
                    binding.loadingView.visibility = View.GONE
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
                    Float.MAX_VALUE
                }
            }
        } else {
            hospitals
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHospitalSearchBinding.inflate(inflater, container, false)
        return binding.root
    }


    private fun setupRecyclerView() {
        adapter = HospitalAdapter(
            onItemClick = { hospital ->
                navigateToHospitalDetail(hospital)
            },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HospitalSearchFragment.adapter
        }
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

    private fun setupSearchView() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClear.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                searchHospitals(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }


    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            // 키보드 숨기기
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

            // 뒤로가기
            parentFragmentManager.popBackStack()
        }

        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchHospitals(binding.etSearch.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun searchHospitals(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.loadingView.visibility = View.VISIBLE

            val hospitals = viewModel.getHospitals(HospitalViewModel.LIST_VIEW).value
            if (hospitals.isEmpty()) {
                binding.emptyView.text = "데이터를 불러오는 중입니다..."
                binding.emptyView.visibility = View.VISIBLE
                loadLocation()
            } else {
                if (query.isEmpty()) {
                    // 검색어가 없을 때도 거리순 정렬 적용
                    val sortedHospitals = sortHospitalsByDistance(hospitals)
                    adapter.submitList(sortedHospitals)
                    binding.emptyView.visibility = View.GONE
                } else {
                    val filteredHospitals = hospitals.filter {
                        it.name.contains(query, ignoreCase = true)
                    }
                    // 필터링된 결과를 거리순으로 정렬
                    val sortedFilteredHospitals = sortHospitalsByDistance(filteredHospitals)
                    adapter.submitList(sortedFilteredHospitals)
                    binding.emptyView.visibility = if (filteredHospitals.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyView.text = "검색 결과가 없습니다"
                }
            }
            binding.loadingView.visibility = View.GONE
        }
    }

    private fun loadLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val newLocation = LatLng(it.latitude, it.longitude)
                        userLocation = newLocation  // userLocation 업데이트
                        adapter.updateUserLocation(newLocation)
                        viewModel.fetchNearbyHospitals(
                            viewId = HospitalViewModel.LIST_VIEW,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            radius = HospitalViewModel.DEFAULT_RADIUS
                        )
                    } ?: loadDefaultLocation()
                }
            } else {
                loadDefaultLocation()
            }
        } catch (e: Exception) {
            loadDefaultLocation()
        }
    }

    private fun loadDefaultLocation() {
        val defaultLat = 37.5666805  // 서울 중심부
        val defaultLng = 127.0784147
        val defaultLocation = LatLng(defaultLat, defaultLng)
        userLocation = defaultLocation  // userLocation 업데이트
        adapter.updateUserLocation(defaultLocation)
        viewModel.fetchNearbyHospitals(
            viewId = HospitalViewModel.LIST_VIEW,
            latitude = defaultLat,
            longitude = defaultLng,
            radius = HospitalViewModel.DEFAULT_RADIUS
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}