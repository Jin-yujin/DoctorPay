package com.project.doctorpay.api

import HospitalInfoItem
import HospitalInfoResponse
import NonPaymentItem
import NonPaymentResponse
import android.util.Log
import androidx.lifecycle.*
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.inferDepartments
import com.project.doctorpay.network.NetworkModule
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error


    private suspend fun fetchHospitalInfo(sidoCd: String, sgguCd: String): Response<HospitalInfoResponse> {
        return healthInsuranceApi.getHospitalInfo(
            serviceKey = NetworkModule.getDecodedServiceKey(),
            pageNo = 1,
            numOfRows = 10,
            sidoCd = sidoCd,
            sgguCd = sgguCd
        )
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
        Log.d("HospitalViewModel", "Hospital info items: ${hospitalInfoItems?.size}")
        Log.d("HospitalViewModel", "Non-payment items: ${nonPaymentItems?.size}")

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
                rating = 0.0, // API에서 제공되지 않는 정보
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

    fun getHospitalById(id: String): LiveData<HospitalInfo?> = liveData {
        Log.d("HospitalViewModel", "Getting hospital by id: $id")
        while (hospitals.value.isEmpty()) {
            delay(100) // 데이터가 로드될 때까지 잠시 대기
        }
        val hospital = hospitals.value.find { it.name == id }
        Log.d("HospitalViewModel", "Found hospital: ${hospital?.name}")
        emit(hospital)
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