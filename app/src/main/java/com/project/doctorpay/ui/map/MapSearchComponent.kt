package com.project.doctorpay.ui.map

import android.content.Context
import android.location.Geocoder
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.databinding.ComponentMapSearchBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


class MapSearchComponent @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ComponentMapSearchBinding.inflate(LayoutInflater.from(context), this, true)
    private var searchJob: Job? = null
    private var onLocationSelectedListener: ((LatLng) -> Unit)? = null
    private val geocoder: Geocoder by lazy { Geocoder(context) }
    private val inputMethodManager: InputMethodManager by lazy {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private val searchAdapter = SearchResultAdapter { location ->
        onLocationSelectedListener?.invoke(location)
        clearSearchResults()
        hideKeyboard()
        binding.searchInput.clearFocus()
    }

    init {
        setupViews()
        setupSearchInput()
        setupTouchInteraction()
    }

    private fun setupViews() {
        binding.searchResultsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
            visibility = View.GONE

            // 결과 목록에 애니메이션 효과 추가
            alpha = 0f
            translationY = -50f
        }

        // 바깥 영역 터치 처리 개선
        binding.root.setOnClickListener {
            if (binding.searchResultsList.visibility == View.VISIBLE) {
                animateSearchResultsHide()
            }
        }
    }

    private fun setupSearchInput() {
        binding.searchInput.apply {
            doAfterTextChanged { text ->
                if (text?.length ?: 0 >= 2) { // 최소 2글자 이상일 때만 검색
                    performSearch(text.toString())
                } else {
                    clearSearchResults()
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val searchText = text.toString()
                    if (searchText.isNotEmpty()) {
                        performSearch(searchText)
                    }
                    true
                } else {
                    false
                }
            }

            // 포커스 변경 시 부드러운 전환
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    if (text?.length ?: 0 >= 2) {
                        animateSearchResultsShow()
                    }
                } else {
                    postDelayed({
                        if (!binding.searchResultsList.isHovered) {
                            animateSearchResultsHide()
                        }
                    }, 150)
                }
            }
        }
    }

    private fun setupTouchInteraction() {
        // 검색 결과 목록 영역의 터치 이벤트 처리
        binding.searchResultsList.setOnTouchListener { _, _ ->
            binding.searchInput.clearFocus()
            false
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(300) // 디바운스 시간 감소로 반응성 향상
                val results = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(query, 5)?.mapNotNull { address ->
                        val latLng = LatLng(address.latitude, address.longitude)
                        SearchResult(
                            address.getAddressLine(0) ?: "",
                            latLng
                        )
                    } ?: emptyList()
                }
                if (results.isNotEmpty()) {
                    animateSearchResultsShow()
                    searchAdapter.submitList(results)
                } else {
                    showNoResults()
                }
            } catch (e: IOException) {
                showError()
            }
        }
    }

    private fun animateSearchResultsShow() {
        binding.searchResultsList.apply {
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .start()
        }
    }

    private fun animateSearchResultsHide() {
        binding.searchResultsList.animate()
            .alpha(0f)
            .translationY(-50f)
            .setDuration(200)
            .withEndAction {
                binding.searchResultsList.visibility = View.GONE
            }
            .start()
    }

    private fun showNoResults() {
        // 결과 없음 상태 표시
        binding.searchResultsList.visibility = View.VISIBLE
        searchAdapter.submitList(emptyList())
        // 필요한 경우 "검색 결과가 없습니다" 메시지 표시
    }

    private fun showError() {
        // 에러 상태 표시
        binding.searchResultsList.visibility = View.GONE
        // 필요한 경우 에러 메시지 표시
    }

    private fun clearSearchResults() {
        animateSearchResultsHide()
    }

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    // 외부에서 호출하는 메서드들은 그대로 유지
    fun clearSearch() {
        binding.searchInput.text?.clear()
        clearSearchResults()
        hideKeyboard()
        binding.searchInput.clearFocus()
    }

    fun setOnLocationSelectedListener(listener: (LatLng) -> Unit) {
        onLocationSelectedListener = listener
    }

    data class SearchResult(
        val address: String,
        val location: LatLng
    )
}