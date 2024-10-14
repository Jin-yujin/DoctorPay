package com.project.doctorpay.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import com.project.doctorpay.databinding.FragmentHomeBinding
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalListFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(healthInsuranceApi)
    }
    private lateinit var adapter: HospitalAdapter

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
        observeViewModel()

        viewModel.fetchHospitalData(sidoCd = "110000", sgguCd = "110019") // 서울 중랑구로 고정
    }

    private fun setupRecyclerView() {
        adapter = HospitalAdapter { hospital ->
            // Handle item click here
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            val searchQuery = binding.searchEditText.text.toString()
            viewModel.searchHospitals(searchQuery)
        }
    }

    private fun setupCategoryButtons() {
        val categoryButtons = mapOf(
            binding.btnInternalMedicine to DepartmentCategory.INTERNAL_MEDICINE,
            binding.btnGeneralSurgery to DepartmentCategory.SURGERY,
            binding.btnDentistry to DepartmentCategory.DENTISTRY,
            binding.btnSensory to DepartmentCategory.SENSORY_ORGANS,
            binding.btnREHABILITATION to DepartmentCategory.REHABILITATION,
            binding.btnPEDIATRICSOBSTETRICS to DepartmentCategory.PEDIATRICS_OBSTETRICS,
            binding.btnENT to DepartmentCategory.MENTAL_NEUROLOGY,
            binding.btnGENERALMEDICINE to DepartmentCategory.GENERAL_MEDICINE,
            binding.btnORIENTALMEDICINE to DepartmentCategory.ORIENTAL_MEDICINE,
            binding.btnDIAGNOSTICS to DepartmentCategory.DIAGNOSTICS,
            binding.btnDERMATOLOGYUROLOGY to DepartmentCategory.DERMATOLOGY_UROLOGY,
            binding.btnOTHERSPECIALTIES to DepartmentCategory.OTHER_SPECIALTIES
        )

        categoryButtons.forEach { (buttonLayout, category) ->
            buttonLayout.setOnClickListener {
                navigateToHospitalList(category)
            }
        }
    }


    private fun navigateToHospitalList(category: DepartmentCategory) {
        val hospitalListFragment = HospitalListFragment.newInstance(category.name)
        parentFragmentManager.beginTransaction()
            .replace(R.id.lyFrameLayout_home, hospitalListFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hospitals.collectLatest { hospitals ->
                adapter.submitList(hospitals)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}