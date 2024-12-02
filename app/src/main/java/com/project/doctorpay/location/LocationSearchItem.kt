package com.project.doctorpay.location


data class LocationSearchItem(
    val title: String,
    val address: String,
    val roadAddress: String?,
    val latitude: Double,
    val longitude: Double
)