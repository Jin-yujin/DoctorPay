package com.project.doctorpay.ui.home

import NonPaymentItem
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.isVisible
import com.project.doctorpay.R
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: LatLng? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        getCurrentLocation()
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

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLocation = LatLng(it.latitude, it.longitude)
                } ?: run {
                    // 기본 위치 (서울 중심부)
                    currentLocation = LatLng(37.5666805, 127.0784147)
                }
            }
        } else {
            currentLocation = LatLng(37.5666805, 127.0784147)
        }
    }

    private fun navigateToHospitalDetail(item: NonPaymentItem) {
        // Fragment가 유효한지 체크
        if (!isAdded || _binding == null) return

        try {
            val hospital = HospitalInfo(
                name = item.yadmNm ?: "",
                ykiho = item.ykiho ?: "",
                address = "",
                phoneNumber = "",
                latitude = item.latitude?.toDoubleOrNull() ?: 0.0,
                longitude = item.longitude?.toDoubleOrNull() ?: 0.0,
                departments = emptyList(),
                departmentCategories = emptyList(),
                nonPaymentItems = listOf(item),
                location = LatLng(
                    item.latitude?.toDoubleOrNull() ?: 0.0,
                    item.longitude?.toDoubleOrNull() ?: 0.0
                ),
                clCdNm = item.clCdNm ?: "",
                rating = 0.0,
                timeInfo = null,
                state = ""
            )

            val detailFragment = HospitalDetailFragment.newInstance(
                hospitalId = item.ykiho ?: "",
                isFromMap = false,
                category = ""
            ).apply {
                setHospitalInfo(hospital)
            }

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e("NonPaymentSearchFragment", "Error navigating to detail", e)
            Toast.makeText(context, "상세 정보를 불러오는데 실패했습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(query: String) {
        if (!isAdded || _binding == null) return

        if (query.length < 2) {
            updateEmptyState(true)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                val results = currentLocation?.let { location ->
                    viewModel.searchNonPaymentItems(query, location.latitude, location.longitude)
                } ?: viewModel.searchNonPaymentItems(query, 37.5666805, 127.0784147)

                // Fragment가 여전히 유효한지 다시 체크
                if (!isAdded || _binding == null) return@launch

                val filteredResults = results.filter { item ->
                    (item.npayKorNm?.contains(query, ignoreCase = true) == true ||
                            item.itemNm?.contains(query, ignoreCase = true) == true) &&
                            !item.yadmNm.isNullOrBlank()
                }.sortedBy { it.yadmNm }

                adapter.submitList(filteredResults)
                updateEmptyState(filteredResults.isEmpty())
            } catch (e: Exception) {
                // Fragment가 여전히 유효한지 체크
                if (!isAdded || _binding == null) return@launch
                Toast.makeText(context, "검색 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            } finally {
                // Fragment가 여전히 유효한지 체크
                if (isAdded && _binding != null) {
                    binding.progressBar.isVisible = false
                }
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