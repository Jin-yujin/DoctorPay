package com.project.doctorpay.ui.calender

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
import java.util.Date

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var appointmentRecyclerView: RecyclerView
    private lateinit var addAppointmentButton: View

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
        // 여기에 어댑터를 설정합니다. 예약 목록 데이터를 표시하기 위한 커스텀 어댑터를 만들어야 합니다.
    }

    private fun setupAddAppointmentButton() {
        addAppointmentButton.setOnClickListener {
            // 여기에 예약 추가 다이얼로그 또는 액티비티를 시작하는 코드를 추가합니다.
            Toast.makeText(context, "예약 추가 기능 구현 예정", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAppointmentList(year: Int, month: Int, dayOfMonth: Int) {
        // 선택된 날짜에 대한 예약 목록을 가져와 RecyclerView를 업데이트합니다.
        // 이 부분은 실제 데이터 소스(예: 데이터베이스 또는 API)와 연동하여 구현해야 합니다.
        Toast.makeText(context, "$year/${month+1}/$dayOfMonth 예약 목록 업데이트", Toast.LENGTH_SHORT).show()
    }
}