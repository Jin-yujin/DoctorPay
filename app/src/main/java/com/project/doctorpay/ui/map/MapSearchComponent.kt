package com.project.doctorpay.ui.map

import android.content.Context
import android.location.Geocoder
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.databinding.ComponentMapSearchBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder


class MapSearchComponent @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {


    private val binding = ComponentMapSearchBinding.inflate(LayoutInflater.from(context), this, true)
    private var searchJob: Job? = null
    private var onLocationSelectedListener: ((LatLng) -> Unit)? = null
    private val client = OkHttpClient()
    private val inputMethodManager: InputMethodManager by lazy {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    companion object {
        private const val KAKAO_REST_API_KEY = "d27b50429aadfd71ce821e898e3b2629"
        private const val SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json"
        private const val KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
        private const val MIN_QUERY_LENGTH = 2
        private const val SEARCH_DEBOUNCE_TIME = 300L
        private const val MAX_RESULTS = 10
    }

    private val searchAdapter = SearchResultAdapter { latLng ->
        onLocationSelectedListener?.invoke(latLng)
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
            alpha = 0f
            translationY = -50f
        }

        binding.root.setOnClickListener {
            if (binding.searchResultsList.visibility == View.VISIBLE) {
                animateSearchResultsHide()
            }
        }
    }

    private fun setupSearchInput() {
        binding.searchInput.apply {
            doAfterTextChanged { text ->
                if (text?.length ?: 0 >= MIN_QUERY_LENGTH) {
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

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    if (text?.length ?: 0 >= MIN_QUERY_LENGTH) {
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
                delay(SEARCH_DEBOUNCE_TIME)

                val results = withContext(Dispatchers.IO) {
                    val addressResults = searchAddress(query)
                    val keywordResults = if (addressResults.isEmpty()) {
                        searchKeyword(query)
                    } else {
                        emptyList()
                    }
                    (addressResults + keywordResults)
                        .distinctBy { "${it.latitude}${it.longitude}" }
                        .take(MAX_RESULTS)
                }

                if (results.isNotEmpty()) {
                    animateSearchResultsShow()
                    searchAdapter.submitList(results)
                } else {
                    showNoResults()
                }
            } catch (e: Exception) {
                Log.e("MapSearch", "Search error", e)
                showError()
            }
        }
    }

    private suspend fun searchKeyword(query: String): List<SearchResultAdapter.SearchResult> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$KEYWORD_URL?query=$encodedQuery")
                .addHeader("Authorization", "KakaoAK $KAKAO_REST_API_KEY")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyList()
            Log.d("MapSearch", "Keyword search response: $responseBody")

            val jsonObject = JSONObject(responseBody)
            val documents = jsonObject.getJSONArray("documents")

            List(documents.length()) { i ->
                val document = documents.getJSONObject(i)
                SearchResultAdapter.SearchResult(
                    address = "${document.getString("place_name")} (${document.getString("address_name")})",
                    latitude = document.getString("y").toDouble(),
                    longitude = document.getString("x").toDouble()
                )
            }
        } catch (e: Exception) {
            Log.e("MapSearch", "Keyword search error", e)
            emptyList()
        }
    }

    private suspend fun searchAddress(query: String): List<SearchResultAdapter.SearchResult> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("$SEARCH_URL?query=$encodedQuery")
                .addHeader("Authorization", "KakaoAK $KAKAO_REST_API_KEY")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return emptyList()
            Log.d("MapSearch", "Address search response: $responseBody")

            val jsonObject = JSONObject(responseBody)
            val documents = jsonObject.getJSONArray("documents")

            List(documents.length()) { i ->
                val document = documents.getJSONObject(i)
                SearchResultAdapter.SearchResult(
                    address = document.getString("address_name"),
                    latitude = document.getString("y").toDouble(),
                    longitude = document.getString("x").toDouble()
                )
            }
        } catch (e: Exception) {
            Log.e("MapSearch", "Address search error", e)
            emptyList()
        }
    }

    private fun showNoResults() {
        binding.searchResultsList.visibility = View.VISIBLE
        searchAdapter.submitList(emptyList())
        Snackbar.make(binding.root, "검색 결과가 없습니다", Snackbar.LENGTH_SHORT).show()
    }

    private fun showError() {
        binding.searchResultsList.visibility = View.GONE
        Snackbar.make(binding.root, "검색 중 오류가 발생했습니다", Snackbar.LENGTH_SHORT).show()
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

}