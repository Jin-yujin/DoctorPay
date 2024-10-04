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
    private lateinit var hospitalName: String
    private lateinit var hospitalAddress: String
    private lateinit var hospitalPhone: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityHospitalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve hospital information from intent
        hospitalName = intent.getStringExtra("HOSPITAL_NAME") ?: "Unknown Hospital"
        hospitalAddress = "서울 서대문구 연희로 272 동신병원 본관동 (홍은동)" // 예시 주소, 실제로는 인텐트에서 받아와야 함
        hospitalPhone = "02-396-9161" // 예시 전화번호, 실제로는 인텐트에서 받아와야 함

        // Set up views with hospital information
        binding.tvHospitalName.text = hospitalName
        binding.tvHospitalType.text = "응급의료시설"
        binding.tvHospitalAddress.text = hospitalAddress
        binding.tvHospitalPhone.text = hospitalPhone
        binding.ratingBar.rating = 4.5f // 예시 평점, 실제로는 인텐트에서 받아와야 함

        binding.tvHospitalHours.text = "진료시간: 평일 09:00-18:00, 토요일 09:00-13:00"
        binding.tvHospitalHoliday.text = "휴일: 일요일, 공휴일"
        binding.tvNightCare.text = "야간진료: 가능"
        binding.tvFemaleDoctors.text = "여의사 진료: 가능"

        // Set up other views and click listeners
        setupClickListeners()
        // Load reviews and non-covered items
        loadReviewPreviews()
        loadNonCoveredItems()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnStart.setOnClickListener { openMapWithDirections("출발") }
        binding.btnDestination.setOnClickListener { openMapWithDirections("도착") }
        binding.btnSave.setOnClickListener { /* TODO: Implement save functionality */ }
        binding.btnCall.setOnClickListener { dialPhoneNumber(hospitalPhone) }
        binding.btnShare.setOnClickListener { shareHospitalInfo() }
        binding.tvHospitalPhone.setOnClickListener { dialPhoneNumber(hospitalPhone) }

        binding.btnAppointment.setOnClickListener {
            // TODO: Implement appointment functionality
        }

        binding.btnMoreReviews.setOnClickListener {
            val intent = Intent(this, com.project.doctorpay.ui.reviews.ReviewsActivity::class.java).apply {
                putExtra("HOSPITAL_NAME", hospitalName)
            }
            startActivity(intent)
        }

        binding.btnMoreNonCoveredItems.setOnClickListener {
            // TODO: Implement non-covered items list functionality
        }
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

    private fun loadReviewPreviews() {
        // TODO: Load review previews from API or database
        // For now, we'll add some dummy data
        addReviewPreview("김OO", "친절하고 좋았어요", 5f)
        addReviewPreview("이OO", "대기 시간이 좀 길었어요", 3f)
    }

    private fun loadNonCoveredItems() {
        // TODO: Load non-covered items from API or database
        // For now, we'll add some dummy data
        addNonCoveredItem("MRI 검사", "500,000원")
        addNonCoveredItem("치과 임플란트", "1,500,000원")
    }

    private fun dialPhoneNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }

    private fun shareHospitalInfo() {
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