package com.project.doctorpay.db


data class FavoriteHospital(
    val hospitalID: String = "",        // ykiho
    val userId: String = "",            // Firebase 사용자 ID
    val timestamp: Long = System.currentTimeMillis(),
    // HospitalInfo의 기본 정보들
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phoneNumber: String = "",
    val clCdNm: String = "",           // 병원 종류
    val departments: List<String> = emptyList(),
    val departmentCategories: List<String> = emptyList()
)