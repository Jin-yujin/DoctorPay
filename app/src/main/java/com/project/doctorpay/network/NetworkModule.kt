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
    private const val SERVICE_KEY = "0H0upZmR4M4DyfwLLid%2F7qyTNc%2BVxA0cg0mMk9zOU6V4zdapEmdXA10%2Fz69RvH4ey70OMYofiJ%2FEtqZlT3JC0w%3D%3D"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val urlLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d("API_CALL", "URL: ${request.url}")
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

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(urlLoggingInterceptor)
        .addInterceptor(serviceKeyInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(SimpleXmlConverterFactory.createNonStrict())
        .client(client)
        .build()

    val healthInsuranceApi: HealthInsuranceApi = retrofit.create(HealthInsuranceApi::class.java)

    // 디코딩된 서비스 키를 반환하는 함수
    fun getDecodedServiceKey(): String {
        return URLDecoder.decode(SERVICE_KEY, "UTF-8")
    }
}