package com.project.doctorpay.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.R
import java.util.Calendar

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var addAppointmentButton: View
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

        setupCalendarView()
        setupRecyclerView()
        setupAddAppointmentButton()

        return view
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
            Toast.makeText(context, "예약 추가 기능 구현 예정", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAppointmentList(year: Int, month: Int, dayOfMonth: Int) {
        // 선택된 날짜의 예약을 필터링하고 RecyclerView를 업데이트
        val selectedDateAppointments = appointmentData.filter {
            it.year == year && it.month == month && it.day == dayOfMonth
        }
        (appointmentRecyclerView.adapter as AppointmentAdapter).updateData(selectedDateAppointments)

        Toast.makeText(context, "$year/${month + 1}/$dayOfMonth 예약 목록 업데이트", Toast.LENGTH_SHORT).show()
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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false)
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
