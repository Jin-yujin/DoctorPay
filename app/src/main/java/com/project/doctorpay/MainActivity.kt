package com.project.doctorpay

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.navercorp.nid.NaverIdLoginSDK
import com.project.doctorpay.ui.calendar.Appointment
import com.project.doctorpay.ui.calendar.CalendarFragment
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.ui.home.HomeFragment
import com.project.doctorpay.ui.map.MapViewFragment
import com.project.doctorpay.ui.member.LoginActivity
import com.project.doctorpay.ui.mypage.MyPageFragment
import com.kakao.sdk.user.UserApiClient as LoginClient

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val _newAppointment = MutableLiveData<Appointment?>()
    val newAppointment: LiveData<Appointment?> = _newAppointment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            // If not logged in, redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.navigation_home -> {
                    // 홈 프래그먼트로 전환
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()
                    true
                }
                R.id.navigation_map_list -> {
                    // 지도 프래그먼트로 전환
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, MapViewFragment())
                        .commit()
                    true
                }
                R.id.navigation_calendar -> {
                    // 캘린더 프래그먼트로 전환
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, CalendarFragment())
                        .commit()
                    true
                }
                R.id.navigation_mypage -> {
                    // 마이페이지 프래그먼트로 전환
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, MyPageFragment())
                        .commit()
                    true
                }
                R.id.navigation_like_list -> {
                    // 찜 목록 프래그먼트로 전환
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, FavoriteFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }

        // 기본 프래그먼트 설정 (예: 홈)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()

    }

    fun logout() {
        auth.signOut()

        // Kakao logout
        LoginClient.instance.logout { error ->
            if (error != null) {
                // Handle error if needed
            }
        }

        // Naver logout
        NaverIdLoginSDK.logout()

        // Google logout
        val googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN)
        googleSignInClient.signOut().addOnCompleteListener {
            // 로그아웃 후 로그인 화면으로 이동
            navigateToLoginScreen()
        }
    }

    private fun navigateToLoginScreen() {
        // Clear login state
        val sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("is_logged_in", false).apply()

        // LoginActivity로 이동
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    fun addAppointmentToCalendar(appointment: Appointment) {
        _newAppointment.value = appointment
    }

    fun clearNewAppointment() {
        _newAppointment.value = null
    }

}