package com.project.doctorpay.DB

import HospitalInfoItem
import NonPaymentItem
import com.naver.maps.geometry.LatLng

data class HospitalInfo(
    val location: LatLng,
    val name: String,
    val address: String,
    val department: String,
    val time: String,
    val phoneNumber: String,
    val state: String,
    val rating: Double,
    val latitude: Double,
    val longitude: Double,
    val nonPaymentItems: List<NonPaymentItem>
)

// API 응답을 통합 모델로 변환하는 확장 함수
fun HospitalInfoItem.toHospitalInfo(nonPaymentItems: List<NonPaymentItem>): HospitalInfo {
    return HospitalInfo(
        location = LatLng(this.YPos?.toDoubleOrNull() ?: 0.0, this.XPos?.toDoubleOrNull() ?: 0.0),
        name = this.yadmNm ?: "",
        address = "${this.sidoCdNm ?: ""} ${this.sgguCdNm ?: ""} ${this.emdongNm ?: ""}".trim(),
        department = inferDepartments(this, nonPaymentItems),
        time = "",  // API에서 제공되지 않는 정보
        phoneNumber = this.telno ?: "",
        state = "",  // API에서 제공되지 않는 정보
        rating = 0.0,  // API에서 제공되지 않는 정보
        latitude = this.YPos?.toDoubleOrNull() ?: 0.0,
        longitude = this.XPos?.toDoubleOrNull() ?: 0.0,
        nonPaymentItems = nonPaymentItems
    )
}

fun inferDepartments(hospitalInfo: HospitalInfoItem, nonPaymentItems: List<NonPaymentItem>): String {
    val departments = mutableSetOf<String>()

    // dgsbjtCd를 파싱하여 진료과목 추가
    hospitalInfo.dgsbjtCd?.split(",")?.forEach { code ->
        val department = getDepartmentName(code.trim())
        if (department != null) {
            departments.add(department)
        }
    }

    // 병원 유형에 따른 기본 진료과목 추론 (필요한 경우)
    when (hospitalInfo.clCdNm) {
        "종합병원" -> {
            if (departments.isEmpty()) {
                departments.addAll(listOf("내과", "외과", "소아과", "산부인과"))
            }
        }
        "병원", "의원" -> {
            if (departments.isEmpty()) {
                departments.add("일반의")
            }
        }
    }

    // 비급여 항목에서 추가 진료과목 추론
    nonPaymentItems.forEach { item ->
        when {
            item.itemNm?.contains("치과") == true -> departments.add("치과")
            item.itemNm?.contains("안과") == true -> departments.add("안과")
            item.itemNm?.contains("이비인후과") == true -> departments.add("이비인후과")
        }
    }

    return departments.joinToString(", ")
}

private fun getDepartmentName(code: String): String? {
    return when (code) {
        "01" -> "내과"
        "02" -> "외과"
        "03" -> "정형외과"
        "04" -> "신경외과"
        "05" -> "안과"
        "06" -> "이비인후과"
        // 추가 코드와 해당하는 진료과목 이름
        else -> null
    }
}