package com.project.doctorpay.DB

import com.naver.maps.geometry.LatLng

data class HospitalInfo(
    val location: LatLng, //병원 위치
    val name: String, //병원 이름
    val address: String, //주소
    val department: String, //진료 과목
    val time: String, //영업 시간
    val phoneNumber: String, //병원 번호
    val state: String, //영업 중 여부
    val rating: Double// 별점
)