package com.project.doctorpay

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.project.doctorpay.DB.UserProfile
import com.project.doctorpay.databinding.ActivitySignUpBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        setupRegionSpinner()
        setupSignUpButton()
    }

    private fun setupRegionSpinner() {
        val regions = arrayOf("서울", "경기", "인천", "강원", "충북", "충남", "대전", "세종", "전북", "전남", "광주", "경북", "경남", "대구", "울산", "부산", "제주")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, regions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.regionSpinner.adapter = adapter
    }

    private fun setupSignUpButton() {
        binding.signUpButton.setOnClickListener {
            val email = binding.idEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()
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

            if (email.isNotEmpty() && password.isNotEmpty() && nickname.isNotEmpty() &&
                age.isNotEmpty() && gender.isNotEmpty() && region.isNotEmpty()) {

                // Firebase Authentication으로 사용자 생성
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            val userProfile = UserProfile(email, nickname, age, gender, region)

                            // Firestore에 사용자 프로필 저장
                            user?.let {
                                db.collection("users").document(it.uid)
                                    .set(userProfile)
                                    .addOnSuccessListener {
                                        Log.d("SignUpActivity", "User profile saved successfully")
                                        Toast.makeText(this, "가입되었습니다.", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("SignUpActivity", "Error saving user profile", e)
                                        Toast.makeText(this, "프로필 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            } ?: run {
                                Log.e("SignUpActivity", "User is null after successful authentication")
                                Toast.makeText(this, "사용자 생성 오류", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("SignUpActivity", "Authentication failed", task.exception)
                            Toast.makeText(this, "가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "모든 필드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                Log.d("SignUpActivity", "Email: $email, Password: $password, Nickname: $nickname, Age: $age, Gender: $gender, Region: $region")
            }
        }
    }
}