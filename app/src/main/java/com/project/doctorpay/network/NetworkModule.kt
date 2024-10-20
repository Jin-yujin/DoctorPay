package com.project.doctorpay.network

import android.util.Log
import com.project.doctorpay.api.HealthInsuranceApi
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "http://apis.data.go.kr/B551182/"
    private const val SERVICE_KEY = "rctk2eXwpdEoBK9zhzZpm%2BlyA9%2BAJByBI8T8SlgPgIWlhrwsQu%2B1Ayx7UBIvZd5oLNsccSTf5Hw2OY6dW3lo5A%3D%3D"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val urlLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        if (url.contains("MadmDtlInfoService2.7")) {
            Log.d("DgsbjtInfo_API_CALL", "URL: $url")
        } else {
            Log.d("API_CALL", "URL: $url")
        }
        chain.proceed(request)
    }

    private val serviceKeyInterceptor = Interceptor { chain ->
        val original = chain.request()
        val originalHttpUrl = original.url

        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("serviceKey", SERVICE_KEY)
            .build()

        val requestBuilder = original.newBuilder()
            .url(url)

        val request = requestBuilder.build()
        chain.proceed(request)
    }



    private val responseLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.toString().contains("MadmDtlInfoService2.7")) {
            Log.d("DgsbjtInfo_API_RESPONSE", "Response Code: ${response.code}")
            Log.d("DgsbjtInfo_API_RESPONSE", "Response Body: ${response.peekBody(Long.MAX_VALUE).string()}")
        }

        response
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Connection", "close")
            val request = requestBuilder.build()
            chain.proceed(request)
        }
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(serviceKeyInterceptor)
        .addInterceptor(urlLoggingInterceptor)
        .addInterceptor(responseLoggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()

    val healthInsuranceApi: HealthInsuranceApi = retrofit.create(HealthInsuranceApi::class.java)

    fun getServiceKey(): String = SERVICE_KEY

}