package com.project.doctorpay.ui.favorite

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.OperationState  // OperationState를 db 패키지에서 import
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.FragmentFavoriteBinding
import com.project.doctorpay.db.FavoriteRepository
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoriteFragment : Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HospitalAdapter
    private var currentFilter: OperationState? = null
    private val favoriteRepository = FavoriteRepository()

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        setupObservers()
        loadFavoriteHospitals()
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.apply {
            addChip("전체")
            addChip("영업중")
            addChip("영업마감")

            setOnCheckedChangeListener { _, checkedId ->
                val chip = findViewById<Chip>(checkedId)
                currentFilter = when (chip?.text?.toString()) {
                    "영업중" -> OperationState.OPEN
                    "영업마감" -> OperationState.CLOSED
                    else -> null
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    filterAndUpdateHospitals()
                }
            }
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getHospitals(HospitalViewModel.FAVORITE_VIEW).collectLatest { hospitals ->
                filterAndUpdateHospitals(hospitals)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getIsLoading(HospitalViewModel.FAVORITE_VIEW).collectLatest { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.FAVORITE_VIEW).collectLatest { error ->
                error?.let { showError(it) }
            }
        }
    }

    private suspend fun filterAndUpdateHospitals(hospitals: List<HospitalInfo> = emptyList()) {
        try {
            val favoriteYkihos = favoriteRepository.getFavoriteYkihos()
            val favoriteHospitals = hospitals.filter { hospital ->
                favoriteYkihos.contains(hospital.ykiho)
            }

            val filteredHospitals = when (currentFilter) {
                null -> favoriteHospitals
                else -> favoriteHospitals.filter { it.operationState == currentFilter }
            }

            val sortedHospitals = filteredHospitals.sortedWith(
                compareBy<HospitalInfo> { it.operationState != OperationState.OPEN }
                    .thenBy { it.operationState != OperationState.EMERGENCY }
                    .thenBy { it.operationState != OperationState.LUNCH_BREAK }
                    .thenBy { it.operationState.ordinal }
            )

            updateUI(sortedHospitals)
        } catch (e: Exception) {
            Log.e("FavoriteFragment", "Error filtering hospitals", e)
            showError("즐겨찾기 목록을 불러오는데 실패했습니다")
        }
    }

    private fun filterHospitals(hospitals: List<HospitalInfo>): List<HospitalInfo> {
        return when (currentFilter) {
            null -> hospitals
            else -> hospitals.filter { hospital ->
                hospital.operationState == currentFilter
            }
        }.sortedWith(compareBy<HospitalInfo>(
            { it.operationState != OperationState.OPEN },
            { it.operationState != OperationState.EMERGENCY },
            { it.operationState != OperationState.LUNCH_BREAK },
            { it.operationState.ordinal }
        ))
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
            try {
                val favoriteYkihos = favoriteRepository.getFavoriteYkihos()
                if (favoriteYkihos.isEmpty()) {
                    updateUI(emptyList())
                    return@launch
                }

                viewModel.fetchNearbyHospitals(
                    viewId = HospitalViewModel.FAVORITE_VIEW,
                    latitude = 37.5666805,  // Default location (Seoul)
                    longitude = 126.9784147,
                    radius = 5000,
                    forceRefresh = forceRefresh
                )
            } catch (e: Exception) {
                showError("즐겨찾기 목록을 불러오는데 실패했습니다")
            }
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