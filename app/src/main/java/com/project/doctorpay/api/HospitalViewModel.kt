package com.project.doctorpay.api

import HospitalInfoItem
import HospitalInfoResponse
import NonPaymentItem
import NonPaymentResponse
import android.util.Log
import androidx.lifecycle.*
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.DepartmentCategory
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.inferDepartments
import com.project.doctorpay.network.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.simpleframework.xml.core.ElementException
import retrofit2.HttpException
import retrofit2.Response

class HospitalViewModel(private val healthInsuranceApi: HealthInsuranceApi) : ViewModel() {
    private val _hospitals = MutableStateFlow<List<HospitalInfo>>(emptyList())
    val hospitals: StateFlow<List<HospitalInfo>> = _hospitals

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error


    suspend fun fetchHospitalInfo(sidoCd: String, sgguCd: String): Response<HospitalInfoResponse> {
        val response = healthInsuranceApi.getHospitalInfo(
            serviceKey = NetworkModule.getDecodedServiceKey(),
            pageNo = 1,
            numOfRows = 10,
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
            numOfRows = 10
        )
    }

    fun fetchHospitalData(sidoCd: String, sgguCd: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val hospitalInfoResponse = fetchHospitalInfo(sidoCd, sgguCd)
                val nonPaymentResponse = fetchNonPaymentInfo()

                Log.d("HospitalViewModel", "Hospital Info Response: ${hospitalInfoResponse.body()}")
                Log.d("HospitalViewModel", "Non-Payment Response: ${nonPaymentResponse.body()}")

                val combinedHospitals = combineHospitalData(
                    hospitalInfoResponse.body()?.body?.items?.itemList,
                    nonPaymentResponse.body()?.body?.items
                )
                _hospitals.value = combinedHospitals
                Log.d("HospitalViewModel", "Combined hospitals size: ${combinedHospitals.size}")
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun combineHospitalData(
        hospitalInfoItems: List<HospitalInfoItem>?,
        nonPaymentItems: List<NonPaymentItem>?
    ): List<HospitalInfo> {
        val nonPaymentMap = nonPaymentItems?.groupBy { it.yadmNm } ?: emptyMap()
        return hospitalInfoItems?.mapNotNull { hospitalInfo ->
            Log.d("HOSPITAL_INFO", "Hospital: ${hospitalInfo.yadmNm}, dgsbjtCd: ${hospitalInfo.dgsbjtCd}")

            val nonPaymentItemsForHospital = nonPaymentMap[hospitalInfo.yadmNm] ?: emptyList()
            val latitude = hospitalInfo.YPos?.toDoubleOrNull() ?: 0.0
            val longitude = hospitalInfo.XPos?.toDoubleOrNull() ?: 0.0
            val departments = inferDepartments(hospitalInfo, nonPaymentItemsForHospital)
            val departmentCategory = when {
                departments.contains("치과") -> "DENTISTRY"
                departments.contains("내과") -> "INTERNAL_MEDICINE"
                departments.contains("외과") -> "SURGERY"
                departments.contains("소아과") || departments.contains("산부인과") -> "PEDIATRICS_OBSTETRICS"
                departments.contains("안과") || departments.contains("이비인후과") -> "SENSORY_ORGANS"
                departments.contains("정형외과") -> "REHABILITATION"
                departments.contains("정신과") || departments.contains("신경과") -> "MENTAL_NEUROLOGY"
                departments.contains("피부과") || departments.contains("비뇨기과") -> "DERMATOLOGY_UROLOGY"
                hospitalInfo.clCdNm == "종합병원" -> "GENERAL_MEDICINE"
                hospitalInfo.clCdNm == "병원" -> "GENERAL_MEDICINE"
                else -> "OTHER_SPECIALTIES"
            }
            HospitalInfo(
                location = LatLng(latitude, longitude),
                name = hospitalInfo.yadmNm ?: "",
                address = "${hospitalInfo.sidoCdNm ?: ""} ${hospitalInfo.sgguCdNm ?: ""} ${hospitalInfo.emdongNm ?: ""}".trim(),
                department = departments,
                departmentCategory = departmentCategory,
                time = "",
                phoneNumber = hospitalInfo.telno ?: "",
                state = "",
                rating = 0.0,
                latitude = latitude,
                longitude = longitude,
                nonPaymentItems = nonPaymentItemsForHospital,
                clCdNm = hospitalInfo.clCdNm ?: ""
            )
        } ?: emptyList()
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

    // 카테고리별로 병원 필터링
    fun getHospitalsByCategory(category: DepartmentCategory): List<HospitalInfo> {
        return _hospitals.value.filter { it.departmentCategory == category.categoryName }
    }

    // 카테고리별 병원 수 반환
    fun getHospitalCountByCategory(): Map<String, Int> {
        return _hospitals.value.groupingBy { it.departmentCategory }.eachCount()
    }

    fun getHospitalById(id: String): LiveData<HospitalInfo> {
        return liveData {
            val hospital = _hospitals.value.find { it.name == id }
            emit(hospital ?: throw IllegalArgumentException("Hospital not found"))
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