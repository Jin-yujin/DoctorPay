package com.project.doctorpay.db


data class FavoriteHospital(
    val hospitalID: String = "",        // ykiho, 병원 고유 ID (API에서 제공하는 ID 사용)
    val userId: String = "",       // Firebase 사용자 ID
    val timestamp: Long = System.currentTimeMillis()  // 즐겨찾기 추가 시간
)
