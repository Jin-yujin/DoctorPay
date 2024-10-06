package com.project.doctorpay.ui.hospitalList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.api.healthInsuranceApi
import com.project.doctorpay.databinding.ViewHospitalListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HospitalListFragment : Fragment() {
    private var _binding: ViewHospitalListBinding? = null
    private val binding get() = _binding!!

    private var category: String = ""
    private lateinit var adapter: HospitalAdapter
    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(healthInsuranceApi)
    }

    companion object {
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: String): HospitalListFragment {
            return HospitalListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getString(ARG_CATEGORY, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ViewHospitalListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupObservers()
        loadHospitalList()

        binding.checkFilter.setOnCheckedChangeListener { _, isChecked ->
            loadHospitalList(isChecked)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHospitalList(binding.checkFilter.isChecked)
        }
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter { hospital ->
            navigateToHospitalDetail(hospital)
        }
        binding.mListView.layoutManager = LinearLayoutManager(requireContext())
        binding.mListView.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hospitals.collectLatest { hospitals ->
                val filteredHospitals = hospitals.filter { hospital ->
                    hospital.department.split(", ").any { it.contains(category, ignoreCase = true) }
                }
                adapter.submitList(filteredHospitals)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadHospitalList(onlyAvailable: Boolean = false) {
        viewModel.fetchHospitalData()
        if (onlyAvailable) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.hospitals.collectLatest { hospitals ->
                    val filteredHospitals = hospitals.filter { hospital ->
                        hospital.department.split(", ").any { it.contains(category, ignoreCase = true) } &&
                                hospital.state == "영업중"
                    }
                    adapter.submitList(filteredHospitals)
                }
            }
        }
    }

    private fun navigateToHospitalDetail(hospital: HospitalInfo) {
        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalId = hospital.name,  // 고유 ID가 없으므로 이름을 사용
            isFromMap = false,
            category = category
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