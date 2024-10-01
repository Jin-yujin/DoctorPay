package com.project.doctorpay

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.project.doctorpay.databinding.ActivitySignUpBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var auth: FirebaseAuth

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
            val email = binding.idEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "가입되었습니다.", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "가입 실패하였습니다.: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "ID와 Password를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}