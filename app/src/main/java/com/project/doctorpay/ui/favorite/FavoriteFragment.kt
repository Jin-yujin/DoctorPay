package com.project.doctorpay.ui.favorite

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.OperationState
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.FragmentFavoriteBinding
import com.project.doctorpay.db.FavoriteRepository
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.Detail.HospitalDetailFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.Manifest
import androidx.activity.OnBackPressedCallback
import com.project.doctorpay.comp.BackPressHandler
import com.project.doctorpay.comp.handleBackPress
import com.project.doctorpay.db.inferDepartments
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FavoriteFragment : Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HospitalAdapter
    private var currentFilter: OperationState? = null
    private val favoriteRepository = FavoriteRepository.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: LatLng? = null

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }
    private lateinit var backPressHandler: BackPressHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        backPressHandler = BackPressHandler(requireActivity())

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (System.currentTimeMillis() > backPressHandler.backPressedTime + 2000) {
                        backPressHandler.backPressedTime = System.currentTimeMillis()
                        Toast.makeText(requireContext(), "한 번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        requireActivity().finishAffinity() // 앱 종료
                    }
                }
            }
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        setupObservers()
        checkLocationPermission() // 위치 권한 체크 및 위치 정보
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            else -> {
                // 기본 위치로 서울 설정
                userLocation = LatLng(37.5666805, 126.9784147)
                adapter.updateUserLocation(userLocation!!)
                loadFavoriteHospitals()
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { loc ->
                    userLocation = LatLng(loc.latitude, loc.longitude)
                    adapter.updateUserLocation(userLocation!!)
                    loadFavoriteHospitals()
                } ?: run {
                    // 위치를 가져올 수 없는 경우 기본 위치(서울) 사용
                    userLocation = LatLng(37.5666805, 126.9784147)
                    adapter.updateUserLocation(userLocation!!)
                    loadFavoriteHospitals()
                }
            }.addOnFailureListener {
                // 위치 가져오기 실패시 기본 위치 사용
                userLocation = LatLng(37.5666805, 126.9784147)
                adapter.updateUserLocation(userLocation!!)
                loadFavoriteHospitals()
            }
        } catch (e: SecurityException) {
            userLocation = LatLng(37.5666805, 126.9784147)
            adapter.updateUserLocation(userLocation!!)
            loadFavoriteHospitals()
        }
    }


    private fun ChipGroup.addChip(text: String) {
        Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            if (text == "전체") isChecked = true
            id = View.generateViewId()
            addView(this)
        }
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter(
            onItemClick = { hospital -> navigateToHospitalDetail(hospital) },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )
        binding.favoriteRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FavoriteFragment.adapter
        }
    }

    private fun setupObservers() {
        // 병원 데이터 옵저버 하나만 유지
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getHospitals(HospitalViewModel.FAVORITE_VIEW).collectLatest { hospitals ->
                if (hospitals.isNotEmpty()) {
                    filterAndUpdateHospitals(hospitals)
                }
            }
        }

        // 로딩 상태 옵저버
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getIsLoading(HospitalViewModel.FAVORITE_VIEW).collectLatest { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }

        // 에러 옵저버
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.FAVORITE_VIEW).collectLatest { error ->
                error?.let { showError(it) }
            }
        }
    }

    private suspend fun filterAndUpdateHospitals(hospitals: List<HospitalInfo>) {
        try {
            // 1. Firebase에서 즐겨찾기 목록 가져오기
            val favoriteYkihos = favoriteRepository.getFavoriteYkihos()
            val favoriteHospitals = hospitals.filter { hospital ->
                favoriteYkihos.contains(hospital.ykiho)
            }.distinct()

            // 2. 각 병원의 운영시간 정보 업데이트
            val updatedHospitals = coroutineScope {
                favoriteHospitals.map { hospital ->
                    async {
                        try {
                            // 운영시간 정보만 가져오기
                            val timeInfo = viewModel.fetchHospitalTimeInfo(hospital.ykiho)

                            // 새로운 운영상태로 병원 정보 업데이트
                            hospital.copy(
                                timeInfo = timeInfo,
                                state = timeInfo.getCurrentState().toDisplayText()
                            )
                        } catch (e: Exception) {
                            hospital // 에러 시 기존 정보 유지
                        }
                    }
                }.awaitAll()
            }

            // 3. 운영 상태로 필터링
            val filteredHospitals = when (currentFilter) {
                OperationState.OPEN -> updatedHospitals.filter { hospital ->
                    hospital.operationState == OperationState.OPEN ||
                            hospital.operationState == OperationState.EMERGENCY
                }
                OperationState.CLOSED -> updatedHospitals.filter { hospital ->
                    hospital.operationState == OperationState.CLOSED ||
                            hospital.operationState == OperationState.LUNCH_BREAK
                }
                else -> updatedHospitals
            }

            // 4. 운영 상태에 따라 정렬
            val sortedHospitals = filteredHospitals.sortedWith(
                compareBy<HospitalInfo> {
                    when (it.operationState) {
                        OperationState.OPEN -> 0
                        OperationState.EMERGENCY -> 1
                        OperationState.LUNCH_BREAK -> 2
                        OperationState.CLOSED -> 3
                        OperationState.UNKNOWN -> 4
                    }
                }
            )

            updateUI(sortedHospitals)
        } catch (e: Exception) {
            Log.e("FavoriteFragment", "Error filtering hospitals", e)
            showError("즐겨찾기 목록을 불러오는데 실패했습니다")
        }
    }

    private fun updateUI(hospitals: List<HospitalInfo>) {
        if (hospitals.isEmpty()) {
            binding.emptyView.apply {
                visibility = View.VISIBLE
                text = when {
                    currentFilter != null -> "해당 조건의 병원이 없습니다"
                    else -> "즐겨찾기한 병원이 없습니다.\n병원 상세페이지에서 즐겨찾기를 추가해보세요."
                }
            }
            binding.favoriteRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.favoriteRecyclerView.visibility = View.VISIBLE
            adapter.submitList(hospitals)
        }
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun loadFavoriteHospitals(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            binding.swipeRefreshLayout.isRefreshing = true
            try {
                // 1. Firebase에서 즐겨찾기 병원 기본 정보 가져오기
                var favoriteHospitals = favoriteRepository.getFavoriteHospitals()
                if (favoriteHospitals.isEmpty()) {
                    updateUI(emptyList())
                    return@launch
                }

                // 2. 각 병원의 운영시간 정보 업데이트
                favoriteHospitals = coroutineScope {
                    favoriteHospitals.map { hospital ->
                        async {
                            try {
                                // 운영시간 정보 가져오기
                                val timeInfo = viewModel.fetchHospitalTimeInfo(hospital.ykiho)

                                // 새로운 운영상태로 병원 정보 업데이트
                                hospital.copy(
                                    timeInfo = timeInfo,
                                    state = timeInfo.getCurrentState().toDisplayText()
                                )
                            } catch (e: Exception) {
                                Log.e("FavoriteFragment", "Error fetching time info for ${hospital.name}", e)
                                hospital // 에러 시 기존 정보 유지
                            }
                        }
                    }.awaitAll()
                }

                // 3. 운영상태로 필터링
                val filteredHospitals = when (currentFilter) {
                    OperationState.OPEN -> favoriteHospitals.filter { hospital ->
                        hospital.operationState == OperationState.OPEN ||
                                hospital.operationState == OperationState.EMERGENCY
                    }
                    OperationState.CLOSED -> favoriteHospitals.filter { hospital ->
                        hospital.operationState == OperationState.CLOSED ||
                                hospital.operationState == OperationState.LUNCH_BREAK
                    }
                    else -> favoriteHospitals
                }

                // 4. 운영 상태별로 정렬
                val sortedHospitals = filteredHospitals.sortedWith(
                    compareBy<HospitalInfo> {
                        when (it.operationState) {
                            OperationState.OPEN -> 0
                            OperationState.EMERGENCY -> 1
                            OperationState.LUNCH_BREAK -> 2
                            OperationState.CLOSED -> 3
                            OperationState.UNKNOWN -> 4
                        }
                    }
                )

                updateUI(sortedHospitals)

            } catch (e: Exception) {
                Log.e("FavoriteFragment", "Error loading favorites", e)
                showError("즐겨찾기 목록을 불러오는데 실패했습니다")
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.apply {
            addChip("전체")
            addChip("영업중")
            addChip("영업마감")

            setOnCheckedChangeListener { group, checkedId ->
                if (checkedId == View.NO_ID) {
                    group.check(group.getChildAt(0).id)
                    return@setOnCheckedChangeListener
                }

                val chip = group.findViewById<Chip>(checkedId)
                currentFilter = when (chip?.text?.toString()) {
                    "영업중" -> OperationState.OPEN
                    "영업마감" -> OperationState.CLOSED
                    else -> null
                }

                // 필터 변경 시 전체 목록 새로 로드
                loadFavoriteHospitals(forceRefresh = true)
            }
        }
    }
    // 별도의 캐시 관리 추가
    private var lastLoadTime = 0L
    private val CACHE_DURATION = 5 * 60 * 1000 // 5분

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (now - lastLoadTime > CACHE_DURATION) {
            loadFavoriteHospitals(forceRefresh = true)
            lastLoadTime = now
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        binding.swipeRefreshLayout.isRefreshing = false
    }

    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalId = hospital.name,
            isFromMap = false,
            category = ""
        ).apply {
            setHospitalInfo(hospital)
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
}