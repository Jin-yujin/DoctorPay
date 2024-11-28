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
    }

    private fun setupViews() {
        binding.searchResultsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
            visibility = View.GONE
        }

        // 검색 결과 외부 클릭시 결과 숨김과 키보드 내림
        binding.root.setOnClickListener {
            clearSearchResults()
            hideKeyboard()
            binding.searchInput.clearFocus()
        }
    }

    private fun setupSearchInput() {
        binding.searchInput.apply {
            doAfterTextChanged { text ->
                if (text?.isNotEmpty() == true) {
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
                        hideKeyboard()
                    }
                    true
                } else {
                    false
                }
            }

            // 포커스 변경 리스너 추가
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    hideKeyboard()
                }
            }
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                delay(500) // Debounce search
                val results = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(query, 5)?.mapNotNull { address ->
                        val latLng = LatLng(address.latitude, address.longitude)
                        SearchResult(
                            address.getAddressLine(0) ?: "",
                            latLng
                        )
                    } ?: emptyList()
                }
                updateSearchResults(results)
            } catch (e: IOException) {
                // Handle geocoding error
            }
        }
    }

    private fun updateSearchResults(results: List<SearchResult>) {
        binding.searchResultsList.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
        searchAdapter.submitList(results)
    }

    private fun clearSearchResults() {
        binding.searchResultsList.visibility = View.GONE
        searchAdapter.submitList(emptyList())
    }

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

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