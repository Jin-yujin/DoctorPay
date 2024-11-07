package com.project.doctorpay.db

import HospitalInfoItem
import NonPaymentItem
import android.os.Parcelable
import com.naver.maps.geometry.LatLng
import kotlinx.parcelize.Parcelize
import com.project.doctorpay.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DepartmentCategory(val categoryName: String, val codes: List<String>, val keywords: List<String>) {
    GENERAL_MEDICINE("일반의", listOf("00", "23", "41"), listOf("일반의")),
    INTERNAL_MEDICINE("내과", listOf("01", "20"), listOf("내과")),
    SURGERY("외과", listOf("04", "05", "06", "07", "08"), listOf("외과", "흉부외과")),
    OBSTETRICS("산부인과", listOf("11"), listOf("소아과", "소아청소년과","산부인과", "여성")),
    MENTAL_NEUROLOGY("정신/신경과", listOf("02", "03"), listOf("정신", "신경")),
    OTOLARYNGOLOGY("이비인후과", listOf("12"), listOf("이비인후과")),
    OPHTHALMOLOGY("안과", listOf("13"), listOf("안과")),
    DERMATOLOGY("피부과", listOf("14"), listOf("피부과")),
    REHABILITATION("정형외과", listOf("21"), listOf("정형", "재활", "물리치료")),
    DENTISTRY("치과", listOf("27", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61"), listOf("치과")),
    ORIENTAL_MEDICINE("한의원", listOf("28", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "90"), listOf("한의원", "한방")),
    OTHER_SPECIALTIES("기타", listOf("09","10","15", "24", "25", "26", "31", "40", "42", "43", "44"), listOf("비뇨", "성형"));

    companion object {
        fun getCategory(code: String): DepartmentCategory {
            return values().find { it.codes.contains(code) } ?: OTHER_SPECIALTIES
        }

        fun getCategoryByKeyword(keyword: String): DepartmentCategory {
            return values().find { category ->
                category.keywords.any { keyword.contains(it, ignoreCase = true) }
            } ?: OTHER_SPECIALTIES
        }
    }
}

enum class OperationState {
    OPEN,           // 영업 중
    CLOSED,         // 영업 종료
    LUNCH_BREAK,    // 점심시간
    EMERGENCY,      // 응급실 운영 중
    UNKNOWN         // 상태 알 수 없음
}

data class TimeRange(
    val start: LocalTime?,
    val end: LocalTime?
)

data class HospitalTimeInfo(
    val weekdayTime: TimeRange?,
    val saturdayTime: TimeRange?,
    val sundayTime: TimeRange?,
    val lunchTime: TimeRange?,
    val saturdayLunchTime: TimeRange?,
    val isEmergencyDay: Boolean,
    val isEmergencyNight: Boolean,
    val emergencyDayContact: String?,
    val emergencyNightContact: String?,
    val isClosed: Boolean = false
) {
    fun getCurrentState(currentTime: LocalTime = LocalTime.now(),
                        currentDay: LocalDate = LocalDate.now()): OperationState {
        if (isClosed) return OperationState.CLOSED
        if (isEmergencyDay || isEmergencyNight) return OperationState.EMERGENCY

        val timeRange = when (currentDay.dayOfWeek) {
            DayOfWeek.SUNDAY -> sundayTime
            DayOfWeek.SATURDAY -> saturdayTime
            else -> weekdayTime
        }

        return when {
            timeRange?.start == null || timeRange.end == null -> OperationState.UNKNOWN
            currentTime.isAfter(timeRange.start) && currentTime.isBefore(timeRange.end) -> {
                // 점심시간 체크
                val lunchTimeRange = if (currentDay.dayOfWeek == DayOfWeek.SATURDAY) {
                    saturdayLunchTime
                } else {
                    lunchTime
                }

                if (lunchTimeRange?.let { lunch ->
                        lunch.start?.let { start ->
                            lunch.end?.let { end ->
                                currentTime.isAfter(start) && currentTime.isBefore(end)
                            }
                        }
                    } == true) {
                    OperationState.LUNCH_BREAK
                } else {
                    OperationState.OPEN
                }
            }
            else -> OperationState.CLOSED
        }
    }

    companion object {
        fun parseTime(timeStr: String?): LocalTime? {
            if (timeStr.isNullOrBlank()) return null
            return try {
                val formatter = DateTimeFormatter.ofPattern("HHmm", Locale.getDefault())
                LocalTime.parse(timeStr, formatter)
            } catch (e: Exception) {
                null
            }
        }

        fun parseLunchTime(lunchTimeStr: String?): TimeRange? {
            if (lunchTimeStr.isNullOrBlank()) return null
            val times = lunchTimeStr.split("-")
            return if (times.size == 2) {
                TimeRange(
                    parseTime(times[0].trim()),
                    parseTime(times[1].trim())
                )
            } else {
                null
            }
        }
    }
}

// HospitalInfo 데이터 클래스 수정
data class HospitalInfo(
    val location: LatLng,
    val name: String,
    val address: String,
    val departments: List<String>,
    val departmentCategories: List<String>,
    val phoneNumber: String,
    val state: String,        // 현재 운영 상태를 나타내는 텍스트
    val rating: Double,
    val latitude: Double,
    val longitude: Double,
    val nonPaymentItems: List<NonPaymentItem>,
    val clCdNm: String,
    val ykiho: String,
    val timeInfo: HospitalTimeInfo? = null
) {
    val operationState: OperationState
        get() = timeInfo?.getCurrentState() ?: OperationState.UNKNOWN
}


// API 응답을 통합 모델로 변환하는 확장 함수
fun HospitalInfoItem.toHospitalInfo(nonPaymentItems: List<NonPaymentItem>, timeInfo: HospitalTimeInfo? = null): HospitalInfo {
    val departmentCodes = this.dgsbjtCd?.split(",")?.map { it.trim() } ?: emptyList()
    val departmentCategories = departmentCodes.mapNotNull { code ->
        DepartmentCategory.values().find { it.codes.contains(code) }
    }.distinct()

    val departments = inferDepartments(this.yadmNm ?: "", nonPaymentItems, departmentCodes)
    val departmentCategoryNames = departmentCategories.map { it.name }

    // 임시로 현재 운영 상태 확인
    val currentState = timeInfo?.getCurrentState() ?: OperationState.UNKNOWN
    val operationStateText = when (currentState) {
        OperationState.OPEN -> "영업중"
        OperationState.CLOSED -> "영업마감"
        OperationState.LUNCH_BREAK -> "점심시간"
        OperationState.EMERGENCY -> "응급실 운영중"
        OperationState.UNKNOWN -> "운영시간 정보없음"
    }

    return HospitalInfo(
        location = LatLng(this.YPos?.toDoubleOrNull() ?: 0.0, this.XPos?.toDoubleOrNull() ?: 0.0),
        name = this.yadmNm ?: "",
        address = "${this.sidoCdNm ?: ""} ${this.sgguCdNm ?: ""} ${this.emdongNm ?: ""}".trim(),
        departments = departments,
        departmentCategories = departmentCategoryNames,
        phoneNumber = this.telno ?: "",
        state = operationStateText,  // 운영 상태 텍스트로 설정
        rating = 0.0,
        latitude = this.YPos?.toDoubleOrNull() ?: 0.0,
        longitude = this.XPos?.toDoubleOrNull() ?: 0.0,
        nonPaymentItems = nonPaymentItems,
        clCdNm = this.clCdNm ?: "",
        ykiho = this.ykiho ?: "",
        timeInfo = timeInfo
    )
}

fun inferDepartments(hospitalName: String, nonPaymentItems: List<NonPaymentItem>, departmentCodes: List<String>): List<String> {
    val departments = mutableSetOf<String>()

    // 병원 이름에서 과 추론
    DepartmentCategory.values().forEach { category ->
        if (category.keywords.any { hospitalName.contains(it, ignoreCase = true) }) {
            departments.add(category.categoryName)
        }
    }

    // 비급여 항목에서 추가 진료과목 추론
    nonPaymentItems.forEach { item ->
        item.itemNm?.let { itemName ->
            departments.add(DepartmentCategory.getCategoryByKeyword(itemName).categoryName)
        }
    }

    // dgsbjtCd로 진료과 추론
    departmentCodes.forEach { code ->
        departments.add(DepartmentCategory.getCategory(code).categoryName)
    }

    return departments.toList()
}