package com.project.doctorpay.ui.hospitalList

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
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import com.project.doctorpay.databinding.ViewHospitalListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HospitalListFragment : Fragment() {
    private var _binding: ViewHospitalListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HospitalAdapter

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(healthInsuranceApi)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ViewHospitalListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        loadHospitalList()
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter { hospital ->
            navigateToHospitalDetail(hospital)
        }
        binding.mListView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HospitalListFragment.adapter
        }
    }
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hospitals.collectLatest { hospitals ->
                Log.d("HospitalListFragment", "받은 병원 목록 크기: ${hospitals.size}")
                adapter.submitList(hospitals)
                updateUI(hospitals)
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


    private fun updateUI(hospitals: List<HospitalInfo>) {
        adapter.submitList(hospitals)
        binding.swipeRefreshLayout.isRefreshing = false

        if (hospitals.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.mListView.visibility = View.GONE
            binding.emptyView.text = getString(R.string.no_hospitals_found)
        } else {
            binding.emptyView.visibility = View.GONE
            binding.mListView.visibility = View.VISIBLE
        }
        Log.d("HospitalListFragment", "UI 업데이트 완료. 병원 수: ${hospitals.size}")

    }

    private fun showError(error: String) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        binding.errorView.apply {
            visibility = View.VISIBLE
            text = error
        }
    }


    private fun setupListeners() {
        binding.checkFilter.setOnCheckedChangeListener { _, isChecked ->
            filterHospitals(isChecked)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitalList()
        }
    }

    private fun loadHospitalList() {
        viewModel.fetchHospitalData(sidoCd = "110000", sgguCd = "110019") // 서울 중랑구로 고정
    }

    private fun filterHospitals(onlyAvailable: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hospitals = viewModel.hospitals.value
            val filteredHospitals = if (onlyAvailable) {
                hospitals.filter { it.state == "영업중" }
            } else {
                hospitals
            }
            updateUI(filteredHospitals)
        }
    }

    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        Log.d("HospitalListFragment", "Navigating to detail for hospital: ${hospital.name}")
        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalId = hospital.name,
            isFromMap = false,
            category = ""
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
        Log.d("HospitalListFragment", "Fragment transaction committed")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HospitalListFragment()
    }
}