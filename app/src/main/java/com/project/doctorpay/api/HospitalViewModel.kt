package com.project.doctorpay.api

import DgsbjtInfoItem
import DgsbjtInfoResponse
import HospitalInfoItem
import HospitalInfoResponse
import NonPaymentItem
import NonPaymentResponse
import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.inferDepartments
import com.project.doctorpay.network.NetworkModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.simpleframework.xml.core.ElementException
import retrofit2.HttpException
import retrofit2.Response
import java.io.EOFException

class HospitalViewModel(
    private val healthInsuranceApi: HealthInsuranceApi
) : ViewModel() {

    private val _hospitals = MutableStateFlow<List<HospitalInfo>>(emptyList())
    val hospitals: StateFlow<List<HospitalInfo>> = _hospitals

    private val _filteredHospitals = MutableStateFlow<List<HospitalInfo>>(emptyList())
    val filteredHospitals: StateFlow<List<HospitalInfo>> = _filteredHospitals

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentPage = 1
    private val pageSize = 100
    private var isLastPage = false

    private fun formatCoordinate(value: Double): String {
        return String.format("%.8f", value)  // 좌표 정밀도 유지
    }

    fun fetchNearbyHospitals(latitude: Double, longitude: Double, radius: Int = 10000) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 재시도 로직 추가
                var retryCount = 0
                var success = false
                var lastException: Exception? = null

                while (retryCount < 3 && !success) {
                    try {
                        val response = healthInsuranceApi.getHospitalInfo(
                            serviceKey = NetworkModule.getServiceKey(),
                            pageNo = currentPage,
                            numOfRows = pageSize,
                            xPos = formatCoordinate(longitude),
                            yPos = formatCoordinate(latitude),
                            radius = radius
                        )

                        when {
                            response.isSuccessful -> {
                                val body = response.body()
                                if (body == null) {
                                    _error.value = "서버 응답이 비어있습니다"
                                    return@launch
                                }

                                val newHospitals = body.body?.items?.itemList ?: emptyList()
                                val combinedHospitals = combineHospitalData(newHospitals, emptyList())

                                if (currentPage == 1) {
                                    _hospitals.value = combinedHospitals
                                } else {
                                    _hospitals.value = _hospitals.value + combinedHospitals
                                }

                                isLastPage = newHospitals.size < pageSize
                                currentPage++

                                filterHospitalsWithin5km(latitude, longitude, _hospitals.value)
                                success = true
                            }
                            response.code() == 429 -> {
                                // Too Many Requests - 잠시 대기 후 재시도
                                delay(1000L * (retryCount + 1))
                            }
                            else -> {
                                _error.value = "서버 오류: ${response.code()} - ${response.message()}"
                                Log.e("HospitalViewModel", "API Error: ${response.errorBody()?.string()}")
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        lastException = e
                        retryCount++
                        if (retryCount < 3) {
                            delay(1000L * retryCount)
                        }
                    }
                }

                if (!success && lastException != null) {
                    handleError(lastException)
                }

            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPagination() {
        currentPage = 1
        isLastPage = false
        _hospitals.value = emptyList()
    }

    private fun filterHospitalsWithin5km(latitude: Double, longitude: Double, hospitals: List<HospitalInfo>) {
        val filteredList = hospitals.filter { hospital ->
            val distance = calculateDistance(
                latitude, longitude,
                hospital.latitude, hospital.longitude
            )
            distance <= 5000 // 5km = 5000m
        }
        _filteredHospitals.value = filteredList
    }


    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    suspend fun fetchHospitalInfo(sidoCd: String, sgguCd: String): Response<HospitalInfoResponse> {
        val response = healthInsuranceApi.getHospitalInfo(
            serviceKey = NetworkModule.getServiceKey(),
            pageNo = 1,
            numOfRows = 100,
            sidoCd = sidoCd,
            sgguCd = sgguCd,
            xPos = "0",
            yPos = "0",
            radius = 0
        )
        Log.d("API_RESPONSE", "Raw Hospital Info Response: ${response.body()}")
        return response
    }


    private suspend fun fetchNonPaymentInfo(): Response<NonPaymentResponse> {
        return healthInsuranceApi.getNonPaymentInfo(
            serviceKey = NetworkModule.getServiceKey(),
            pageNo = 1,
            numOfRows = 100
        )
    }



    private suspend fun fetchDgsbjtInfo(ykiho: String): Response<DgsbjtInfoResponse> {
        return healthInsuranceApi.getDgsbjtInfo(
            serviceKey = NetworkModule.getServiceKey(),
            ykiho = ykiho.trim(),
            pageNo = 1,
            numOfRows = 100
        )
    }

//
//    suspend fun fetchHospitalAndDgsbjtInfo(sidoCd: String, sgguCd: String) {
//        val hospitalResponse = fetchHospitalInfo(sidoCd, sgguCd)
//        if (hospitalResponse.isSuccessful) {
//            val hospitals = hospitalResponse.body()?.body?.items?.itemList ?: emptyList()
//            hospitals.forEach { hospital ->
//                hospital.ykiho?.let { ykiho ->
//                    val dgsbjtResponse = fetchDgsbjtInfo(ykiho)
//                    if (dgsbjtResponse.isSuccessful) {
//                        // DgsbjtInfo 처리 로직
//                        val dgsbjtItems = dgsbjtResponse.body()?.body?.items ?: emptyList()
//                        // 여기서 hospital 정보와 dgsbjtItems를 결합하거나 처리합니다.
//                    } else {
//                        Log.e("API", "Failed to fetch DgsbjtInfo for ykiho: $ykiho")
//                    }
//                }
//            }
//        } else {
//            Log.e("API", "Failed to fetch HospitalInfo")
//        }
//    }

    private fun updateHospitalWithDgsbjtInfo(hospital: HospitalInfo, dgsbjtItems: List<DgsbjtInfoItem>?): HospitalInfo {
        val dgsbjtCodes = dgsbjtItems?.mapNotNull { it.dgsbjtCd } ?: emptyList()
        val updatedDepartments = inferDepartments(hospital.name, hospital.nonPaymentItems, dgsbjtCodes)
        val updatedDepartmentCategories = getDepartmentCategories(updatedDepartments)
        return hospital.copy(
            departments = updatedDepartments,
            departmentCategories = updatedDepartmentCategories
        )
    }

    fun filterHospitalsByCategory(hospitals: List<HospitalInfo>, category: DepartmentCategory?): List<HospitalInfo> {
        val filteredHospitals = if (category == null) {
            hospitals
        } else {
            hospitals.filter { hospital ->
                hospital.departmentCategories.contains(category.name)
            }
        }

        Log.d("HospitalViewModel", "Filtering for category: ${category?.name}, Results: ${filteredHospitals.size}")

        filteredHospitals.forEach { hospital ->
            Log.d("HospitalViewModel", "Filtered Hospital: ${hospital.name}, Categories: ${hospital.departmentCategories.joinToString()}")
        }

        return filteredHospitals
    }

    private fun combineHospitalData(
        hospitalInfoItems: List<HospitalInfoItem>?,
        nonPaymentItems: List<NonPaymentItem>?
    ): List<HospitalInfo> {
        val nonPaymentMap = nonPaymentItems?.groupBy { it.yadmNm } ?: emptyMap()
        return hospitalInfoItems?.mapNotNull { hospitalInfo ->
            val nonPaymentItemsForHospital = nonPaymentMap[hospitalInfo.yadmNm] ?: emptyList()
            val latitude = hospitalInfo.YPos?.toDoubleOrNull() ?: 0.0
            val longitude = hospitalInfo.XPos?.toDoubleOrNull() ?: 0.0
            val departments = inferDepartments(hospitalInfo.yadmNm ?: "", nonPaymentItemsForHospital, hospitalInfo.dgsbjtCd?.split(",") ?: emptyList())
            val departmentCategories = getDepartmentCategories(departments)

            HospitalInfo(
                location = LatLng(latitude, longitude),
                name = hospitalInfo.yadmNm ?: "",
                address = hospitalInfo.addr ?: "",
                departments = departments,
                departmentCategories = departmentCategories,
                time = "",
                phoneNumber = hospitalInfo.telno ?: "",
                state = "",
                rating = 0.0,
                latitude = latitude,
                longitude = longitude,
                nonPaymentItems = nonPaymentItemsForHospital,
                clCdNm = hospitalInfo.clCdNm ?: "",
                ykiho = hospitalInfo.ykiho ?: ""
            )
        } ?: emptyList()
    }


    fun loadMoreHospitals(latitude: Double, longitude: Double) {
        if (!isLoading.value && !isLastPage) {
            fetchNearbyHospitals(latitude, longitude)
        }
    }

    private fun getDepartmentCategory(departments: String): String {
        val departmentSet = departments.split(", ").toSet()
        return DepartmentCategory.values().find { category ->
            departmentSet.any { dept -> dept == category.categoryName }
        }?.name ?: DepartmentCategory.OTHER_SPECIALTIES.name
    }



    private fun handleError(e: Exception) {
        Log.e("HospitalViewModel", "데이터 불러오기 오류", e)
        val errorMessage = when (e) {
            is HttpException -> {
                when (e.code()) {
                    429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
                    500, 502, 503, 504 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                    else -> "HTTP 오류: ${e.code()}"
                }
            }
            is ElementException -> "데이터 형식 오류: ${e.message}"
            is EOFException -> "서버 응답이 불완전합니다. 다시 시도해주세요."
            else -> "알 수 없는 오류: ${e.message}"
        }

        Log.e("HospitalViewModel", errorMessage)
        _error.value = errorMessage
    }

    private fun getDepartmentCategories(departments: List<String>): List<String> {
        return departments.map { dept ->
            DepartmentCategory.values().find { it.categoryName == dept }?.name ?: DepartmentCategory.OTHER_SPECIALTIES.name
        }.distinct()
    }


    fun fetchHospitalData(sidoCd: String, sgguCd: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val hospitalInfoResponse = fetchHospitalInfo(sidoCd, sgguCd)
                val nonPaymentResponse = fetchNonPaymentInfo()

                if (!hospitalInfoResponse.isSuccessful || !nonPaymentResponse.isSuccessful) {
                    throw Exception("API Error: Hospital Info or Non-Payment Info request failed")
                }

                val combinedHospitals = combineHospitalData(
                    hospitalInfoResponse.body()?.body?.items?.itemList,
                    nonPaymentResponse.body()?.body?.items
                )

                val updatedHospitals = combinedHospitals.map { hospital ->
                    val dgsbjtInfoResponse = fetchDgsbjtInfo(hospital.ykiho)
                    if (dgsbjtInfoResponse.isSuccessful) {
                        updateHospitalWithDgsbjtInfo(hospital, dgsbjtInfoResponse.body()?.body?.items?.itemList)
                    } else {
                        Log.e("HospitalViewModel", "Failed to fetch DgsbjtInfo for ykiho: ${hospital.ykiho}")
                        hospital
                    }
                }

                Log.d("HospitalViewModel", "Combined hospitals: ${updatedHospitals.size}")
                updatedHospitals.forEach { hospital ->
                    Log.d("HospitalViewModel", "Hospital: ${hospital.name}, Categories: ${hospital.departmentCategories.joinToString()}")
                }

                _hospitals.value = updatedHospitals
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }



    fun searchHospitals(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val filteredHospitals = _hospitals.value.filter {
                    it.name.contains(query, ignoreCase = true)
                }
                _hospitals.value = filteredHospitals
            } catch (e: Exception) {
                _error.value = "검색 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


}