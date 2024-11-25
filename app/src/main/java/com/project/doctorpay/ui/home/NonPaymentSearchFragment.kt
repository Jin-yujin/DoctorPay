package com.project.doctorpay.ui.home

import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.isVisible
import com.project.doctorpay.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil.setContentView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.FragmentNonPaymentSearchBinding
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.Detail.HospitalDetailFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NonPaymentSearchFragment : Fragment() {
    private var _binding: FragmentNonPaymentSearchBinding? = null
    private val binding get() = _binding!!
    private var searchJob: Job? = null

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }

    private val adapter = NonPaymentSearchAdapter { item ->
        val hospital = HospitalInfo(
            name = item.yadmNm ?: "",
            ykiho = item.ykiho ?: "",
            address = "",
            phoneNumber = "",
            latitude = 0.0,
            longitude = 0.0,
            departments = emptyList(),
            departmentCategories = emptyList(),
            nonPaymentItems = listOf(item),
            location = LatLng(0.0, 0.0),
            clCdNm = "",
            rating = 0.0,
            timeInfo = null,
            state = ""
        )

        val detailFragment = HospitalDetailFragment.newInstance(
            hospitalId = item.ykiho ?: "",
            isFromMap = false,
            category = ""
        )
        detailFragment.setHospitalInfo(hospital)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNonPaymentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupSearchListener()
    }

    private fun setupUI() {
        binding.apply {
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

            btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            btnFilter.setOnClickListener {
                showFilterDialog()
            }
        }
    }

    private fun showFilterDialog() {
        val bottomSheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_category_filter, null)

        val dialog = BottomSheetDialog(requireContext()).apply {
            setContentView(bottomSheetView)
        }

        val sortGroup = bottomSheetView.findViewById<RadioGroup>(R.id.sortGroup)
        val btnApply = bottomSheetView.findViewById<MaterialButton>(R.id.btnApplyFilter)

        btnApply.setOnClickListener {
            val results = adapter.currentList.toMutableList()

            when (sortGroup.checkedRadioButtonId) {
                R.id.rbPriceHigh -> results.sortByDescending { it.curAmt?.toIntOrNull() ?: 0 }
                R.id.rbPriceLow -> results.sortBy { it.curAmt?.toIntOrNull() ?: 0 }
                R.id.rbName -> results.sortBy { it.npayKorNm }
            }

            adapter.submitList(results)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupSearchListener() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce
                    performSearch(s?.toString() ?: "")
                }
            }
        })
    }

    private fun performSearch(query: String) {
        if (query.length < 2) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                val results = viewModel.searchNonPaymentItems(query)
                adapter.submitList(results)
                updateEmptyState(results.isEmpty())
            } catch (e: Exception) {
                Toast.makeText(context, "검색 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            emptyStateLayout.isVisible = isEmpty
            recyclerView.isVisible = !isEmpty
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = NonPaymentSearchFragment()
    }
}