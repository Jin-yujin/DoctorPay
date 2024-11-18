package com.project.doctorpay.ui.reviews

data class ReviewFilter(
    var department: String = "전체",
    var ratingRange: IntRange = 0..5
)