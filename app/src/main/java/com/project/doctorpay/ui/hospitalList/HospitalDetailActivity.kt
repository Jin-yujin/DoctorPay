package com.project.doctorpay.ui.hospitalList

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        binding.btnBack.setOnClickListener { finish() }

        binding.btnStart.setOnClickListener {
            openMapWithDirections("출발")
        }

        binding.btnDestination.setOnClickListener {
            openMapWithDirections("도착")
        }

        binding.btnSave.setOnClickListener {
            // TODO: Implement save functionality
        }

        binding.btnCall.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$hospitalPhone")
            }
            startActivity(intent)
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, hospitalName)
                putExtra(Intent.EXTRA_TEXT, "$hospitalName\n$hospitalAddress\n$hospitalPhone")
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        binding.tvHospitalPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$hospitalPhone")
            }
            startActivity(intent)
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
            // Google Maps app is not installed, open in browser instead
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(browserIntent)
        }
    }
}