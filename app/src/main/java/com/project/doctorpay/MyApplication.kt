package com.project.doctorpay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.kakao.sdk.common.KakaoSdk
import com.google.firebase.Firebase
import com.google.firebase.initialize

class MyApplication : Application() {
    companion object {
        private lateinit var instance: MyApplication

        fun getInstance(): MyApplication {
            return instance
        }

        fun showToast(message: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(instance, message, Toast.LENGTH_SHORT).apply {
                    setGravity(Gravity.CENTER, 0, 0)
                }.show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Firebase.initialize(this)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // 앱 초기화 시 필요한 설정들
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "default",
            "기본 채널",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}