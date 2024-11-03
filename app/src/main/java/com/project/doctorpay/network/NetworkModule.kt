package com.project.doctorpay.network

import android.util.Log
import com.project.doctorpay.api.HealthInsuranceApi
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "http://apis.data.go.kr/B551182/"
    private const val SERVICE_KEY = "rctk2eXwpdEoBK9zhzZpm%2BlyA9%2BAJByBI8T8SlgPgIWlhrwsQu%2B1Ayx7UBIvZd5oLNsccSTf5Hw2OY6dW3lo5A%3D%3D"
    private const val TIMEOUT_SECONDS = 30L
    private const val MAX_RETRIES = 3

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val urlLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        Log.d("API_CALL", "URL: $url")
        chain.proceed(request)
    }

    private val serviceKeyInterceptor = Interceptor { chain ->
        val original = chain.request()
        val originalUrl = original.url

        // serviceKey가 이미 있는지 확인
        if (originalUrl.queryParameter("serviceKey") != null) {
            return@Interceptor chain.proceed(original)
        }

        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("serviceKey", SERVICE_KEY)
            .build()

        val newRequest = original.newBuilder()
            .url(newUrl)
            .build()

        chain.proceed(newRequest)
    }

    private val timeoutInterceptor = Interceptor { chain ->
        val request = chain.request()

        try {
            // 최초 시도
            chain.proceed(request)
        } catch (e: IOException) {
            // 실패시 재시도
            var lastException = e
            for (attempt in 1..MAX_RETRIES) {
                try {
                    Log.d("NetworkModule", "Retrying request (attempt $attempt)")
                    return@Interceptor chain.proceed(request)
                } catch (e: IOException) {
                    lastException = e
                    if (attempt == MAX_RETRIES) {
                        throw lastException
                    }
                    // 지수 백오프
                    Thread.sleep(1000L * attempt)
                }
            }
            throw lastException
        }
    }

    private val okHttpClient = OkHttpClient.Builder().apply {
        addInterceptor(loggingInterceptor)
        addInterceptor(urlLoggingInterceptor)
        addInterceptor(serviceKeyInterceptor)
        addInterceptor(timeoutInterceptor)
        connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        retryOnConnectionFailure(true)
        protocols(listOf(Protocol.HTTP_1_1))

        // Keep-Alive 비활성화
        addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Connection", "close")
                .build()
            chain.proceed(request)
        }
    }.build()

    // API 요청당 새로운 OkHttpClient 인스턴스 생성
    fun createNewClient(): OkHttpClient {
        return okHttpClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(SimpleXmlConverterFactory.createNonStrict())
        .client(okHttpClient)
        .build()

    val healthInsuranceApi: HealthInsuranceApi = retrofit.create(HealthInsuranceApi::class.java)

    fun getServiceKey(): String = URLDecoder.decode(SERVICE_KEY, "UTF-8")
}