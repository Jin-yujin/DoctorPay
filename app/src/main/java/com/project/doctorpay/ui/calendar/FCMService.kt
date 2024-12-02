package com.project.doctorpay.ui.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.project.doctorpay.MainActivity
import com.project.doctorpay.MyApplication.Companion.CHANNEL_ID
import com.project.doctorpay.R

class FCMService : FirebaseMessagingService() {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FCMService"
    private val tag = "FCMService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "New FCM token: $token")

        // Firestore에 토큰 저장
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { uid ->
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(tag, "FCM token updated for user: $uid")
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "Error updating FCM token", e)
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "From: ${remoteMessage.from}")

        remoteMessage.data.let { data ->
            Log.d(tag, "Message data: $data")

            val title = data["title"] ?: "병원 예약 알림"
            val message = data["message"] ?: "예약된 일정이 있습니다."
            val appointmentId = data["appointmentId"]

            showNotification(title, message, appointmentId)
        }

        remoteMessage.notification?.let { notification ->
            Log.d(tag, "Notification: ${notification.title} / ${notification.body}")
            showNotification(
                notification.title ?: "병원 예약 알림",
                notification.body ?: "예약된 일정이 있습니다.",
                null
            )
        }
    }

    private fun showNotification(title: String, message: String, appointmentId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            appointmentId?.let { id ->
                putExtra("appointment_id", id)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = appointmentId?.hashCode() ?: System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun sendNotification(title: String, messageBody: String, appointmentId: String?) {
        val channelId = "hospital_appointment_channel"

        // 알림 클릭시 실행될 Intent 설정
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

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // 알림 생성
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 채널 생성 (Android 8.0 이상 필수)
        val channel = NotificationChannel(
            channelId,
            "병원 예약 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "병원 예약 관련 알림을 표시합니다"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // 알림 표시
        val notificationId = appointmentId?.hashCode() ?: 0
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}