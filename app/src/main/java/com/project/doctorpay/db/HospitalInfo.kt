package com.project.doctorpay.db

import HospitalInfoItem
import NonPaymentItem
import android.os.Parcelable
import com.naver.maps.geometry.LatLng
import kotlinx.parcelize.Parcelize
import com.project.doctorpay.R

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


@Parcelize
data class HospitalInfo(
    val location: LatLng,
    val name: String,
    val address: String,
    val departments: List<String>,
    val departmentCategories: List<String>,
    val time: String,
    val phoneNumber: String,
    val state: String,
    val rating: Double,
    val latitude: Double = 0.0, // 위도
    val longitude: Double = 0.0, // 경도
    val nonPaymentItems: List<NonPaymentItem>,
    val clCdNm: String,  // 병원 종류 (예: 종합병원, 병원, 의원 등)
    val ykiho: String,
) : Parcelable


// API 응답을 통합 모델로 변환하는 확장 함수
fun HospitalInfoItem.toHospitalInfo(nonPaymentItems: List<NonPaymentItem>): HospitalInfo {
    val departmentCodes = this.dgsbjtCd?.split(",")?.map { it.trim() } ?: emptyList()
    val departmentCategories = departmentCodes.mapNotNull { code ->
        DepartmentCategory.values().find { it.codes.contains(code) }
    }.distinct()

    val departments = inferDepartments(this.yadmNm ?: "", nonPaymentItems, departmentCodes)
    val departmentCategoryNames = departmentCategories.map { it.name }

    return HospitalInfo(
        location = LatLng(this.YPos?.toDoubleOrNull() ?: 0.0, this.XPos?.toDoubleOrNull() ?: 0.0),
        name = this.yadmNm ?: "",
        address = "${this.sidoCdNm ?: ""} ${this.sgguCdNm ?: ""} ${this.emdongNm ?: ""}".trim(),
        departments = departments,
        departmentCategories = departmentCategoryNames,
        time = "영업 시간 준비중",
        phoneNumber = this.telno ?: "",
        state = "",
        rating = 0.0,
        latitude = this.YPos?.toDoubleOrNull() ?: 0.0,
        longitude = this.XPos?.toDoubleOrNull() ?: 0.0,
        nonPaymentItems = nonPaymentItems,
        clCdNm = this.clCdNm ?: "",
        ykiho = this.ykiho ?: ""
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