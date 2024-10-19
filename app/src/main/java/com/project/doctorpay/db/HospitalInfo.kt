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
    OTOLARYNGOLOGY(R.string.OTOLARYNGOLOGY.toString(), listOf("12")),
    OPHTHALMOLOGY(R.string.OPHTHALMOLOGY.toString(), listOf("13")),
    DERMATOLOGY(R.string.DERMATOLOGY.toString(), listOf("14")),
    REHABILITATION(R.string.REHABILITATION.toString(), listOf("21")),
    DENTISTRY(R.string.DENTISTRY.toString(), listOf("27", "49", "50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61")),
    ORIENTAL_MEDICINE(R.string.ORIENTAL_MEDICINE.toString(), listOf("28", "80", "81", "82", "83", "84", "85", "86", "87", "88", "89", "90")),
    OTHER_SPECIALTIES(R.string.OTHER_SPECIALTIES.toString(), listOf("09","15", "24", "25", "26", "31", "40", "42", "43", "44"));

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
    val clCdNm: String,  // 병원 종류 (예: 종합병원, 병원, 의원 등)
    val ykiho: String
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
        department = inferDepartments(this.yadmNm ?: "", nonPaymentItems, this.dgsbjtCd ?: ""),
        departmentCategory = departmentCategory,
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

// inferDepartments 함수 추가
fun inferDepartments(hospitalName: String, nonPaymentItems: List<NonPaymentItem>, dgsbjtCodes: String): String {
    val departments = mutableSetOf<String>()

    // 병원 이름에서 과 추론
    when {
        hospitalName.contains("내과") -> departments.add("내과")
        hospitalName.contains("외과") && !hospitalName.contains("흉부") -> departments.add("외과")
        hospitalName.contains("이비인후과") -> departments.add("이비인후과")
        hospitalName.contains("안과") -> departments.add("안과")
        hospitalName.contains("정신") || hospitalName.contains("신경") -> departments.add("신경과")
        hospitalName.contains("소아") -> departments.add("소아과")
        hospitalName.contains("산부인과") || hospitalName.contains("여성") -> departments.add("산부인과")
        hospitalName.contains("피부") -> departments.add("피부과")
        hospitalName.contains("재활") || hospitalName.contains("물리치료") || hospitalName.contains("정형") -> departments.add("정형외과")
        hospitalName.contains("한방") || hospitalName.contains("한의원") -> departments.add("한의원")
        hospitalName.contains("치과") -> departments.add("치과")
        hospitalName.contains("비뇨") -> departments.add("비뇨기과")
        hospitalName.contains("성형") -> departments.add("성형외과")
        hospitalName.contains("흉부") -> departments.add("흉부외과")
        else -> departments.add("기타 일반과")
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

    // dgsbjtCd로 진료과 추론
    dgsbjtCodes.split(",").forEach { code ->
        val departmentCategory = DepartmentCategory.getCategory(code)
        departments.add(departmentCategory.categoryName)
    }

    return departments.joinToString(", ")
}