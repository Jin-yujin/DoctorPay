package com.project.doctorpay

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.kakao.sdk.auth.model.OAuthToken
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.project.doctorpay.databinding.ActivityLoginBinding
import com.kakao.sdk.user.UserApiClient as LoginClient

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupLoginButton()
        setupSocialLoginButtons()
        setupSignUpTextView()
        setupForgotPasswordTextView()

        // Naver SDK 초기화
        NaverIdLoginSDK.initialize(this, "YYfE8Topjmu6Sp_yBcPA", "Jzh2Zl5AOM", "닥터페이")
    }

    private fun setupLoginButton() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            startMainActivity()
                        } else {
                            Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
//                Toast.makeText(this, "ID와 Password를 입력하세요.", Toast.LENGTH_SHORT).show()
            }

            // 여기서 실제 로그인 로직을 구현할 수 있습니다.
            // 지금은 단순히 MainActivity로 이동합니다.
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // LoginActivity를 종료하여 뒤로 가기 시 로그인 화면으로 돌아가지 않도록 합니다.
        }
    }

    private fun setupSocialLoginButtons() {
        // 구글 소셜 로그인
        binding.googleLoginButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // 카카오 소셜 로그인
        binding.kakaoLoginButton.setOnClickListener {
            if (LoginClient.instance.isKakaoTalkLoginAvailable(this)) {
                LoginClient.instance.loginWithKakaoTalk(this) { token, error ->
                    if (error != null) {
                        Log.e("KakaoLogin", "카카오톡으로 로그인 실패", error)
                    } else if (token != null) {
                        Log.i("KakaoLogin", "카카오톡으로 로그인 성공 ${token.accessToken}")
                        startMainActivity()
                    }
                }
            } else {
                LoginClient.instance.loginWithKakaoAccount(this) { token, error ->
                    if (error != null) {
                        Log.e("KakaoLogin", "카카오계정으로 로그인 실패", error)
                    } else if (token != null) {
                        Log.i("KakaoLogin", "카카오계정으로 로그인 성공 ${token.accessToken}")
                        startMainActivity()
                    }
                }
            }
        }

        // 네이버 소셜 로그인
        binding.naverLoginButton.setOnClickListener {
            NaverIdLoginSDK.authenticate(this, naverCallback)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startMainActivity()
                } else {
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private val kakaoCallback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        if (error != null) {
            Toast.makeText(this, "Kakao login failed: ${error.message}", Toast.LENGTH_SHORT).show()
        } else if (token != null) {
            // Kakao 로그인 성공, Firebase Custom Token 생성 요청
            // 서버에 Kakao 토큰을 보내고 Firebase Custom Token을 받아와야 합니다.
            // 이 부분은 서버 구현이 필요합니다.
            // 여기서는 임시로 바로 MainActivity로 이동합니다.
            startMainActivity()
        }
    }

    private val naverCallback = object : OAuthLoginCallback {
        override fun onSuccess() {
            // Naver 로그인 성공, Firebase Custom Token 생성 요청
            // 서버에 Naver 토큰을 보내고 Firebase Custom Token을 받아와야 합니다.
            // 이 부분은 서버 구현이 필요합니다.
            // 여기서는 임시로 바로 MainActivity로 이동합니다.
            startMainActivity()
        }

        override fun onFailure(httpStatus: Int, message: String) {
            Toast.makeText(this@LoginActivity, "Naver login failed: $message", Toast.LENGTH_SHORT).show()
        }

        override fun onError(errorCode: Int, message: String) {
            Toast.makeText(this@LoginActivity, "Naver login error: $message", Toast.LENGTH_SHORT).show()
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

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}