package com.project.doctorpay.ui.member

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.firestore
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = Firebase.firestore
    private lateinit var sharedPreferences: SharedPreferences

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google 로그인 실패", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)

        // 자동 로그인 체크
        if (isLoggedIn()) {
            startMainActivity()
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupLoginButton()
        setupSocialLoginButtons()
        setupSignUpTextView()
        setupForgotPasswordTextView()
        setupAutoLoginCheckbox()

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
                            saveLoginState(true)
                            checkUserProfile(auth.currentUser?.uid, "email")
                        } else {
                            Toast.makeText(
                                this,
                                "이메일과 비밀번호를 확인하세요",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            } else {
                Toast.makeText(this, "이메일과 비밀번호를 입력하세요ㅇㅇㅇ.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupAutoLoginCheckbox() {
        binding.autoLoginCheckBox.setOnCheckedChangeListener { _, isChecked ->
            saveAutoLoginPreference(isChecked)
        }
    }

    private fun saveAutoLoginPreference(isAutoLogin: Boolean) {
        sharedPreferences.edit().putBoolean("auto_login", isAutoLogin).apply()
    }

    private fun saveLoginState(isLoggedIn: Boolean) {
        sharedPreferences.edit().putBoolean("is_logged_in", isLoggedIn).apply()
    }

    private fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean("is_logged_in", false)
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupSocialLoginButtons() {
        // 구글 소셜 로그인
        binding.googleLoginButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // 카카오 소셜 로그인 수정
        binding.kakaoLoginButton.setOnClickListener {
            if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
                UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                    if (error != null) {
                        Log.e("KakaoLogin", "카카오톡 로그인 실패", error)
                        // 카카오톡 로그인 실패시 카카오계정으로 로그인 시도
                        loginWithKakaoAccount()
                    } else if (token != null) {
                        Log.i("KakaoLogin", "카카오톡 로그인 성공 ${token.accessToken}")
                        handleKakaoLoginResult(token, null)
                    }
                }
            } else {
                loginWithKakaoAccount()
            }
        }

        // 네이버 소셜 로그인
        binding.naverLoginButton.setOnClickListener {
            NaverIdLoginSDK.authenticate(this, naverCallback)
        }
    }

    private fun loginWithKakaoAccount() {
        UserApiClient.instance.loginWithKakaoAccount(this, callback = { token, error ->
            if (error != null) {
                Log.e("KakaoLogin", "카카오계정 로그인 실패: ${error.message}", error)
                Toast.makeText(this, "카카오계정 로그인 실패", Toast.LENGTH_SHORT).show()
            } else if (token != null) {
                handleKakaoLoginResult(token, null)
            }
        })
    }

    private fun handleKakaoLoginResult(token: OAuthToken?, error: Throwable?) {
        if (error != null) {
            Log.e("KakaoLogin", "카카오 로그인 실패: ${error.message}", error)
            Toast.makeText(this, "카카오 로그인 실패", Toast.LENGTH_SHORT).show()
            return
        }

        UserApiClient.instance.me { user, error ->
            if (error != null || user == null) {
                Toast.makeText(this, "사용자 정보 요청 실패", Toast.LENGTH_SHORT).show()
                return@me
            }

            // 1. Firebase Auth 처리
            val email = "kakao_${user.id}@doctorpay.com"
            val password = "kakao_${user.id}_secret"

            // 먼저 로그인 시도
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { signInTask ->
                    if (signInTask.isSuccessful) {
                        // 로그인 성공 - 프로필 확인
                        checkKakaoUserProfile(auth.currentUser?.uid, user.id.toString())
                    } else {
                        // 로그인 실패 - 새 계정 생성
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { createTask ->
                                if (createTask.isSuccessful) {
                                    // 계정 생성 성공 - 프로필 작성으로 이동
                                    val intent = Intent(this, ProfileCompletionActivity::class.java)
                                    intent.putExtra("USER_IDENTIFIER", auth.currentUser?.uid)
                                    intent.putExtra("LOGIN_TYPE", "kakao")
                                    intent.putExtra("KAKAO_ID", user.id.toString())
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this, "계정 생성 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                }
        }
    }

    private fun checkKakaoUserProfile(firebaseUid: String?, kakaoId: String) {
        if (firebaseUid == null) return

        // Firebase Auth의 UID로 Firestore 문서 확인
        db.collection("users").document(firebaseUid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // 프로필 있음 - 메인으로 이동
                    saveLoginState(true)
                    startMainActivity()
                } else {
                    // 프로필 없음 - 프로필 작성으로 이동
                    val intent = Intent(this, ProfileCompletionActivity::class.java)
                    intent.putExtra("USER_IDENTIFIER", firebaseUid)
                    intent.putExtra("LOGIN_TYPE", "kakao")
                    intent.putExtra("KAKAO_ID", kakaoId)
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "프로필 확인 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private val naverCallback = object : OAuthLoginCallback {
        override fun onSuccess() {
            NaverIdLoginSDK.getAccessToken()?.let { accessToken ->
                // Firebase 인증용 임시 이메일/비밀번호 생성
                val email = "naver_${accessToken.substring(0, 10)}@doctorpay.com"
                val password = "naver_${accessToken}_secret"

                // Firebase 계정 생성/로그인
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { signInTask ->
                        if (signInTask.isSuccessful) {
                            checkUserProfile(auth.currentUser?.uid, "naver")
                        } else {
                            // 계정이 없으면 생성
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { createTask ->
                                    if (createTask.isSuccessful) {
                                        val intent = Intent(this@LoginActivity, ProfileCompletionActivity::class.java)
                                        intent.putExtra("USER_IDENTIFIER", auth.currentUser?.uid)
                                        intent.putExtra("LOGIN_TYPE", "naver")
                                        startActivity(intent)
                                        finish()
                                    }
                                }
                        }
                    }
            }
        }

        override fun onFailure(httpStatus: Int, message: String) {
            Toast.makeText(this@LoginActivity, "Naver 로그인 실패", Toast.LENGTH_SHORT).show()
        }

        override fun onError(errorCode: Int, message: String) {
            Toast.makeText(this@LoginActivity, "Naver 로그인 에러: $message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSignUpTextView() {
        binding.signUpTextView.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun setupForgotPasswordTextView() {
        binding.forgotPasswordTextView.setOnClickListener {
            // 비밀번호 찾기 기능 구현
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    saveLoginState(true)
                                    startMainActivity()
                                } else {
                                    val intent = Intent(this, ProfileCompletionActivity::class.java)
                                    intent.putExtra("USER_IDENTIFIER", user.uid)
                                    intent.putExtra("LOGIN_TYPE", "google")
                                    startActivity(intent)
                                    finish()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "프로필 확인 실패", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Google 로그인 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkUserProfile(userIdentifier: String?, loginType: String) {
        if (userIdentifier == null) {
            Toast.makeText(this, "사용자 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userDocRef = if (loginType == "email") {
            db.collection("users").document(userIdentifier)
        } else {
            db.collection("users").document("${loginType}_$userIdentifier")
        }

        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    saveLoginState(true)
                    startMainActivity()
                } else {
                    if (loginType != "email") {
                        val intent = Intent(this, ProfileCompletionActivity::class.java)
                        intent.putExtra("USER_IDENTIFIER", "${loginType}_$userIdentifier")
                        intent.putExtra("LOGIN_TYPE", loginType)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "이메일 계정이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "프로필 확인 실패", Toast.LENGTH_SHORT).show()
            }
    }
}