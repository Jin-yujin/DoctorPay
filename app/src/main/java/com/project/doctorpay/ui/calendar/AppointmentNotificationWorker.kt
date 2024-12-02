package com.project.doctorpay.ui.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class AppointmentNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val tag = "AppointmentNotification"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun doWork(): Result {
        try {
            val hospitalName = inputData.getString(KEY_HOSPITAL_NAME) ?: return Result.failure()
            val appointmentTime = inputData.getString(KEY_APPOINTMENT_TIME) ?: return Result.failure()
            val appointmentId = inputData.getString(KEY_APPOINTMENT_ID) ?: return Result.failure()

            showNotification(hospitalName, appointmentTime, appointmentId)
            return Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Error in worker", e)
            return Result.failure()
        }
    }

    private fun showNotification(hospitalName: String, appointmentTime: String, appointmentId: String) {
        val channelId = "appointment_notification_channel"

        // 알림 채널 생성 (Android 8.0 이상)
        val channel = NotificationChannel(
            channelId,
            "예약 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "병원 예약 알림을 표시합니다"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // 알림 클릭시 실행될 Intent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("appointment_id", appointmentId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            appointmentId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 알림 생성
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("병원 예약 알림")
            .setContentText("오늘 ${hospitalName}에 ${appointmentTime} 예약이 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .build()

        // 알림 표시
        notificationManager.notify(appointmentId.hashCode(), notification)
    }

    companion object {
        private const val KEY_HOSPITAL_NAME = "hospital_name"
        private const val KEY_APPOINTMENT_TIME = "appointment_time"
        private const val KEY_APPOINTMENT_ID = "appointment_id"

        fun scheduleNotification(
            context: Context,
            appointment: Appointment,
            notifyHoursBefore: Long = 24
        ) {
            val appointmentDateTime = LocalDateTime.of(
                appointment.year,
                appointment.month + 1,
                appointment.day,
                appointment.time.split(":")[0].toInt(),
                appointment.time.split(":")[1].toInt()
            )

            val currentDateTime = LocalDateTime.now()
            val notificationDateTime = appointmentDateTime.minusHours(notifyHoursBefore)

            if (notificationDateTime.isBefore(currentDateTime)) {
                return
            }

            val delay = notificationDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                    System.currentTimeMillis()

            val inputData = workDataOf(
                KEY_HOSPITAL_NAME to appointment.hospitalName,
                KEY_APPOINTMENT_TIME to appointment.time,
                KEY_APPOINTMENT_ID to appointment.id
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val notificationWork = OneTimeWorkRequestBuilder<AppointmentNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(appointment.id)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    appointment.id,
                    ExistingWorkPolicy.REPLACE,
                    notificationWork
                )
        }

        fun cancelNotification(context: Context, appointmentId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(appointmentId)
        }
    }
}