package com.project.doctorpay.db

data class UserProfile(
    val email: String,
    var nickname: String = "",
    var age: String = "",
    var gender: String = "",
    var region: String = ""
)