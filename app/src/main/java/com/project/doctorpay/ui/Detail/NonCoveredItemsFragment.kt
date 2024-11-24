package com.project.doctorpay.ui.Detail

import NonPaymentItem
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var currentPage = 1
    private var isLoading = false
    private var hasMoreItems = true
    private val itemsList = mutableListOf<NonPaymentItem>()


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

                // 스크롤 리스너 추가
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)

                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                        if (!isLoading && hasMoreItems) {
                            if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                                && firstVisibleItemPosition >= 0
                            ) {
                                loadMoreItems()
                            }
                        }
                    }
                })
            }

            // 검색 관련 UI 설정
            btnSearch.setOnClickListener {
                if (searchLayout.isVisible) {
                    // 검색창이 보이는 상태에서 검색 버튼을 누르면 검색 종료
                    closeSearch()
                } else {
                    // 검색창이 숨겨진 상태에서 검색 버튼을 누르면 검색 시작
                    openSearch()
                }
            }

            btnClearSearch.setOnClickListener {
                searchEditText.text.clear()
                resetSearch()
                btnClearSearch.visibility = View.GONE
            }

            searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    btnClearSearch.isVisible = !s.isNullOrEmpty()
                    filterItems(s?.toString() ?: "")
                }
            })

            searchEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
                    true
                } else {
                    false
                }
            }

            btnSort.setOnClickListener {
                showSortOptions()
            }
        }
    }

    private fun openSearch() {
        binding.apply {
            searchLayout.isVisible = true
            searchDivider.isVisible = true
            searchEditText.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        binding.apply {
            searchLayout.isVisible = false
            searchDivider.isVisible = false
            searchEditText.text.clear()
            resetSearch()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        }
    }

    private fun resetSearch() {
        binding.apply {
            emptyStateLayout.isVisible = false
            recyclerView.isVisible = true
            adapter.submitList(itemsList.toList())
        }
    }

    private fun filterItems(query: String) {
        if (query.isBlank()) {
            resetSearch()
            return
        }

        val filteredList = itemsList.filter { item ->
            item.npayKorNm?.contains(query, ignoreCase = true) == true ||
                    item.itemNm?.contains(query, ignoreCase = true) == true
        }

        if (filteredList.isEmpty()) {
            binding.emptyStateLayout.isVisible = true
            binding.emptyStateText.text = "검색 결과가 없습니다."
            binding.recyclerView.isVisible = false
        } else {
            binding.emptyStateLayout.isVisible = false
            binding.recyclerView.isVisible = true
            adapter.submitList(filteredList)
        }
    }

    private fun loadNonCoveredItems() {
        hospitalId?.let { ykiho ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    isLoading = true
                    showLoading()

                    val result = viewModel.fetchNonPaymentItemsOnly(ykiho, currentPage)

                    if (result.items.isEmpty() && currentPage == 1) {
                        showEmptyState()
                    } else {
                        hideEmptyState()
                        itemsList.addAll(result.items)
                        adapter.submitList(itemsList.toList())
                        hasMoreItems = result.hasMore
                    }
                } catch (e: Exception) {
                    showError("비급여 항목을 불러오는데 실패했습니다.")
                } finally {
                    isLoading = false
                    hideLoading()
                }
            }
        } ?: run {
            showError("병원 정보가 올바르지 않습니다.")
        }
    }

    private fun loadMoreItems() {
        if (isLoading || !hasMoreItems) return

        currentPage++
        loadNonCoveredItems()
    }

    private fun showLoading() {
        binding.progressBar.isVisible = true
    }

    private fun hideLoading() {
        binding.progressBar.isVisible = false
    }

    private fun showSortOptions() {
        val options = arrayOf("금액 높은 순", "금액 낮은 순", "이름 순")
        AlertDialog.Builder(requireContext())
            .setTitle("정렬 방식 선택")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> itemsList.sortByDescending { it.curAmt?.toIntOrNull() ?: 0 }
                    1 -> itemsList.sortBy { it.curAmt?.toIntOrNull() ?: 0 }
                    2 -> itemsList.sortBy { it.npayKorNm }
                }
                adapter.submitList(itemsList.toList())
            }
            .show()
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
