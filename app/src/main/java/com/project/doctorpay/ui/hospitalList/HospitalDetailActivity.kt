package com.project.doctorpay.ui.hospitalList

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.project.doctorpay.R
import com.project.doctorpay.databinding.ActivityHospitalDetailBinding

class HospitalDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHospitalDetailBinding
    private lateinit var hospitalAddress: String
    private lateinit var hospitalPhone: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityHospitalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val hospitalName = intent.getStringExtra("HOSPITAL_NAME") ?: ""
        hospitalAddress = "서울 서대문구 연희로 272 동신병원 본관동 (홍은동)" // 예시 주소
        hospitalPhone = "02-396-9161" // 예시 전화번호

        binding.tvHospitalName.text = hospitalName
        binding.tvHospitalType.text = "응급의료시설"
        binding.tvHospitalAddress.text = hospitalAddress
        binding.tvHospitalPhone.text = hospitalPhone
        binding.ratingBar.rating = 4.5f // 예시 평점

        binding.tvHospitalHours.text = "진료시간: 평일 09:00-18:00, 토요일 09:00-13:00"
        binding.tvHospitalHoliday.text = "휴일: 일요일, 공휴일"
        binding.tvNightCare.text = "야간진료: 가능"
        binding.tvFemaleDoctors.text = "여의사 진료: 가능"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnStart.setOnClickListener { openMapWithDirections("출발") }
        binding.btnDestination.setOnClickListener { openMapWithDirections("도착") }
        binding.btnSave.setOnClickListener { /* TODO: Implement save functionality */ }
        binding.btnCall.setOnClickListener { dialPhoneNumber(hospitalPhone) }
        binding.btnShare.setOnClickListener { shareHospitalInfo(hospitalName) }
        binding.tvHospitalPhone.setOnClickListener { dialPhoneNumber(hospitalPhone) }

        binding.btnAppointment.setOnClickListener {
            // TODO: Implement appointment functionality
        }

        binding.btnMoreReviews.setOnClickListener {
            // TODO: Open full review list
        }

        binding.btnMoreNonCoveredItems.setOnClickListener {
            // TODO: Open full non-covered items list
        }

        // Add sample reviews
        addReviewPreview("김OO", "친절하고 좋았어요", 5f)
        addReviewPreview("이OO", "대기 시간이 좀 길었어요", 3f)

        // Add sample non-covered items
        addNonCoveredItem("MRI 검사", "500,000원")
        addNonCoveredItem("치과 임플란트", "1,500,000원")
    }

    private fun openMapWithDirections(mode: String) {
        val encodedAddress = Uri.encode(hospitalAddress)
        val uri = when (mode) {
            "출발" -> Uri.parse("https://maps.google.com/maps?saddr=$encodedAddress&daddr=")
            "도착" -> Uri.parse("https://maps.google.com/maps?daddr=$encodedAddress")
            else -> Uri.parse("https://maps.google.com/maps?q=$encodedAddress")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(browserIntent)
        }
    }

    private fun dialPhoneNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }

    private fun shareHospitalInfo(hospitalName: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, hospitalName)
            putExtra(Intent.EXTRA_TEXT, "$hospitalName\n$hospitalAddress\n$hospitalPhone")
        }
        startActivity(Intent.createChooser(shareIntent, "공유하기"))
    }

    private fun addReviewPreview(name: String, content: String, rating: Float) {
        val reviewView = LayoutInflater.from(this).inflate(R.layout.item_review_preview, binding.layoutReviews, false)
        reviewView.findViewById<TextView>(R.id.tvReviewerName).text = name
        reviewView.findViewById<TextView>(R.id.tvReviewContent).text = content
        reviewView.findViewById<RatingBar>(R.id.rbReviewRating).rating = rating
        binding.layoutReviews.addView(reviewView)
    }

    private fun addNonCoveredItem(itemName: String, price: String) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_non_covered, binding.layoutNonCoveredItems, false)
        itemView.findViewById<TextView>(R.id.tvItemName).text = itemName
        itemView.findViewById<TextView>(R.id.tvItemPrice).text = price
        binding.layoutNonCoveredItems.addView(itemView)
    }
}