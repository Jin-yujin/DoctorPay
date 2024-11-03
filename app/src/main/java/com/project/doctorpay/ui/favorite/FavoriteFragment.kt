package com.project.doctorpay.ui.favorite

import android.os.Bundle
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
        loadHospitals()

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitals()
        }
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.apply {
            addChip("전체")
            addChip("영업중")
            addChip("영업마감")

            setOnCheckedChangeListener { _: ChipGroup, checkedId: Int ->
                val chip = findViewById<Chip>(checkedId)
                currentFilter = when (chip?.text?.toString()) {
                    "영업중" -> OperationState.OPEN
                    "영업마감" -> OperationState.CLOSED
                    else -> null
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.hospitals.value.let { hospitals ->
                        updateUI(filterHospitals(hospitals))
                    }
                }
            }
        }
    }

    private fun ChipGroup.addChip(text: String) {
        Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            if (text == "전체") isChecked = true
            id = View.generateViewId()  // 각 Chip에 고유 ID 부여
            addView(this)
        }
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter { hospital ->
            navigateToHospitalDetail(hospital)
        }
        binding.favoriteRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.favoriteRecyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hospitals.collectLatest { hospitals ->
                updateUI(filterHospitals(hospitals))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { error ->
                error?.let { showError(it) }
            }
        }
    }

    private fun filterHospitals(hospitals: List<HospitalInfo>): List<HospitalInfo> {
        return when (currentFilter) {
            null -> hospitals
            else -> hospitals.filter { hospital ->
                hospital.operationState == currentFilter
            }
        }.sortedWith(compareBy<HospitalInfo>(
            // 영업중인 병원을 먼저 보여줌
            { it.operationState != OperationState.OPEN },
            // 그 다음 응급실 운영
            { it.operationState != OperationState.EMERGENCY },
            // 점심시간
            { it.operationState != OperationState.LUNCH_BREAK },
            // 마지막으로 영업마감과 알 수 없음
            { it.operationState.ordinal }
        ))
    }

    private fun updateUI(hospitals: List<HospitalInfo>) {
        adapter.submitList(hospitals)
        binding.swipeRefreshLayout.isRefreshing = false

        if (hospitals.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.favoriteRecyclerView.visibility = View.GONE
            val message = if (currentFilter != null) {
                getString(R.string.no_hospitals_with_filter)
            } else {
                getString(R.string.no_favorite_hospitals)
            }
            binding.emptyView.text = message
        } else {
            binding.emptyView.visibility = View.GONE
            binding.favoriteRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showError(error: String) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    private fun loadHospitals() {
        val latitude = 37.6065
        val longitude = 127.0927
        viewModel.fetchNearbyHospitals(
            latitude = latitude,
            longitude = longitude,
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}