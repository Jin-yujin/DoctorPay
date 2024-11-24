package com.project.doctorpay.ui.map

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.project.doctorpay.databinding.HospitalFilterLayoutBinding
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.OperationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HospitalFilterComponent @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding = HospitalFilterLayoutBinding.inflate(LayoutInflater.from(context), this, true)
    private var searchJob: Job? = null
    private var onFilterChanged: ((FilterCriteria) -> Unit)? = null
    private var currentCategory: DepartmentCategory? = null
    private var currentSearchQuery: String = ""
    private var isEmergencyOnly: Boolean = false

    data class FilterCriteria(
        val searchQuery: String,
        val category: DepartmentCategory?,
        val emergencyOnly: Boolean
    ) {
        override fun toString(): String {
            return "FilterCriteria(query='$searchQuery', category=${category?.categoryName}, emergency=$emergencyOnly)"
        }
    }

    init {
        setupSearchView()
        setupFilterChips()
    }

    private fun setupSearchView() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                binding.clearButton.isVisible = query.isNotEmpty()
                currentSearchQuery = query
                debounceSearch(query)
            }
        })

        binding.clearButton.setOnClickListener {
            binding.searchEditText.setText("")
            currentSearchQuery = ""
            notifyFilterChanged()
        }
    }

    private fun setupFilterChips() {
        // 기존 칩을 모두 제거
        binding.filterChipGroup.removeAllViews()

        // 전체 칩 추가
        addFilterChip("전체", null)

        // 주요 진료과목 카테고리 칩 추가
        val mainCategories = listOf(
            DepartmentCategory.INTERNAL_MEDICINE to "내과",
            DepartmentCategory.SURGERY to "외과",
            DepartmentCategory.DENTISTRY to "치과",
            DepartmentCategory.OBSTETRICS to "산부인과",
            DepartmentCategory.ORIENTAL_MEDICINE to "한의원"
        )

        mainCategories.forEach { (category, displayName) ->
            addFilterChip(displayName, category)
        }

        // 응급실 칩 추가 (특별 케이스)
        addFilterChip("응급실", null, true)

        binding.filterChipGroup.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            when {
                chip?.tag is DepartmentCategory -> {
                    currentCategory = chip.tag as DepartmentCategory
                    isEmergencyOnly = false
                }
                chip?.tag == "emergency" -> {
                    currentCategory = null
                    isEmergencyOnly = true
                }
                else -> {
                    currentCategory = null
                    isEmergencyOnly = false
                }
            }
            notifyFilterChanged()
        }
    }

    private fun addFilterChip(text: String, category: DepartmentCategory?, isEmergency: Boolean = false) {
        val chip = Chip(context).apply {
            this.text = text
            isCheckable = true
            tag = when {
                isEmergency -> "emergency"
                category != null -> category
                else -> "all"
            }
        }
        binding.filterChipGroup.addView(chip)

        // 전체 칩은 기본적으로 선택
        if (category == null && !isEmergency) {
            chip.isChecked = true
        }
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Job()).launch {
            delay(300)
            notifyFilterChanged()
        }
    }

    private fun notifyFilterChanged() {
        onFilterChanged?.invoke(
            FilterCriteria(
                searchQuery = currentSearchQuery,
                category = currentCategory,
                emergencyOnly = isEmergencyOnly
            )
        )
    }

    fun setOnFilterChangedListener(listener: (FilterCriteria) -> Unit) {
        onFilterChanged = listener
    }

    fun updateResultCount(count: Int) {
        binding.resultCount.text = "총 ${count}개의 병원"
    }

    fun resetFilters() {
        binding.searchEditText.setText("")
        currentSearchQuery = ""
        currentCategory = null
        isEmergencyOnly = false
        binding.filterChipGroup.children.firstOrNull()?.let { firstChip ->
            (firstChip as? Chip)?.isChecked = true
        }
    }

    fun getCurrentFilters(): FilterCriteria {
        return FilterCriteria(
            searchQuery = currentSearchQuery,
            category = currentCategory,
            emergencyOnly = isEmergencyOnly
        )
    }
}