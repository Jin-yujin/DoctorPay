package com.project.doctorpay.ui.calendar

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.doctorpay.MainActivity
import com.project.doctorpay.databinding.FragmentCalendarBinding
import com.project.doctorpay.databinding.DialogAddAppointmentBinding
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {
    private lateinit var binding: FragmentCalendarBinding
    private lateinit var appointmentAdapter: AppointmentAdapter
    private val appointmentList = mutableListOf<Appointment>()
    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        loadAppointments()

        // MainActivity로부터 새 예약 데이터를 받기 위한 Observer 설정
        (activity as? MainActivity)?.newAppointment?.observe(viewLifecycleOwner) { newAppointment ->
            newAppointment?.let {
                addAppointment(it)
                (activity as? MainActivity)?.clearNewAppointment()
            }
        }
    }

    private fun setupViews() {
        setupCalendarView()
        setupRecyclerView()
        setupAddAppointmentButton()
        setupSearchIcon()
        setupTodayDateView()
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate.set(year, month, dayOfMonth)
            loadAppointmentsForDate(year, month, dayOfMonth)
        }
    }

    private fun setupRecyclerView() {
        appointmentAdapter = AppointmentAdapter()
        binding.appointmentRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appointmentAdapter
        }
    }

    private fun setupAddAppointmentButton() {
        binding.addAppointmentButton.setOnClickListener {
            showAddAppointmentDialog(selectedDate)
        }
    }

    private fun setupSearchIcon() {
        binding.searchIcon.setOnClickListener {
            Toast.makeText(context, "검색 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTodayDateView() {
        binding.todayDateView.updateDate()
        binding.todayDateView.setOnClickListener {
            val today = Calendar.getInstance()
            binding.calendarView.date = today.timeInMillis
            loadAppointmentsForDate(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
        }
    }

    private fun showAddAppointmentDialog(selectedDate: Calendar) {
        val dialogBinding = DialogAddAppointmentBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        val calendar = selectedDate.clone() as Calendar

        setupDateAndTimeEditTexts(dialogBinding, calendar)

        dialogBinding.addButton.setOnClickListener {
            val hospitalName = dialogBinding.hospitalNameEditText.text.toString()
            val appointmentDate = dialogBinding.dateEditText.text.toString()
            val appointmentTime = dialogBinding.timeEditText.text.toString()
            val notes = dialogBinding.notesEditText.text.toString()

            if (hospitalName.isBlank() || appointmentDate.isBlank() || appointmentTime.isBlank()) {
                Toast.makeText(context, "모든 필드를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newAppointment = Appointment(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
                appointmentTime,
                hospitalName,
                notes
            )

            addAppointment(newAppointment)
            Toast.makeText(context, "예약이 추가되었습니다", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupDateAndTimeEditTexts(dialogBinding: DialogAddAppointmentBinding, calendar: Calendar) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        dialogBinding.dateEditText.setText(dateFormat.format(calendar.time))
        dialogBinding.timeEditText.setText(timeFormat.format(calendar.time))

        dialogBinding.dateEditText.setOnClickListener {
            showDatePicker(dialogBinding, calendar)
        }

        dialogBinding.timeEditText.setOnClickListener {
            showTimePicker(dialogBinding, calendar)
        }
    }

    private fun showDatePicker(dialogBinding: DialogAddAppointmentBinding, calendar: Calendar) {
        DatePickerDialog(requireContext(), { _, year, month, day ->
            calendar.set(year, month, day)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            dialogBinding.dateEditText.setText(dateFormat.format(calendar.time))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(dialogBinding: DialogAddAppointmentBinding, calendar: Calendar) {
        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            dialogBinding.timeEditText.setText(timeFormat.format(calendar.time))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }

    fun addAppointment(appointment: Appointment) {
        appointmentList.add(appointment)
        saveAppointments()
        loadAppointmentsForDate(appointment.year, appointment.month, appointment.day)
    }

    private fun saveAppointments() {
        val sharedPreferences = requireContext().getSharedPreferences("Appointments", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val appointmentSet = appointmentList.map { Gson().toJson(it) }.toSet()
        editor.putStringSet("appointments", appointmentSet)
        editor.apply()
    }

    private fun loadAppointments() {
        val sharedPreferences = requireContext().getSharedPreferences("Appointments", Context.MODE_PRIVATE)
        val appointmentSet = sharedPreferences.getStringSet("appointments", setOf()) ?: setOf()

        appointmentList.clear()
        for (appointmentJson in appointmentSet) {
            try {
                val appointment = Gson().fromJson(appointmentJson, Appointment::class.java)
                appointmentList.add(appointment)
            } catch (e: Exception) {
                Log.e("CalendarFragment", "Error parsing appointment: $appointmentJson", e)
            }
        }

        val calendar = Calendar.getInstance()
        loadAppointmentsForDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }

    private fun loadAppointmentsForDate(year: Int, month: Int, dayOfMonth: Int) {
        val appointmentsForDate = appointmentList.filter {
            it.year == year && it.month == month && it.day == dayOfMonth
        }
        appointmentAdapter.submitList(appointmentsForDate)
    }

    override fun onResume() {
        super.onResume()
        binding.todayDateView.updateDate()
        loadAppointments()
    }
}