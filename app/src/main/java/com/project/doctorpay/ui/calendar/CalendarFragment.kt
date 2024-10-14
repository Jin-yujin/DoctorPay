package com.project.doctorpay.ui.calendar

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import com.project.doctorpay.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var addAppointmentButton: View
    private lateinit var searchIcon: ImageView
    private lateinit var todayDateView: TodayDateView
    private val appointmentData = mutableListOf<Appointment>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        calendarView = view.findViewById(R.id.calendarView)
        appointmentRecyclerView = view.findViewById(R.id.appointmentRecyclerView)
        addAppointmentButton = view.findViewById(R.id.addAppointmentButton)
        searchIcon = view.findViewById(R.id.searchIcon)
        todayDateView = view.findViewById(R.id.todayDateView)

        setupCalendarView()
        setupRecyclerView()
        setupAddAppointmentButton()
        setupSearchIcon()
        setupTodayDateView()

        return view
    }

    private fun setupSearchIcon() {
        searchIcon.setOnClickListener {
            // 검색 기능 구현
            Toast.makeText(context, "검색 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTodayDateView() {
        todayDateView.updateDate()
        todayDateView.setOnClickListener {
            val today = Calendar.getInstance()
            calendarView.date = today.timeInMillis
            updateAppointmentList(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
        }
    }

    private fun setupCalendarView() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // 선택된 날짜에 대한 예약 목록을 업데이트합니다.
            updateAppointmentList(year, month, dayOfMonth)
        }
    }

    private fun setupRecyclerView() {
        appointmentRecyclerView.layoutManager = LinearLayoutManager(context)
        // 예약 목록에 대한 어댑터를 설정
        appointmentRecyclerView.adapter = AppointmentAdapter(appointmentData)
    }

    private fun setupAddAppointmentButton() {
        addAppointmentButton.setOnClickListener {
            // 예약 추가 다이얼로그 또는 액티비티를 띄우는 코드를 추가합니다.
            showAddAppointmentDialog()
        }
    }

    private fun updateAppointmentList(year: Int, month: Int, dayOfMonth: Int) {
        // 선택된 날짜의 예약을 필터링하고 RecyclerView를 업데이트
        val selectedDateAppointments = appointmentData.filter {
            it.year == year && it.month == month && it.day == dayOfMonth
        }
        (appointmentRecyclerView.adapter as AppointmentAdapter).updateData(selectedDateAppointments)
    }

    private fun showAddAppointmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_appointment, null)
        val hospitalNameTextView = dialogView.findViewById<TextView>(R.id.hospitalNameTextView)
        val hospitalNameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.hospitalNameInputLayout)
        val hospitalNameEditText = dialogView.findViewById<EditText>(R.id.hospitalNameEditText)
        val dateEditText = dialogView.findViewById<EditText>(R.id.dateEditText)
        val timeEditText = dialogView.findViewById<EditText>(R.id.timeEditText)
        val notesEditText = dialogView.findViewById<EditText>(R.id.notesEditText)

        hospitalNameTextView.visibility = View.GONE
        hospitalNameInputLayout.visibility = View.VISIBLE

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = calendarView.date  // 현재 선택된 날짜 사용

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateEditText.setText(dateFormat.format(calendar.time))

        dateEditText.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, day ->
                calendar.set(year, month, day)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateEditText.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        timeEditText.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeEditText.setText(timeFormat.format(calendar.time))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.AppointmentDialogTheme)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.addButton).setOnClickListener {
            val hospitalName = hospitalNameEditText.text.toString()
            val appointmentDate = dateEditText.text.toString()
            val appointmentTime = timeEditText.text.toString()
            val notes = notesEditText.text.toString()

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

            appointmentData.add(newAppointment)
            updateAppointmentList(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            Toast.makeText(context, "예약이 추가되었습니다", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        todayDateView.updateDate()
    }

    fun addAppointment(appointment: Appointment) {
        appointmentData.add(appointment)
        updateAppointmentList(appointment.year, appointment.month, appointment.day)
    }

}

data class Appointment(
    val year: Int,
    val month: Int,
    val day: Int,
    val time: String,
    val hospital: String,
    val notes: String
)

class AppointmentAdapter(private var appointments: List<Appointment>) :
    RecyclerView.Adapter<AppointmentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_add_appointment, parent, false)
        return AppointmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(appointments[position])
    }

    override fun getItemCount(): Int = appointments.size

    fun updateData(newAppointments: List<Appointment>) {
        appointments = newAppointments
        notifyDataSetChanged()
    }
}

class AppointmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    // 예약 정보를 바인딩하는 부분 구현
    fun bind(appointment: Appointment) {
        // 병원 이름, 시간, 메모 등을 itemView에서 설정
    }
}
