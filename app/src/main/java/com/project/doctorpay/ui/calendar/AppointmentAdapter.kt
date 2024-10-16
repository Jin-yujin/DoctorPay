package com.project.doctorpay.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemAppointmentBinding

data class Appointment(
    val year: Int,
    val month: Int,
    val day: Int,
    val time: String,
    val hospitalName: String,
    val notes: String
)

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
            binding.hospitalNameTextView.text = appointment.hospitalName
            binding.timeTextView.text = appointment.time
            binding.notesTextView.text = appointment.notes

            binding.editButton.setOnClickListener { onEditClick(appointment) }
            binding.deleteButton.setOnClickListener { onDeleteClick(appointment) }
        }
    }

    class AppointmentDiffCallback : DiffUtil.ItemCallback<Appointment>() {
        override fun areItemsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Appointment, newItem: Appointment): Boolean {
            return oldItem == newItem
        }
    }
}