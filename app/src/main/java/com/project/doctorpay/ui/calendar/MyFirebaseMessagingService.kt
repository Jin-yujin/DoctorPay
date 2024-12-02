package com.project.doctorpay.ui.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.google.firebase.firestore.FirebaseFirestore

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // 새로운 토큰이 생성될 때마다 Firestore에 저장
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { uid ->
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(uid)
                .update("fcmToken", token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // 알림 데이터 처리
        remoteMessage.data.let { data ->
            val title = data["title"] ?: "병원 예약 알림"
            val message = data["message"] ?: "예약된 일정이 있습니다."
            val appointmentId = data["appointmentId"]

            sendNotification(title, message, appointmentId)
        }
    }

    private fun sendNotification(title: String, message: String, appointmentId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            appointmentId?.let { id ->
                putExtra("appointment_id", id)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "appointment_notification_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 안드로이드 Oreo 이상에서는 채널 생성이 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "예약 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "병원 예약 알림을 표시합니다"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}