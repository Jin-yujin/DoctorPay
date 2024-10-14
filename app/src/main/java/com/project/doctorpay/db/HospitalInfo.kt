package com.project.doctorpay.db

import HospitalInfoItem
import NonPaymentItem
import android.os.Parcelable
import com.naver.maps.geometry.LatLng
import kotlinx.parcelize.Parcelize

@Parcelize
data class HospitalInfo(
    val location: LatLng, //현 위치와 병원 사이 거리  나중에 추가 예정
    val name: String, // 병원이름
    val address: String, //병원 주소 ex) 서울 중랑구 ㅇㅇ동 ~
    val department: String, //진료과목 ex)내과, 외과 ...
    val time: String, //병원 운영 시간
    val phoneNumber: String, //병원 전화번호
    val state: String, // 병원 영업 여부 - 현재 시간과 운영시간 비교 예정
    val rating: Double, //별점 - 이후 추가예정
    val latitude: Double,
    val longitude: Double,
    val nonPaymentItems: List<NonPaymentItem>,
    val clCdNm: String  // 병원 종류 (예: 종합병원, 병원, 의원 등)
) : Parcelable


// API 응답을 통합 모델로 변환하는 확장 함수
fun HospitalInfoItem.toHospitalInfo(nonPaymentItems: List<NonPaymentItem>): HospitalInfo {
    return HospitalInfo(
        location = LatLng(this.YPos?.toDoubleOrNull() ?: 0.0, this.XPos?.toDoubleOrNull() ?: 0.0),
        name = this.yadmNm ?: "",
        address = "${this.sidoCdNm ?: ""} ${this.sgguCdNm ?: ""} ${this.emdongNm ?: ""}".trim(),
        department = inferDepartments(this, nonPaymentItems),
        time = "",  // API에서 제공되지 않는 정보
        phoneNumber = this.telno ?: "",
        state = "",  // API에서 제공되지 않는 정보. 필요하다면 별도로 처리 필요
        rating = 0.0,  // API에서 제공되지 않는 정보. 필요하다면 별도로 처리 필요
        latitude = this.YPos?.toDoubleOrNull() ?: 0.0,
        longitude = this.XPos?.toDoubleOrNull() ?: 0.0,
        nonPaymentItems = nonPaymentItems,
        clCdNm = this.clCdNm ?: ""
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