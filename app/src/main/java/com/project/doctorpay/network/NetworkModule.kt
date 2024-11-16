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
    private const val TIMEOUT_SECONDS = 60L
    private const val MAX_RETRIES = 3
    private const val INITIAL_RETRY_DELAY = 1000L
    private const val MAX_PARALLEL_REQUESTS = 4

    private val dispatcher = Dispatcher().apply {
        maxRequests = MAX_PARALLEL_REQUESTS * 2
        maxRequestsPerHost = MAX_PARALLEL_REQUESTS
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val urlLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        Log.d("API_CALL", "URL: $url")
        chain.proceed(request)
    }

    private val retryInterceptor = Interceptor { chain ->
        var retryCount = 0
        var lastException: Exception? = null

        while (retryCount < MAX_RETRIES) {
            try {
                val request = chain.request()
                val response = chain.proceed(request)

                if (response.isSuccessful) {
                    return@Interceptor response
                } else {
                    response.close()
                    throw IOException("Response not successful: ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                retryCount++

                if (retryCount < MAX_RETRIES) {
                    val backoffDelay = INITIAL_RETRY_DELAY * (1 shl (retryCount - 1))
                    Log.d("RetryInterceptor", "Retrying request (attempt $retryCount)")
                    Thread.sleep(backoffDelay)
                }
            }
        }

        throw lastException ?: IOException("Max retries exceeded")
    }

    private val gzipInterceptor = Interceptor { chain ->
        val request = chain.request()
        val newRequest = request.newBuilder()
            .header("Accept-Encoding", "identity")  // gzip 비활성화
            .build()
        chain.proceed(newRequest)
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

    private val connectionPoolingInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Connection", "keep-alive")
            .build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder().apply {
        dispatcher(dispatcher)
        addInterceptor(loggingInterceptor)
        addInterceptor(retryInterceptor)
        addInterceptor(connectionPoolingInterceptor)
        connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        retryOnConnectionFailure(true)
        connectionPool(ConnectionPool(
            MAX_PARALLEL_REQUESTS,
            5, // keep-alive duration
            TimeUnit.MINUTES
        ))
    }.build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(SimpleXmlConverterFactory.createNonStrict())
        .client(okHttpClient)
        .build()

    val healthInsuranceApi: HealthInsuranceApi = retrofit.create(HealthInsuranceApi::class.java)

    fun getServiceKey(): String = URLDecoder.decode(SERVICE_KEY, "UTF-8")
}