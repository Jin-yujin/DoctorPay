package com.project.doctorpay.db

import HospitalInfoItem
import NonPaymentItem
import android.os.Parcelable
import com.naver.maps.geometry.LatLng
import kotlinx.parcelize.Parcelize
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// 기존 enum 클래스들과 data 클래스들은 그대로 유지
enum class DepartmentCategory(val categoryName: String, val codes: List<String>, val keywords: List<String>) {
    GENERAL_MEDICINE("일반의",
        listOf("00", "23", "41"), // 일반의, 가정의학과, 보건
        listOf("일반의", "가정의학과")
    ),

    INTERNAL_MEDICINE("내과",
        listOf("01", "20"), // 내과, 결핵과
        listOf("내과")
    ),

    SURGERY("외과",
        listOf("04", "05", "06", "07", "08"), // 외과, 정형외과, 신경외과, 흉부외과, 성형외과
        listOf("외과", "흉부외과")
    ),

    OBSTETRICS("산부인과",
        listOf("10"), // 산부인과
        listOf("산부인과", "여성")
    ),

    MENTAL_NEUROLOGY("정신/신경과",
        listOf("02", "03"), // 신경과, 정신건강의학과
        listOf("정신", "신경")
    ),

    OTOLARYNGOLOGY("이비인후과",
        listOf("13"), // 이비인후과
        listOf("이비인후과")
    ),

    OPHTHALMOLOGY("안과",
        listOf("12"), // 안과
        listOf("안과")
    ),

    DERMATOLOGY("피부과",
        listOf("14"), // 피부과
        listOf("피부과")
    ),

    REHABILITATION("정형외과",
        listOf("21"), // 재활의학과
        listOf("정형", "재활", "물리치료")
    ),

    DENTISTRY("치과",
        listOf("27", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61"),
        // 치과 관련 모든 코드
        listOf("치과")
    ),

    ORIENTAL_MEDICINE("한의원",
        listOf("28", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "90"),
        // 한의원 관련 모든 코드
        listOf("한의원", "한방")
    ),

    OTHER_SPECIALTIES("기타",
        listOf(
            "09", // 마취통증의학과
            "11", //소아 청소년과
            "15", // 비뇨의학과
            "16", // 영상의학과
            "17", // 방사선종양학과
            "18", // 병리과
            "19", // 진단검사의학과
            "22", // 핵의학과
            "24", // 응급의학과
            "25", // 직업환경의학과
            "26", // 예방의학과
            "31", // 기타2
            "40", // 기타2(2)
            "42", // 기타3
            "43", // 보건기관치과
            "44"  // 보건기관한방
        ),
        listOf("비뇨", "성형")
    );

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
    // timeInfo 필드 제거
) {
    fun getCurrentState(): OperationState {
        if (isClosed) return OperationState.CLOSED
        if (isEmergencyDay || isEmergencyNight) return OperationState.EMERGENCY

        val currentTime = LocalTime.now()
        val currentDay = LocalDate.now().dayOfWeek

        val timeRange = when (currentDay) {
            DayOfWeek.SUNDAY -> sundayTime
            DayOfWeek.SATURDAY -> saturdayTime
            else -> weekdayTime
        }

        return when {
            timeRange?.start == null || timeRange.end == null -> {
                if (currentDay == DayOfWeek.SUNDAY) {
                    OperationState.CLOSED  // 일요일이고 시간 정보가 없으면 휴무로 처리
                } else {
                    OperationState.UNKNOWN
                }
            }
            currentTime.isAfter(timeRange.start) && currentTime.isBefore(timeRange.end) -> {
                val lunchTimeRange = if (currentDay == DayOfWeek.SATURDAY) {
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
}

data class TimeRange(
    val start: LocalTime?,
    val end: LocalTime?
)

enum class OperationState {
    OPEN,           // 영업 중
    CLOSED,         // 영업 종료
    LUNCH_BREAK,    // 점심시간
    EMERGENCY,      // 응급실 운영 중
    UNKNOWN;        // 상태 알 수 없음

    fun toDisplayText(): String = when (this) {
        OPEN -> "영업중"
        CLOSED -> "영업마감"
        LUNCH_BREAK -> "점심시간"
        EMERGENCY -> "응급실 운영중"
        UNKNOWN -> "운영시간 정보없음"
    }
}

data class HospitalInfo(
    val location: LatLng,
    val name: String,
    val address: String,
    val departments: List<String>,
    val departmentCategories: List<String>,
    val phoneNumber: String,
    val state: String,
    val rating: Double,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nonPaymentItems: List<NonPaymentItem>,
    val clCdNm: String,
    val ykiho: String,
    val timeInfo: HospitalTimeInfo? = null
) {
    val operationState: OperationState
        get() = timeInfo?.getCurrentState() ?: OperationState.UNKNOWN

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HospitalInfo) return false
        return ykiho == other.ykiho
    }

    override fun hashCode(): Int {
        return ykiho.hashCode()
    }
}

// 데이터 매핑 관련 확장 함수들은 Repository로 이동
fun inferDepartments(hospitalName: String, nonPaymentItems: List<NonPaymentItem>, departmentCodes: List<String>): List<String> {
    val departments = mutableSetOf<String>()

    DepartmentCategory.values().forEach { category ->
        if (category.keywords.any { hospitalName.contains(it, ignoreCase = true) }) {
            departments.add(category.categoryName)
        }
    }

    nonPaymentItems.forEach { item ->
        item.itemNm?.let { itemName ->
            departments.add(DepartmentCategory.getCategoryByKeyword(itemName).categoryName)
        }
    }

    departmentCodes.forEach { code ->
        departments.add(DepartmentCategory.getCategory(code).categoryName)
    }

    return departments.toList()
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
