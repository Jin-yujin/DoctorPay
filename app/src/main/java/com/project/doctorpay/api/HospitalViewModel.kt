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

class HospitalViewModel(private val healthInsuranceApi: HealthInsuranceApi) : ViewModel() {

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

    fun fetchNearbyHospitals(latitude: Double, longitude: Double, radius: Int = 10000) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = healthInsuranceApi.getHospitalInfo(
                    serviceKey = NetworkModule.getDecodedServiceKey(),
                    pageNo = currentPage,
                    numOfRows = pageSize,
                    xPos = longitude.toString(),
                    yPos = latitude.toString(),
                    radius = radius
                )
                if (response.isSuccessful) {
                    val newHospitals = response.body()?.body?.items?.itemList ?: emptyList()
                    val combinedHospitals = combineHospitalData(newHospitals, emptyList())

                    if (currentPage == 1) {
                        _hospitals.value = combinedHospitals
                    } else {
                        _hospitals.value = _hospitals.value + combinedHospitals
                    }

                    isLastPage = newHospitals.size < pageSize
                    currentPage++

                    filterHospitalsWithin5km(latitude, longitude, _hospitals.value)
                } else {
                    _error.value = "병원 정보를 불러오는데 실패했습니다: ${response.message()}"
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
            serviceKey = NetworkModule.getDecodedServiceKey(),
            pageNo = 1,
            numOfRows = 100,
            sidoCd = sidoCd,
            sgguCd = sgguCd
        )
        Log.d("API_RESPONSE", "Raw Hospital Info Response: ${response.body()}")
        return response
    }


    private suspend fun fetchNonPaymentInfo(): Response<NonPaymentResponse> {
        return healthInsuranceApi.getNonPaymentInfo(
            serviceKey = NetworkModule.getDecodedServiceKey(),
            pageNo = 1,
            numOfRows = 100
        )
    }


    fun fetchHospitalData(sidoCd: String, sgguCd: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val hospitalInfoResponse = fetchHospitalInfo(sidoCd, sgguCd)
                val nonPaymentResponse = fetchNonPaymentInfo()

                if (!hospitalInfoResponse.isSuccessful || !nonPaymentResponse.isSuccessful) {
                    throw Exception("API Error")
                }

                val combinedHospitals = combineHospitalData(
                    hospitalInfoResponse.body()?.body?.items?.itemList,
                    nonPaymentResponse.body()?.body?.items
                )

                // Fetch DgsbjtInfo for each hospital in parallel
                val updatedHospitals = combinedHospitals.map { hospital ->
                    async {
                        val dgsbjtInfoResponse = fetchDgsbjtInfo(hospital.ykiho)
                        if (dgsbjtInfoResponse.isSuccessful) {
                            updateHospitalWithDgsbjtInfo(hospital, dgsbjtInfoResponse.body()?.body?.items)
                        } else {
                            hospital
                        }
                    }
                }.awaitAll()

                _hospitals.value = updatedHospitals
                Log.d("HospitalViewModel", "Combined hospitals size: ${updatedHospitals.size}")
            } catch (e: Exception) {
                Log.e("HospitalViewModel", "Error fetching data", e)
                _error.value = "데이터를 불러오는 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchDgsbjtInfo(ykiho: String): Response<DgsbjtInfoResponse> {
        return healthInsuranceApi.getDgsbjtInfo(
            serviceKey = NetworkModule.getDecodedServiceKey(),
            ykiho = ykiho,
            pageNo = 1,
            numOfRows = 100
        )
    }


    private fun updateHospitalWithDgsbjtInfo(hospital: HospitalInfo, dgsbjtItems: List<DgsbjtInfoItem>?): HospitalInfo {
        val dgsbjtCodes = dgsbjtItems?.mapNotNull { it.dgsbjtCd }?.joinToString(",") ?: ""
        val updatedDepartments = inferDepartments(hospital.name, hospital.nonPaymentItems, dgsbjtCodes)
        val updatedDepartmentCategory = getDepartmentCategory(updatedDepartments)
        return hospital.copy(
            department = updatedDepartments,
            departmentCategory = updatedDepartmentCategory
        )
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
            val departments = inferDepartments(hospitalInfo.yadmNm ?: "", nonPaymentItemsForHospital, hospitalInfo.dgsbjtCd ?: "")
            val departmentCategory = getDepartmentCategory(departments)

            HospitalInfo(
                location = LatLng(latitude, longitude),
                name = hospitalInfo.yadmNm ?: "",
                address = hospitalInfo.addr ?: "",
                department = departments,
                departmentCategory = departmentCategory,
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
            is HttpException -> "HTTP 오류: ${e.code()}"
            is ElementException -> "XML 파싱 오류: ${e.message}"
            else -> "알 수 없는 오류: ${e.message}"
        }
        Log.e("HospitalViewModel", errorMessage)
        _error.value = "데이터를 불러오는 중 오류가 발생했습니다: $errorMessage"
    }


    // API 호출 최적화를 위한 새로운 함수
    fun fetchHospitalDataOptimized(sidoCd: String, sgguCd: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val hospitalInfoResponse = fetchHospitalInfo(sidoCd, sgguCd)
                val nonPaymentResponse = fetchNonPaymentInfo()

                if (!hospitalInfoResponse.isSuccessful || !nonPaymentResponse.isSuccessful) {
                    throw Exception("API Error")
                }

                val combinedHospitals = combineHospitalData(
                    hospitalInfoResponse.body()?.body?.items?.itemList,
                    nonPaymentResponse.body()?.body?.items
                )

                // 병원 목록을 100개씩 나누어 DgsbjtInfo API 호출
                val chunkedHospitals = combinedHospitals.chunked(100)
                val updatedHospitals = chunkedHospitals.flatMap { hospitalChunk ->
                    val ykihos = hospitalChunk.map { it.ykiho }.joinToString(",")
                    val dgsbjtInfoResponse = fetchDgsbjtInfoBatch(ykihos)
                    if (dgsbjtInfoResponse.isSuccessful) {
                        updateHospitalsWithDgsbjtInfo(hospitalChunk, dgsbjtInfoResponse.body()?.body?.items)
                    } else {
                        hospitalChunk
                    }
                }

                _hospitals.value = updatedHospitals
                Log.d("HospitalViewModel", "Combined hospitals size: ${updatedHospitals.size}")
            } catch (e: Exception) {
                Log.e("HospitalViewModel", "Error fetching data", e)
                _error.value = "데이터를 불러오는 중 오류가 발생했습니다: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    private suspend fun fetchDgsbjtInfoBatch(ykihos: String): Response<DgsbjtInfoResponse> {
        return healthInsuranceApi.getDgsbjtInfo(
            serviceKey = NetworkModule.getDecodedServiceKey(),
            ykiho = ykihos,
            pageNo = 1,
            numOfRows = 100
        )
    }



    private fun updateHospitalsWithDgsbjtInfo(hospitals: List<HospitalInfo>, dgsbjtItems: List<DgsbjtInfoItem>?): List<HospitalInfo> {
        val dgsbjtMap = dgsbjtItems?.groupBy { it.dgsbjtCd }?.mapValues { (_, items) -> items.firstOrNull() } ?: emptyMap()
        return hospitals.map { hospital ->
            val dgsbjtCodes = hospital.ykiho.split(",").mapNotNull { ykiho ->
                dgsbjtMap[ykiho]?.dgsbjtCd
            }.joinToString(",")
            val updatedDepartments = inferDepartments(hospital.name, hospital.nonPaymentItems, dgsbjtCodes)
            val updatedDepartmentCategory = getDepartmentCategory(updatedDepartments)
            hospital.copy(
                department = updatedDepartments,
                departmentCategory = updatedDepartmentCategory
            )
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