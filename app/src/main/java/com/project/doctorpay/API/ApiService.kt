package com.project.doctorpay.api

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("getNonPaymentItemHospList2")
    suspend fun getNonPaymentItemHospList(
        @Query("ServiceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
        @Query("yadmNm") hospitalName: String? = null
    ): NonPaymentResponse
}