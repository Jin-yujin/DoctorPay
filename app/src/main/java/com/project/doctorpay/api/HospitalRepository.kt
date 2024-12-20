package com.project.doctorpay.api

import HospitalInfoItem
import HospitalInfoResponse
import NonPaymentItem
import NonPaymentResponse
import com.naver.maps.geometry.LatLng
import com.project.doctorpay.db.HospitalInfo
import com.project.doctorpay.db.HospitalTimeInfo
import com.project.doctorpay.db.TimeRange
import com.project.doctorpay.db.toHospitalInfo
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.network.NetworkModule.healthInsuranceApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// 병원 정보 Repository
class HospitalRepository(private val api: HealthInsuranceApi) {

    // 병원 정보와 비급여 정보를 동시에 조회하는 통합 함수
    suspend fun getHospitalWithNonPaymentInfo(
        serviceKey: String,
        pageNo: Int,
        numOfRows: Int,
        params: HospitalSearchParams
    ): List<HospitalInfo> = coroutineScope {
        // 병원 정보와 비급여 정보를 병렬로 조회
        val hospitalInfoDeferred = async {
            api.getHospitalInfo(
                serviceKey = serviceKey,
                pageNo = pageNo,
                numOfRows = numOfRows,
                sidoCd = params.sidoCd,
                sgguCd = params.sgguCd,
                emdongNm = params.emdongNm,
                yadmNm = params.yadmNm,
                zipCd = params.zipCd,
                clCd = params.clCd,
                dgsbjtCd = params.dgsbjtCd,
                xPos = params.xPos,
                yPos = params.yPos,
                radius = params.radius,
                ykiho = params.ykiho
            )
        }

        val nonPaymentDeferred = async {
            api.getNonPaymentInfo(
                serviceKey = serviceKey,
                pageNo = pageNo,
                numOfRows = numOfRows,
                yadmNm = params.yadmNm,
                clCd = params.clCd,
                sidoCd = params.sidoCd,
                sgguCd = params.sgguCd
            )
        }

        // 두 API 호출 결과를 기다림
        val hospitalResponse = hospitalInfoDeferred.await()
        val nonPaymentResponse = nonPaymentDeferred.await()

        // 응답 결과를 통합하여 처리
        processResponses(hospitalResponse, nonPaymentResponse)
    }

    private suspend fun processResponses(
        hospitalResponse: Response<HospitalInfoResponse>,
        nonPaymentResponse: Response<NonPaymentResponse>
    ): List<HospitalInfo> {
        if (!hospitalResponse.isSuccessful || !nonPaymentResponse.isSuccessful) {
            return emptyList()
        }

        val nonPaymentMap = nonPaymentResponse.body()?.body?.items?.groupBy { it.yadmNm } ?: emptyMap()

        return hospitalResponse.body()?.body?.items?.itemList?.mapNotNull { hospitalItem ->
            val nonPaymentItems = nonPaymentMap[hospitalItem.yadmNm] ?: emptyList()
            val timeInfo = hospitalItem.ykiho?.let { getHospitalTimeInfo(it) }
            hospitalItem.toHospitalInfo(nonPaymentItems, timeInfo)
        } ?: emptyList()
    }

    private suspend fun getHospitalTimeInfo(ykiho: String): HospitalTimeInfo? {
        return try {
            val response = api.getDtlInfo(
                serviceKey = NetworkModule.getServiceKey(),
                ykiho = ykiho
            )
            if (response.isSuccessful) {
                response.body()?.body?.items?.item?.let { detailItem ->
                    HospitalTimeInfo(
                        weekdayTime = TimeRange(
                            parseTime(detailItem.trmtMonStart),
                            parseTime(detailItem.trmtMonEnd)
                        ),
                        saturdayTime = TimeRange(
                            parseTime(detailItem.trmtSatStart),
                            parseTime(detailItem.trmtSatEnd)
                        ),
                        sundayTime = if (detailItem.noTrmtSun == "Y") null else TimeRange(
                            parseTime(detailItem.trmtSunStart),
                            parseTime(detailItem.trmtSunEnd)
                        ),
                        lunchTime = parseLunchTime(detailItem.lunchWeek),
                        saturdayLunchTime = parseLunchTime(detailItem.lunchSat),
                        isEmergencyDay = detailItem.emyDayYn == "Y",
                        isEmergencyNight = detailItem.emyNgtYn == "Y",
                        emergencyDayContact = detailItem.emyDayTelNo1,
                        emergencyNightContact = detailItem.emyNgtTelNo1
                    )
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

// 병원 검색 파라미터를 위한 데이터 클래스
data class HospitalSearchParams(
    val sidoCd: String? = null,
    val sgguCd: String? = null,
    val emdongNm: String? = null,
    val yadmNm: String? = null,
    val zipCd: String? = null,
    val clCd: String? = null,
    val dgsbjtCd: String? = null,
    val xPos: String,
    val yPos: String,
    val radius: Int,
    val ykiho: String? = null
)

// 기존 확장 함수들은 그대로 유지
private fun parseTime(timeStr: String?): LocalTime? {
    if (timeStr.isNullOrBlank()) return null
    return try {
        val cleanTime = timeStr.trim().replace("""[^0-9]""".toRegex(), "")
        if (cleanTime.length != 4) return null
        val hour = cleanTime.substring(0, 2).toInt()
        val minute = cleanTime.substring(2, 4).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        LocalTime.of(hour, minute)
    } catch (e: Exception) {
        null
    }
}

private fun parseLunchTime(lunchTimeStr: String?): TimeRange? {
    if (lunchTimeStr.isNullOrBlank() || lunchTimeStr == "없음" || lunchTimeStr == "점심시간 없음") {
        return null
    }

    // 기본 점심시간
    return TimeRange(LocalTime.of(12, 30), LocalTime.of(13, 30))
}