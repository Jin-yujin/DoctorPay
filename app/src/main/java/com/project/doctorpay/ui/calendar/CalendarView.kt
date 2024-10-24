package com.project.doctorpay.ui.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*

class CalendarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val appointmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#02354D")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0CD7EE")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val selectedDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var cellWidth = 0f
    private var cellHeight = 0f
    private val calendar = Calendar.getInstance()
    private val today = Calendar.getInstance()
    private var selectedDate: Triple<Int, Int, Int>? = null  // 선택된 날짜 저장
    private val appointments = mutableSetOf<Triple<Int, Int, Int>>()
    private var onMonthChangeListener: ((Int, Int) -> Unit)? = null
    private var onDateClickListener: ((Int, Int, Int) -> Unit)? = null

    private val dayOfWeek = arrayOf("일", "월", "화", "수", "목", "금", "토")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellWidth = w / 7f
        cellHeight = (h - 150f) / 5f  // 상단에 월 표시와 요일을 위한 공간 확보
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawHeader(canvas)
        drawCalendar(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        val monthYear = SimpleDateFormat("yyyy년 MM월", Locale.getDefault()).format(calendar.time)
        canvas.drawText(monthYear, width / 2f, 60f, headerTextPaint)

        for (i in 0..6) {
            canvas.drawText(dayOfWeek[i], cellWidth * (i + 0.5f), 120f, textPaint)
        }
    }

    private fun drawCalendar(canvas: Canvas) {
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val monthStartDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in 0 until 6) {
            for (j in 0 until 7) {
                val dayNumber = i * 7 + j - monthStartDayOfWeek + 1
                if (dayNumber in 1..daysInMonth) {
                    val x = j * cellWidth + cellWidth / 2
                    val y = i * cellHeight + cellHeight / 2 + 150f // 헤더 높이 고려

                    // 선택된 날짜 배경 그리기
                    val isSelected = selectedDate?.let { (year, month, day) ->
                        year == calendar.get(Calendar.YEAR) &&
                                month == calendar.get(Calendar.MONTH) &&
                                day == dayNumber
                    } ?: false

                    if (isSelected) {
                        val rect = RectF(
                            j * cellWidth + 5f,
                            i * cellHeight + 150f,
                            (j + 1) * cellWidth - 5f,
                            (i + 1) * cellHeight + 150f
                        )
                        val cornerRadius = 15f
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectedDatePaint)
                    }

                    // 오늘 날짜 표시
                    val isToday = (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                            calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                            dayNumber == today.get(Calendar.DAY_OF_MONTH))

                    canvas.drawText(dayNumber.toString(), x, y, if (isToday) todayPaint else textPaint)

                    // 일정 표시
                    if (appointments.contains(Triple(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), dayNumber))) {
                        canvas.drawRect(
                            j * cellWidth + cellWidth * 0.1f,
                            (i + 1) * cellHeight + 130f - cellHeight * 0.2f,
                            (j + 1) * cellWidth - cellWidth * 0.1f,
                            (i + 1) * cellHeight + 130f - cellHeight * 0.1f,
                            appointmentPaint
                        )
                    }
                }
            }
        }
    }

    fun setDate(year: Int, month: Int) {
        calendar.set(year, month, 1)
        invalidate()
    }

    fun setSelectedDate(year: Int, month: Int, day: Int) {
        selectedDate = Triple(year, month, day)
        invalidate()
    }

    fun moveToNextMonth() {
        calendar.add(Calendar.MONTH, 1)
        onMonthChangeListener?.invoke(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
        invalidate()
    }

    fun moveToPreviousMonth() {
        calendar.add(Calendar.MONTH, -1)
        onMonthChangeListener?.invoke(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
        invalidate()
    }

    fun setOnMonthChangeListener(listener: (Int, Int) -> Unit) {
        onMonthChangeListener = listener
    }

    fun setAppointments(appointmentList: List<Appointment>) {
        appointments.clear()
        appointments.addAll(appointmentList.map { Triple(it.year, it.month, it.day) })
        invalidate()
    }

    fun setOnDateClickListener(listener: (Int, Int, Int) -> Unit) {
        onDateClickListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val row = ((event.y - 150f) / cellHeight).toInt()
            val col = (event.x / cellWidth).toInt()

            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val monthStartDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
            val dayNumber = row * 7 + col - monthStartDayOfWeek + 1

            if (dayNumber in 1..calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                selectedDate = Triple(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    dayNumber
                )
                onDateClickListener?.invoke(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    dayNumber
                )
                invalidate()
            }
        }
        return true
    }
}