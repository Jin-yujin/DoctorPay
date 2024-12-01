package com.project.doctorpay.ui.calendar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import java.util.concurrent.TimeUnit
import java.time.LocalDateTime
import java.time.ZoneId

class AppointmentNotificationWorker(
    private val context: Context,
    private val workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val hospitalName = inputData.getString(KEY_HOSPITAL_NAME) ?: return Result.failure()
        val appointmentTime = inputData.getString(KEY_APPOINTMENT_TIME) ?: return Result.failure()
        val appointmentId = inputData.getString(KEY_APPOINTMENT_ID) ?: return Result.failure()

        showNotification(hospitalName, appointmentTime, appointmentId)
        return Result.success()
    }

    private fun showNotification(hospitalName: String, appointmentTime: String, appointmentId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 채널 생성
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "예약 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "병원 예약 알림을 표시합니다"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭시 앱 실행을 위한 PendingIntent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("appointment_id", appointmentId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 알림 생성
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("병원 예약 알림")
            .setContentText("오늘 ${hospitalName}에 ${appointmentTime} 예약이 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(appointmentId.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "appointment_notification_channel"
        private const val KEY_HOSPITAL_NAME = "hospital_name"
        private const val KEY_APPOINTMENT_TIME = "appointment_time"
        private const val KEY_APPOINTMENT_ID = "appointment_id"

        fun scheduleNotification(
            context: Context,
            appointment: com.project.doctorpay.ui.calendar.Appointment,
            notifyHoursBefore: Long = 24 // 기본값으로 24시간 전에 알림
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

            // 이미 지난 시간이면 알림을 설정하지 않음
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

            val notificationWork = OneTimeWorkRequestBuilder<AppointmentNotificationWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(appointment.id)  // 일정별로 고유한 태그 설정
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