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
    private val pageSize = 20
    private var isLastPage = false

    private fun formatCoordinate(value: Double): String {
        return String.format("%.8f", value)  // 좌표 정밀도 유지
    }

    fun fetchNearbyHospitals(latitude: Double, longitude: Double, radius: Int = 10000) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                Log.d("HospitalViewModel", "Fetching hospitals with lat=$latitude, lon=$longitude, radius=$radius")

                val response = healthInsuranceApi.getHospitalInfo(
                    serviceKey = NetworkModule.getServiceKey(),
                    pageNo = currentPage,
                    numOfRows = pageSize,
                    xPos = formatCoordinate(longitude),
                    yPos = formatCoordinate(latitude),
                    radius = radius
                )

                Log.d("HospitalViewModel", "API Response Code: ${response.code()}")

                when {
                    response.isSuccessful -> {
                        val body = response.body()
                        Log.d("HospitalViewModel", "Response Body: $body")

                        if (body == null) {
                            _error.value = "서버 응답이 비어있습니다"
                            return@launch
                        }

                        val newHospitals = body.body?.items?.itemList ?: emptyList()
                        Log.d("HospitalViewModel", "Received ${newHospitals.size} hospitals from API")

                        // NonPayment 정보도 함께 가져오기
                        val nonPaymentResponse = fetchNonPaymentInfo()
                        val nonPaymentItems = if (nonPaymentResponse.isSuccessful) {
                            nonPaymentResponse.body()?.body?.items ?: emptyList()
                        } else {
                            emptyList()
                        }

                        val combinedHospitals = combineHospitalData(newHospitals, nonPaymentItems)
                        Log.d("HospitalViewModel", "Combined hospitals: ${combinedHospitals.size}")

                        // 진료과목 정보 가져오기
                        val updatedHospitals = combinedHospitals.map { hospital ->
                            try {
                                val dgsbjtResponse = fetchDgsbjtInfo(hospital.ykiho)
                                if (dgsbjtResponse.isSuccessful) {
                                    updateHospitalWithDgsbjtInfo(hospital, dgsbjtResponse.body()?.body?.items?.itemList)
                                } else {
                                    hospital
                                }
                            } catch (e: Exception) {
                                Log.e("HospitalViewModel", "Error fetching dgsbjt info: ${e.message}")
                                hospital
                            }
                        }

                        if (currentPage == 1) {
                            _hospitals.value = updatedHospitals
                        } else {
                            _hospitals.value = _hospitals.value + updatedHospitals
                        }

                        // 5km 이내 병원만 필터링
                        filterHospitalsWithin5km(latitude, longitude, _hospitals.value)

                        isLastPage = newHospitals.size < pageSize
                        currentPage++
                    }
                    response.code() == 429 -> {
                        Log.d("HospitalViewModel", "Rate limit exceeded, retrying...")
                        delay(1000L)
                        fetchNearbyHospitals(latitude, longitude, radius)
                    }
                    else -> {
                        val errorBody = response.errorBody()?.string()
                        _error.value = "서버 오류: ${response.code()} - ${response.message()}"
                        Log.e("HospitalViewModel", "API Error: $errorBody")
                    }
                }

            } catch (e: Exception) {
                Log.e("HospitalViewModel", "Error fetching hospitals", e)
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
            numOfRows = 20,
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
            numOfRows = 20
        )
    }



    private suspend fun fetchDgsbjtInfo(ykiho: String): Response<DgsbjtInfoResponse> {
        return healthInsuranceApi.getDgsbjtInfo(
            serviceKey = NetworkModule.getServiceKey(),
            ykiho = ykiho.trim(),
            pageNo = 1,
            numOfRows = 20
        )
    }


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
        hospitalInfoItems: List<HospitalInfoItem>?,  // nullable로 변경
        nonPaymentItems: List<NonPaymentItem>?       // nullable로 변경
    ): List<HospitalInfo> {
        Log.d("HospitalViewModel", "Combining data - Hospitals: ${hospitalInfoItems?.size}, NonPayment: ${nonPaymentItems?.size}")

        val nonPaymentMap = nonPaymentItems?.groupBy { it.yadmNm } ?: emptyMap()

        return hospitalInfoItems?.mapNotNull { hospitalInfo ->
            try {
                val nonPaymentItemsForHospital = nonPaymentMap[hospitalInfo.yadmNm] ?: emptyList()
                val latitude = hospitalInfo.YPos?.toDoubleOrNull() ?: return@mapNotNull null
                val longitude = hospitalInfo.XPos?.toDoubleOrNull() ?: return@mapNotNull null

                // 좌표가 유효한 경우만 처리
                if (latitude == 0.0 && longitude == 0.0) {
                    return@mapNotNull null
                }

                val departments = inferDepartments(
                    hospitalInfo.yadmNm ?: "",
                    nonPaymentItemsForHospital,
                    hospitalInfo.dgsbjtCd?.split(",") ?: emptyList()
                )

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
                ).also {
                    Log.d("HospitalViewModel", "Created hospital: ${it.name} at (${it.latitude}, ${it.longitude})")
                }
            } catch (e: Exception) {
                Log.e("HospitalViewModel", "Error creating hospital from item: ${hospitalInfo.yadmNm}", e)
                null
            }
        } ?: emptyList()  // null인 경우 빈 리스트 반환
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