package com.project.doctorpay.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemAppointmentBinding
import java.util.Date

data class Appointment(
    val id: String = "", // Firestore document ID
    val userId: String = "", // 사용자 ID
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val time: String = "",
    val hospitalName: String = "",
    val notes: String = "",
    val timestamp: Date = Date() // 생성/수정 시간
) {
    // Firestore 문서를 Appointment 객체로 변환하는 companion object
    companion object {
        fun fromDocument(document: com.google.firebase.firestore.DocumentSnapshot): Appointment {
            return Appointment(
                id = document.id,
                userId = document.getString("userId") ?: "",
                year = document.getLong("year")?.toInt() ?: 0,
                month = document.getLong("month")?.toInt() ?: 0,
                day = document.getLong("day")?.toInt() ?: 0,
                time = document.getString("time") ?: "",
                hospitalName = document.getString("hospitalName") ?: "",
                notes = document.getString("notes") ?: "",
                timestamp = document.getDate("timestamp") ?: Date()
            )
        }
    }

    // Appointment 객체를 Firestore 문서로 변환하는 함수
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "year" to year,
            "month" to month,
            "day" to day,
            "time" to time,
            "hospitalName" to hospitalName,
            "notes" to notes,
            "timestamp" to timestamp
        )
    }
}

class AppointmentAdapter(
    private val onEditClick: (Appointment) -> Unit,
    private val onDeleteClick: (Appointment) -> Unit
) : ListAdapter<Appointment, AppointmentAdapter.AppointmentViewHolder>(AppointmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AppointmentViewHolder(
        private val binding: ItemAppointmentBinding,
        private val onEditClick: (Appointment) -> Unit,
        private val onDeleteClick: (Appointment) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appointment: Appointment) {
            binding.apply {
                hospitalNameTextView.text = appointment.hospitalName
                timeTextView.text = appointment.time
                notesTextView.text = appointment.notes

                val dateText = "${appointment.year}년 ${appointment.month + 1}월 ${appointment.day}일"
                dateTextView.text = dateText

                editButton.setOnClickListener { onEditClick(appointment) }
                deleteButton.setOnClickListener { onDeleteClick(appointment) }
            }
        }
    }

    class AppointmentDiffCallback : DiffUtil.ItemCallback<Appointment>() {
        override fun areItemsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
            return oldItem == newItem
        }
    }
}