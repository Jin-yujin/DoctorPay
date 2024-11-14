package com.project.doctorpay.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import com.project.doctorpay.databinding.FragmentHomeBinding
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.hospitalList.HospitalAdapter
import com.project.doctorpay.ui.hospitalList.HospitalDetailFragment
import com.project.doctorpay.ui.hospitalList.HospitalListFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HospitalViewModel

    companion object {
        fun newInstance(viewModel: HospitalViewModel) = HomeFragment().apply {
            this.viewModel = viewModel
        }
    }

    private lateinit var adapter: HospitalAdapter


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
        // 서울 중랑구의 기본 좌표값 사용
        val latitude = 37.6065
        val longitude = 127.0927
        viewModel.fetchNearbyHospitals(
            viewId = HospitalViewModel.HOME_VIEW,
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

    private fun setupRecyclerView() {

        adapter = HospitalAdapter(
            onItemClick = { hospital -> navigateToHospitalDetail(hospital) },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupSearchButton() {
        binding.searchButton.setOnClickListener {
            val searchQuery = binding.searchEditText.text.toString().trim()
            if (searchQuery.isEmpty()) {
                viewModel.resetSearch(HospitalViewModel.HOME_VIEW)
            } else {
                viewModel.searchHospitals(HospitalViewModel.HOME_VIEW, searchQuery)
            }
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    viewModel.resetSearch(HospitalViewModel.HOME_VIEW)
                } else if (query.length >= 2) {
                    viewModel.searchHospitals(HospitalViewModel.HOME_VIEW, query)
                }
            }
        })
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
            viewModel.getHospitals(HospitalViewModel.HOME_VIEW).collectLatest { hospitals ->
                adapter.submitList(hospitals)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.HOME_VIEW).collectLatest { error ->
                error?.let { showError(it) }
            }
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
