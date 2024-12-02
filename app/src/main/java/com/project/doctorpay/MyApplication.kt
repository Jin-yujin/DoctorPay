package com.project.doctorpay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.kakao.sdk.common.KakaoSdk
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.initialize
import com.google.firebase.messaging.FirebaseMessaging

class MyApplication : Application() {
    companion object {
        const val CHANNEL_ID = "appointment_notification_channel"

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

        // 알림 채널 생성
        createNotificationChannel()

        // FCM 토큰 가져오기
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // 새 토큰 얻기
            val token = task.result
            Log.d("FCM", "FCM Token: $token")
        }

        // 앱 초기화 시 필요한 설정들
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "예약 알림"
            val descriptionText = "병원 예약 알림을 표시합니다"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}