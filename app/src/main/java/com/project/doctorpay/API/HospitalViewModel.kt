package com.project.doctorpay.api

import HospitalInfoItem
import NonPaymentItem
import android.util.Log
import androidx.lifecycle.*
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.DB.HospitalInfo
import com.project.doctorpay.DB.inferDepartments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HospitalViewModel(private val healthInsuranceApi: HealthInsuranceApi) : ViewModel() {
    private val _hospitals = MutableStateFlow<List<HospitalInfo>>(emptyList())
    val hospitals: StateFlow<List<HospitalInfo>> = _hospitals

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun fetchHospitalData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val hospitalInfoResponse = healthInsuranceApi.getHospitalInfo(
                    serviceKey = "0H0upZmR4M4DyfwLLid%2F7qyTNc%2BVxA0cg0mMk9zOU6V4zdapEmdXA10%2Fz69RvH4ey70OMYofiJ%2FEtqZlT3JC0w%3D%3D",
                    pageNo = 1,
                    numOfRows = 10
                )
                val nonPaymentResponse = healthInsuranceApi.getNonPaymentInfo(
                    serviceKey = "0H0upZmR4M4DyfwLLid%2F7qyTNc%2BVxA0cg0mMk9zOU6V4zdapEmdXA10%2Fz69RvH4ey70OMYofiJ%2FEtqZlT3JC0w%3D%3D",
                    pageNo = 1,
                    numOfRows = 10
                )

                val combinedHospitals = combineHospitalData(
                    hospitalInfoResponse.body?.items,
                    nonPaymentResponse.body?.items
                )
                _hospitals.value = combinedHospitals
            } catch (e: Exception) {
                _error.value = "데이터를 불러오는 중 오류가 발생했습니다: ${e.message}"
                Log.e("Data","데이터를 불러오는 중 오류가 발생했습니다: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getHospitalById(id: String): LiveData<HospitalInfo> {
        return liveData {
            val hospital = _hospitals.value.find { it.name == id }
            emit(hospital ?: throw IllegalArgumentException("Hospital not found"))
        }
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
            HospitalInfo(
                location = LatLng(latitude, longitude),
                name = hospitalInfo.yadmNm ?: "",
                address = "${hospitalInfo.sidoCdNm ?: ""} ${hospitalInfo.sgguCdNm ?: ""} ${hospitalInfo.emdongNm ?: ""}".trim(),
                department = inferDepartments(hospitalInfo, nonPaymentItemsForHospital),
                time = "", // API에서 제공되지 않는 정보
                phoneNumber = hospitalInfo.telno ?: "",
                state = "", // API에서 제공되지 않는 정보
                rating = 0.0, // API에서 제공되지 않는 정보, 필요하다면 다른 방식으로 획득해야 함
                latitude = latitude,
                longitude = longitude,
                nonPaymentItems = nonPaymentItemsForHospital
            )
        } ?: emptyList()
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