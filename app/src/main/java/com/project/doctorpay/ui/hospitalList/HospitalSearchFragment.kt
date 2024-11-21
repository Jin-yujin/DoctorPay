package com.project.doctorpay.ui.hospitalList


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.databinding.FragmentHospitalSearchBinding
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.ui.Detail.HospitalDetailFragment
import kotlinx.coroutines.launch

class HospitalSearchFragment : Fragment() {
    private var _binding: FragmentHospitalSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HospitalAdapter
    private lateinit var viewModel: HospitalViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHospitalSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = (requireActivity() as MainActivity).hospitalViewModel
        setupRecyclerView()
        setupSearchView()
        setupListeners()
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
            val hospitals = viewModel.getHospitals(HospitalViewModel.LIST_VIEW).value
            val filteredHospitals = hospitals.filter {
                it.name.contains(query, ignoreCase = true)
            }
            adapter.submitList(filteredHospitals)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}