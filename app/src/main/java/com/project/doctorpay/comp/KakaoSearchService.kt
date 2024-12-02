package com.project.doctorpay.comp

import android.content.Context
import android.util.Log
import com.project.doctorpay.location.LocationSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class KakaoSearchService(
    private val context: Context
) {
    private val client = OkHttpClient()

    companion object {
        private const val REST_API_KEY = "d27b50429aadfd71ce821e898e3b2629"
        private const val SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"
    }

    // 좌표로부터 주소를 가져오는 함수
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$longitude&y=$latitude"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "KakaoAK $REST_API_KEY")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext "주소를 찾을 수 없습니다"

            val jsonObject = JSONObject(responseBody)
            val documents = jsonObject.optJSONArray("documents")

            if (documents?.length() == 0) return@withContext "주소를 찾을 수 없습니다"

            val address = documents?.getJSONObject(0)
                ?.optJSONObject("address")

            buildString {
                address?.optString("region_1depth_name")?.let { append(it).append(" ") }
                address?.optString("region_2depth_name")?.let { append(it).append(" ") }
                address?.optString("region_3depth_name")?.let { append(it) }
            }
        } catch (e: Exception) {
            Log.e("KakaoSearchService", "Error getting address", e)
            "주소를 찾을 수 없습니다"
        }
    }

    suspend fun searchPlaces(query: String): List<LocationSearchItem> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?query=$encodedQuery&size=15&radius=20000"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "KakaoAK $REST_API_KEY")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            Log.d("KakaoSearchService", "Search Response: $responseBody")

            val jsonObject = JSONObject(responseBody)
            val documents = jsonObject.optJSONArray("documents") ?: return@withContext emptyList()

            val results = mutableListOf<LocationSearchItem>()

            for (i in 0 until documents.length()) {
                val place = documents.getJSONObject(i)
                val title = place.optString("place_name", "").takeIf { it.isNotEmpty() }
                    ?: place.optString("address_name", "")

                results.add(
                    LocationSearchItem(
                        title = title,
                        address = place.optString("address_name", ""),
                        roadAddress = place.optString("road_address_name", ""),
                        latitude = place.optString("y", "0.0").toDouble(),
                        longitude = place.optString("x", "0.0").toDouble()
                    )
                )
            }

            results.distinctBy { "${it.latitude}${it.longitude}" }

        } catch (e: Exception) {
            Log.e("KakaoSearchService", "Error searching places", e)
            emptyList()
        }
    }
}