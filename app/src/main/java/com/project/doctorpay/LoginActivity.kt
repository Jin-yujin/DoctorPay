package com.project.doctorpay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project.doctorpay.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLoginButton()
        //setupSignUpTextView() //로그인 기능 구현할 때 주석 풀기
        setupSocialLoginButtons()
    }

    private fun setupLoginButton() {
        binding.loginButton.setOnClickListener {
            // 여기서 실제 로그인 로직을 구현할 수 있습니다.
            // 지금은 단순히 MainActivity로 이동합니다.
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // LoginActivity를 종료하여 뒤로 가기 시 로그인 화면으로 돌아가지 않도록 합니다.
        }
    }

    private fun setupSocialLoginButtons() {
        binding.kakaoLoginButton.setOnClickListener {
            // Kakao 로그인 로직 구현
        }

        binding.naverLoginButton.setOnClickListener {
            // Naver 로그인 로직 구현
        }

        binding.googleLoginButton.setOnClickListener {
            // Google 로그인 로직 구현
        }
    }
}