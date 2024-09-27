package com.project.doctorpay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project.doctorpay.databinding.ActivitySignUpBinding

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSignUpButton()
    }

    private fun setupSignUpButton() {
        binding.signUpButton.setOnClickListener {
            // TODO: Implement sign up logic
            // For now, just finish the activity
            finish()
        }
    }
}