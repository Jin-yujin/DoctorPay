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
        setupSocialLoginButtons()
        setupSignUpTextView()
        setupForgotPasswordTextView()
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

    private fun setupSignUpTextView() {
        binding.signUpTextView.setOnClickListener {
            // 회원가입 화면으로 이동
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupForgotPasswordTextView() {
        binding.forgotPasswordTextView.setOnClickListener {
            // 비밀번호 찾기 화면으로 이동 또는 비밀번호 찾기 로직 구현
            // 예: val intent = Intent(this, ForgotPasswordActivity::class.java)
            // startActivity(intent)
        }
    }
}