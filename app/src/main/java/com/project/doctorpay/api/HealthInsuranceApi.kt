package com.project.doctorpay.api

import DgsbjtInfoResponse
import HospitalInfoResponse
import NonPaymentResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

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
    ): Response<NonPaymentResponse>

    @GET("hospInfoServicev2/getHospBasisList")
    suspend fun getHospitalInfo(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
        @Query("sidoCd") sidoCd: String? = null,
        @Query("sgguCd") sgguCd: String? = null,
        @Query("emdongNm") emdongNm: String? = null,
        @Query("yadmNm") yadmNm: String? = null,
        @Query("zipCd") zipCd: String? = null,
        @Query("clCd") clCd: String? = null,
        @Query("dgsbjtCd") dgsbjtCd: String? = null,
        @Query("xPos") xPos: String? = null,
        @Query("yPos") yPos: String? = null,
        @Query("radius") radius: Int? = null,
        @Query("ykiho") ykiho: String? = null
    ):  Response<HospitalInfoResponse>



    @GET("MadmDtlInfoService2.7/getDgsbjtInfo2.7")
    suspend fun getDgsbjtInfo(
        @Query("serviceKey") serviceKey: String,
        @Query("ykiho ") ykiho: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
        @Query("_type") type: String? = null,
        @Query("dgsbjtCd") dgsbjtCd: String? = null,
        @Query("dgsbjtCdNm") dgsbjtCdNm: String? = null,
        @Query("dgsbjtPrSdrCnt") dgsbjtPrSdrCnt: String? = null,
        @Query("cdiagDrCnt") cdiagDrCnt: String? = null
    ):  Response<DgsbjtInfoResponse>
}
