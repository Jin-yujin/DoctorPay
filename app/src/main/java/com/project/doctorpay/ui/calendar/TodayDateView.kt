package com.project.doctorpay.ui.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

class TodayDateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val rect = Rect()
    private val calendar: Calendar = Calendar.getInstance()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 배경 그리기
        paint.color = Color.parseColor("#02354D")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 날짜 텍스트 그리기
        val dateText = calendar.get(Calendar.DAY_OF_MONTH).toString()
        paint.color = Color.WHITE
        paint.textSize = height * 0.5f
        paint.getTextBounds(dateText, 0, dateText.length, rect)
        val x = width / 2f
        val y = height / 2f + rect.height() / 2f - rect.bottom
        canvas.drawText(dateText, x, y, paint)
    }

    fun updateDate() {
        calendar.timeInMillis = System.currentTimeMillis()
        invalidate()
    }
}