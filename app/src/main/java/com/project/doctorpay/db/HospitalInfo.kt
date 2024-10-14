package com.project.doctorpay.db

import HospitalInfoItem
import NonPaymentItem
import android.os.Parcelable
import com.naver.maps.geometry.LatLng
import kotlinx.parcelize.Parcelize
import com.project.doctorpay.R

enum class DepartmentCategory(val categoryName: String, val codes: List<String>) {
    GENERAL_MEDICINE(R.string.GENERAL_MEDICINE.toString(), listOf("00", "23", "41")),
    INTERNAL_MEDICINE(R.string.INTERNAL_MEDICINE.toString(), listOf("01", "20")),
    SURGERY(R.string.SURGERY.toString(), listOf("04", "05", "06", "07", "08")),
    PEDIATRICS_OBSTETRICS(R.string.PEDIATRICS_OBSTETRICS.toString(), listOf("10", "11")),
    MENTAL_NEUROLOGY(R.string.MENTAL_NEUROLOGY.toString(), listOf("02", "03")),
    SENSORY_ORGANS(R.string.SENSORY_ORGANS.toString(), listOf("12", "13")),
    DERMATOLOGY_UROLOGY(R.string.DERMATOLOGY_UROLOGY.toString(), listOf("14", "15")),
    REHABILITATION(R.string.REHABILITATION.toString(), listOf("21")),
    DIAGNOSTICS(R.string.DIAGNOSTICS.toString(), listOf("16", "17", "18", "19", "22")),
    DENTISTRY(R.string.DENTISTRY.toString(), listOf("27", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61")),
    ORIENTAL_MEDICINE(R.string.ORIENTAL_MEDICINE.toString(), listOf("28", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "90")),
    OTHER_SPECIALTIES(R.string.OTHER_SPECIALTIES.toString(), listOf("09", "24", "25", "26", "31", "40", "42", "43", "44"));

    companion object {
        fun getCategory(code: String): DepartmentCategory {
            return values().find { it.codes.contains(code) } ?: OTHER_SPECIALTIES
        }
    }
}

@Parcelize
data class HospitalInfo(
    val location: LatLng,
    val name: String,
    val address: String,
    val department: String,
    val departmentCategory: String,
    val time: String,
    val phoneNumber: String,
    val state: String,
    val rating: Double,
    val latitude: Double,
    val longitude: Double,
    val nonPaymentItems: List<NonPaymentItem>,
    val clCdNm: String  // 병원 종류 (예: 종합병원, 병원, 의원 등)
) : Parcelable

// API 응답을 통합 모델로 변환하는 확장 함수
fun HospitalInfoItem.toHospitalInfo(nonPaymentItems: List<NonPaymentItem>): HospitalInfo {
    val departmentCodes = this.dgsbjtCd?.split(",")?.map { it.trim() } ?: emptyList()
    val departmentCategories = departmentCodes.mapNotNull { code ->
        DepartmentCategory.values().find { it.codes.contains(code) }
    }.distinct()

    val departmentCategory = if (departmentCategories.isNotEmpty()) {
        departmentCategories.first().name
    } else {
        DepartmentCategory.OTHER_SPECIALTIES.name
    }

    return HospitalInfo(
        location = LatLng(this.YPos?.toDoubleOrNull() ?: 0.0, this.XPos?.toDoubleOrNull() ?: 0.0),
        name = this.yadmNm ?: "",
        address = "${this.sidoCdNm ?: ""} ${this.sgguCdNm ?: ""} ${this.emdongNm ?: ""}".trim(),
        department = inferDepartments(this, nonPaymentItems),
        departmentCategory = departmentCategory,
        time = "",
        phoneNumber = this.telno ?: "",
        state = "",
        rating = 0.0,
        latitude = this.YPos?.toDoubleOrNull() ?: 0.0,
        longitude = this.XPos?.toDoubleOrNull() ?: 0.0,
        nonPaymentItems = nonPaymentItems,
        clCdNm = this.clCdNm ?: ""
    )
}


fun inferDepartments(hospitalInfo: HospitalInfoItem, nonPaymentItems: List<NonPaymentItem>): String {
    val departments = mutableSetOf<String>()

    when (hospitalInfo.clCdNm) {
        "종합병원" -> departments.addAll(listOf("내과", "외과", "소아과", "산부인과"))
        "병원" -> departments.add("일반의")
        else -> departments.add("기타")
    }

    // 비급여 항목에서 추가 진료과목 추론
    nonPaymentItems.forEach { item ->
        when {
            item.itemNm?.contains("치과") == true -> departments.add("치과")
            item.itemNm?.contains("안과") == true -> departments.add("안과")
            item.itemNm?.contains("이비인후과") == true -> departments.add("이비인후과")
            item.itemNm?.contains("피부과") == true -> departments.add("피부과")
            item.itemNm?.contains("정형외과") == true -> departments.add("정형외과")
            item.itemNm?.contains("산부인과") == true -> departments.add("산부인과")
        }
    }

    return departments.joinToString(", ")
}


private fun getDepartmentName(code: String): String? {
    return when (code) {
        "00" -> "일반의"
        "01" -> "내과"
        "02" -> "신경과"
        "03" -> "정신건강의학과"
        "04" -> "외과"
        "05" -> "정형외과"
        "06" -> "신경외과"
        "07" -> "심장혈관흉부외과"
        "08" -> "성형외과"
        "09" -> "마취통증의학과"
        "10" -> "산부인과"
        "11" -> "소아청소년과"
        "12" -> "안과"
        "13" -> "이비인후과"
        "14" -> "피부과"
        "15" -> "비뇨의학과"
        "16" -> "영상의학과"
        "17" -> "방사선종양학과"
        "18" -> "병리과"
        "19" -> "진단검사의학과"
        "20" -> "결핵과"
        "21" -> "재활의학과"
        "22" -> "핵의학과"
        "23" -> "가정의학과"
        "24" -> "응급의학과"
        "25" -> "직업환경의학과"
        "26" -> "예방의학과"
        "49" -> "치과"
        "80" -> "한방내과"
        else -> null
    }
}