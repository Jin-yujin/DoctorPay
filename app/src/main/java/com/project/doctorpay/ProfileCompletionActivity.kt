package com.project.doctorpay

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.project.doctorpay.databinding.ActivityProfileCompletionBinding
import com.project.doctorpay.DB.UserProfile

class ProfileCompletionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileCompletionBinding
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        setupRegionSpinner()
        setupCompleteButton()
    }

    private fun setupRegionSpinner() {
        val regions = arrayOf("서울", "경기", "인천", "강원", "충북", "충남", "대전", "세종", "전북", "전남", "광주", "경북", "경남", "대구", "울산", "부산", "제주")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, regions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.regionSpinner.adapter = adapter
    }

    private fun setupCompleteButton() {
        binding.completeButton.setOnClickListener {
            val nickname = binding.nicknameEditText.text.toString().trim()
            val age = when (binding.ageRadioGroup.checkedRadioButtonId) {
                R.id.age20Radio -> "20세 미만"
                R.id.age20_29Radio -> "20-29세"
                R.id.age30_39Radio -> "30-39세"
                R.id.age40_49Radio -> "40-49세"
                R.id.age50PlusRadio -> "50세 이상"
                else -> ""
            }
            val gender = when (binding.genderRadioGroup.checkedRadioButtonId) {
                R.id.maleRadio -> "남자"
                R.id.femaleRadio -> "여자"
                R.id.otherGenderRadio -> "기타"
                else -> ""
            }
            val region = binding.regionSpinner.selectedItem.toString()

            if (nickname.isNotEmpty() && age != null && gender.isNotEmpty() && region.isNotEmpty()) {
                val user = auth.currentUser
                val userProfile = UserProfile(user?.email ?: "", nickname, age, gender, region)

                // Firestore에 사용자 프로필 저장
                user?.let {
                    db.collection("users").document(it.uid)
                        .set(userProfile)
                        .addOnSuccessListener {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "프로필 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "모든 필드를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}