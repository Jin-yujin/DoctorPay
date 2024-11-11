package com.project.doctorpay.ui.calendar

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentCalendarBinding
import com.project.doctorpay.databinding.DialogAddAppointmentBinding
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var appointmentAdapter: AppointmentAdapter
    private val appointmentList = mutableListOf<Appointment>()
    private var selectedDate: Calendar = Calendar.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        loadAppointments()

        (activity as? MainActivity)?.newAppointment?.observe(viewLifecycleOwner) { newAppointment ->
            newAppointment?.let {
                addAppointment(it)
                (activity as? MainActivity)?.clearNewAppointment()
            }
        }
    }

    private fun setupViews() {
        binding.calendarView.setDate(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH))
        binding.calendarView.setSelectedDate(
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )

        binding.calendarView.setOnDateClickListener { year, month, day ->
            selectedDate.set(year, month, day)
            binding.calendarView.setSelectedDate(year, month, day)

            // 검색창이 열려있으면 닫기
            if (binding.searchViewContainer.visibility == View.VISIBLE) {
                binding.searchViewContainer.visibility = View.GONE
                binding.searchView.setQuery("", false)
                binding.calendarView.setSearchMode(false)
            }

            loadAppointmentsForDate(year, month, day)
        }

        // 검색창 외의 공간 클릭 시 검색창 닫기
        binding.root.setOnClickListener {
            if (binding.searchViewContainer.visibility == View.VISIBLE) {
                binding.searchViewContainer.visibility = View.GONE
                binding.searchView.setQuery("", false)
                binding.calendarView.setSearchMode(false)
                // 현재 선택된 날짜의 일정 표시
                loadAppointmentsForDate(
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
                )
            }
        }

        binding.calendarView.setOnMonthChangeListener { year, month ->
            selectedDate.set(year, month, 1)
            loadAppointments()
        }

        binding.previousMonthButton.setOnClickListener {
            binding.calendarView.moveToPreviousMonth()
        }

        binding.nextMonthButton.setOnClickListener {
            binding.calendarView.moveToNextMonth()
        }

        // SearchView와 달력 클릭 이벤트가 부모 뷰로 전파되지 않도록 설정
        binding.searchViewContainer.setOnClickListener { /* 이벤트 소비 */ }
        binding.calendarView.setOnClickListener { /* 이벤트 소비 */ }

        setupRecyclerView()
        setupAddAppointmentButton()
        setupSearchView()
        setupTodayDateView()
    }

    private fun setupSearchView() {
        binding.searchIcon.setOnClickListener {
            binding.searchViewContainer.visibility = if (binding.searchViewContainer.visibility == View.VISIBLE) {
                binding.calendarView.setSearchMode(false)  // 검색 모드 해제
                View.GONE
            } else {
                binding.calendarView.setSearchMode(true)   // 검색 모드 설정
                View.VISIBLE
            }
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchAppointments(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    binding.calendarView.setSearchMode(false)  // 검색어가 없으면 검색 모드 해제
                    loadAppointmentsForDate(
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH),
                        selectedDate.get(Calendar.DAY_OF_MONTH)
                    )
                } else {
                    binding.calendarView.setSearchMode(true)   // 검색어가 있으면 검색 모드 설정
                    searchAppointments(newText)
                }
                return true
            }
        })

        // SearchView가 닫힐 때 검색 모드 해제
        binding.searchView.setOnCloseListener {
            binding.calendarView.setSearchMode(false)
            false
        }
    }

    private fun searchAppointments(query: String) {
        val userId = auth.currentUser?.uid ?: return

        // 빈 검색어인 경우 현재 선택된 날짜의 일정 표시
        if (query.isBlank()) {
            loadAppointmentsForDate(
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
            )
            binding.calendarView.setSearchMode(false)
            return
        }

        db.collection("users")
            .document(userId)
            .collection("appointments")
            .get()
            .addOnSuccessListener { documents ->
                val searchResults = documents.mapNotNull { doc ->
                    try {
                        Appointment.fromDocument(doc)
                    } catch (e: Exception) {
                        Log.e("CalendarFragment", "Error parsing appointment", e)
                        null
                    }
                }.filter { appointment ->
                    appointment.hospitalName.contains(query, ignoreCase = true) ||
                            appointment.notes.contains(query, ignoreCase = true)
                }.sortedByDescending { it.timestamp }

                if (searchResults.isEmpty() && query.length >= 2) {
                    showEmptySearchResult()
                } else {
                    hideEmptySearchResult()
                }

                appointmentAdapter.submitList(searchResults)
            }
            .addOnFailureListener { e ->
                Log.e("CalendarFragment", "Error searching appointments", e)
                Toast.makeText(context, "검색 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEmptySearchResult() {
        binding.emptySearchView?.let {
            it.visibility = View.VISIBLE
            binding.appointmentRecyclerView.visibility = View.GONE
        }
    }

    private fun hideEmptySearchResult() {
        binding.emptySearchView?.let {
            it.visibility = View.GONE
            binding.appointmentRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        appointmentAdapter = AppointmentAdapter(
            onEditClick = { appointment -> showEditAppointmentDialog(appointment) },
            onDeleteClick = { appointment -> showDeleteConfirmationDialog(appointment) }
        )
        binding.appointmentRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appointmentAdapter
        }
    }

    // 일정 수정 기능
    private fun showEditAppointmentDialog(appointment: Appointment) {
        val dialogBinding = DialogAddAppointmentBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setTitle(" 일정 수정")
            .create()

        // "일정 추가" 문구 숨기기
        dialogBinding.root.findViewById<TextView>(R.id.titleTextView)?.visibility = View.GONE

        val calendar = Calendar.getInstance().apply {
            set(appointment.year, appointment.month, appointment.day)
        }

        setupDateAndTimeEditTexts(dialogBinding, calendar)

        dialogBinding.hospitalNameEditText.setText(appointment.hospitalName)
        dialogBinding.notesEditText.setText(appointment.notes)

        dialogBinding.addButton.text = "수정"
        dialogBinding.addButton.setOnClickListener {
            val updatedAppointment = Appointment(
                id = appointment.id,
                userId = auth.currentUser?.uid ?: "",
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH),
                day = calendar.get(Calendar.DAY_OF_MONTH),
                time = dialogBinding.timeEditText.text.toString(),
                hospitalName = dialogBinding.hospitalNameEditText.text.toString(),
                notes = dialogBinding.notesEditText.text.toString(),
                timestamp = Date()
            )

            updateAppointment(updatedAppointment)
            dialog.dismiss()
        }

        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // 일정 삭제 기능
    private fun showDeleteConfirmationDialog(appointment: Appointment) {
        AlertDialog.Builder(requireContext())
            .setTitle("일정 삭제")
            .setMessage("이 일정을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteAppointment(appointment)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateAppointment(appointment: Appointment) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("appointments")
            .document(appointment.id)
            .set(appointment.toMap())
            .addOnSuccessListener {
                Toast.makeText(context, "일정이 수정되었습니다", Toast.LENGTH_SHORT).show()
                loadAppointments()
            }
            .addOnFailureListener { e ->
                Log.e("CalendarFragment", "Error updating appointment", e)
                Toast.makeText(context, "일정 수정 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteAppointment(appointment: Appointment) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("appointments")
            .document(appointment.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "일정이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                loadAppointments()
            }
            .addOnFailureListener { e ->
                Log.e("CalendarFragment", "Error deleting appointment", e)
                Toast.makeText(context, "일정 삭제 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupAddAppointmentButton() {
        binding.addAppointmentButton.setOnClickListener {
            showAddAppointmentDialog(selectedDate)
        }
    }

    private fun setupTodayDateView() {
        updateTodayDateText()
        binding.todayDateIcon.setOnClickListener {
            val today = Calendar.getInstance()
            binding.calendarView.setDate(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH)
            )
            binding.calendarView.setSelectedDate(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
            )
            loadAppointmentsForDate(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    private fun updateTodayDateText() {
        val today = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd", Locale.getDefault())
        binding.todayDateText.text = dateFormat.format(today.time)
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

            val appointment = Appointment(
                userId = auth.currentUser?.uid ?: "",
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH),
                day = calendar.get(Calendar.DAY_OF_MONTH),
                time = appointmentTime,
                hospitalName = hospitalName,
                notes = notes,
                timestamp = Date()
            )

            addAppointment(appointment)
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
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dialogBinding.dateEditText.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(dialogBinding: DialogAddAppointmentBinding, calendar: Calendar) {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                dialogBinding.timeEditText.setText(timeFormat.format(calendar.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun addAppointment(appointment: Appointment) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("appointments")
            .add(appointment.toMap())
            .addOnSuccessListener {
                Toast.makeText(context, "일정이 추가되었습니다", Toast.LENGTH_SHORT).show()
                loadAppointments()
            }
            .addOnFailureListener { e ->
                Log.e("CalendarFragment", "Error adding appointment", e)
                Toast.makeText(context, "일정 추가 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAppointments() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("appointments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                appointmentList.clear()
                for (document in documents) {
                    try {
                        val appointment = Appointment.fromDocument(document)
                        appointmentList.add(appointment)
                    } catch (e: Exception) {
                        Log.e("CalendarFragment", "Error parsing appointment", e)
                    }
                }
                binding.calendarView.setAppointments(appointmentList)
                loadAppointmentsForDate(
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
                )
            }
            .addOnFailureListener { e ->
                Log.e("CalendarFragment", "Error loading appointments", e)
                Toast.makeText(context, "일정을 불러오는 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAppointmentsForDate(year: Int, month: Int, dayOfMonth: Int) {
        val appointmentsForDate = appointmentList.filter {
            it.year == year && it.month == month && it.day == dayOfMonth
        }
        appointmentAdapter.submitList(appointmentsForDate)
    }

    override fun onResume() {
        super.onResume()
        updateTodayDateText()
        loadAppointments()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}