package com.project.doctorpay.api

import HospitalInfoResponse
import NonPaymentResponse
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface HealthInsuranceApi {
    @GET("nonPaymentDamtInfoService/getNonPaymentItemHospList")
    suspend fun getNonPaymentInfo(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
        @Query("itemCd") itemCd: String? = null,
        @Query("yadmNm") yadmNm: String? = null,
        @Query("clCd") clCd: String? = null,
        @Query("sidoCd") sidoCd: String? = null,
        @Query("sgguCd") sgguCd: String? = null
    ): NonPaymentResponse

    @GET("hospInfoService/getHospBasisList")
    suspend fun getHospitalInfo(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
        @Query("yadmNm") yadmNm: String? = null,
        @Query("sidoCd") sidoCd: String? = null,
        @Query("sgguCd") sgguCd: String? = null,
        @Query("emdongNm") emdongNm: String? = null,
        @Query("ykiho") ykiho: String? = null,
        @Query("clCd") clCd: String? = null
    ): HospitalInfoResponse
}

val retrofit = Retrofit.Builder()
    .baseUrl("http://apis.data.go.kr/B551182/")
    .addConverterFactory(SimpleXmlConverterFactory.create())
    .build()

val healthInsuranceApi = retrofit.create(HealthInsuranceApi::class.java)