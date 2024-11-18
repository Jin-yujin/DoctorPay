package com.project.doctorpay.ui.Detail

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.databinding.FragmentNonCoveredItemsBinding
import com.project.doctorpay.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NonCoveredItemsFragment : Fragment() {
    private var _binding: FragmentNonCoveredItemsBinding? = null
    private val binding get() = _binding!!
    private val fragmentScope = CoroutineScope(Dispatchers.Main + Job())

    private val viewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }

    private val adapter = NonCoveredItemsAdapter()

    private var hospitalId: String? = null
    private var hospitalName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            hospitalId = it.getString("hospitalId")
            hospitalName = it.getString("hospitalName")
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNonCoveredItemsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        loadNonCoveredItems()
        setupBackButton()
        observeViewState()
    }

    private fun setupUI() {
        binding.apply {
            toolbarTitle.text = "${hospitalName ?: "병원"} 비급여 항목"

            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = this@NonCoveredItemsFragment.adapter
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }

            // 정렬 옵션 추가 (선택사항)
            btnSort.setOnClickListener {
                showSortOptions()
            }
        }
    }

    private fun observeViewState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getIsLoading(HospitalViewModel.DETAIL_VIEW).collect { isLoading ->
                binding.progressBar.isVisible = isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getError(HospitalViewModel.DETAIL_VIEW).collect { error ->
                error?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun loadNonCoveredItems() {
        hospitalId?.let { ykiho ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // 비급여 정보만 가져오기
                    val items = viewModel.fetchNonPaymentItemsOnly(ykiho)

                    if (items.isEmpty()) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        adapter.submitList(items)
                    }
                } catch (e: Exception) {
                    showError("비급여 항목을 불러오는데 실패했습니다.")
                }
            }
        } ?: run {
            showError("병원 정보가 올바르지 않습니다.")
        }
    }
    private fun showSortOptions() {
        val options = arrayOf("금액 높은 순", "금액 낮은 순", "이름 순")
        AlertDialog.Builder(requireContext())
            .setTitle("정렬 방식 선택")
            .setItems(options) { _, which ->
                val currentList = adapter.currentList.toMutableList()
                when (which) {
                    0 -> currentList.sortByDescending { it.curAmt?.toIntOrNull() ?: 0 }
                    1 -> currentList.sortBy { it.curAmt?.toIntOrNull() ?: 0 }
                    2 -> currentList.sortBy { it.npayKorNm }
                }
                adapter.submitList(currentList)
            }
            .show()
    }

    private fun showEmptyState() {
        binding.apply {
            recyclerView.isVisible = false
            emptyStateLayout.isVisible = true
            emptyStateText.text = "비급여 항목이 없습니다."
        }
    }

    private fun hideEmptyState() {
        binding.apply {
            recyclerView.isVisible = true
            emptyStateLayout.isVisible = false
        }
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        fragmentScope.cancel()
        super.onDestroyView()
        _binding = null
    }
    companion object {
        fun newInstance(hospitalId: String, hospitalName: String) = NonCoveredItemsFragment().apply {
            arguments = Bundle().apply {
                putString("hospitalId", hospitalId)
                putString("hospitalName", hospitalName)
            }
        }
    }
}
