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
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.FragmentFavoriteBinding
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoriteFragment : Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HospitalAdapter
    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(healthInsuranceApi)
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
        setupObservers()
        loadHospitals()

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitals()
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
            binding.favoriteRecyclerView.visibility = View.GONE
            binding.emptyView.text = getString(R.string.no_favorite_hospitals)
        } else {
            binding.emptyView.visibility = View.GONE
            binding.favoriteRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showError(error: String) {
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }

    private fun loadHospitals() {
        viewModel.fetchHospitalData(sidoCd = "110000", sgguCd = "110019") // 서울 중랑구로 고정
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}