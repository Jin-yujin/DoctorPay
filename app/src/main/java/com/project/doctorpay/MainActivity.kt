package com.project.doctorpay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils.replace
import android.util.Log
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.navercorp.nid.NaverIdLoginSDK
import com.project.doctorpay.api.HospitalViewModel
import com.project.doctorpay.api.HospitalViewModelFactory
import com.project.doctorpay.network.NetworkModule
import com.project.doctorpay.ui.calendar.Appointment
import com.project.doctorpay.ui.calendar.CalendarFragment
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.ui.home.HomeFragment
import com.project.doctorpay.ui.map.MapViewFragment
import com.project.doctorpay.ui.member.LoginActivity
import com.project.doctorpay.ui.mypage.MyPageFragment
import com.kakao.sdk.user.UserApiClient as LoginClient
import android.Manifest

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private val _newAppointment = MutableLiveData<Appointment?>()
    val newAppointment: LiveData<Appointment?> = _newAppointment


    val hospitalViewModel: HospitalViewModel by viewModels {
        HospitalViewModelFactory(NetworkModule.healthInsuranceApi)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Check login status from SharedPreferences first
        val sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)

        auth = FirebaseAuth.getInstance()

        if (!isLoggedIn || auth.currentUser == null) {
            // If not logged in, redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // 알림 권한 체크 추가
        checkNotificationPermission()

        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // HomeFragment에 ViewModel 전달
                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.fragment_container,
                            HomeFragment.newInstance(hospitalViewModel)
                        )
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

        // 기본 프래그먼트 설정에도 ViewModel 전달
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment.newInstance(hospitalViewModel))
            .commit()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 권한이 허용됨
                    Log.d("MainActivity", "Notification permission granted")
                } else {
                    // 권한이 거부됨
                    Log.d("MainActivity", "Notification permission denied")
                }
            }
        }
    }

    fun logout() {
        // Clear login state first
        val sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("is_logged_in", false).apply()

        // Firebase logout
        auth.signOut()

        // Kakao logout - Add null check
        LoginClient.instance.logout { error ->
            if (error != null) {
                // Handle error if needed
            }
        }

        try {
            // Initialize Naver SDK before logout
            NaverIdLoginSDK.initialize(this, "YYfE8Topjmu6Sp_yBcPA", "Jzh2Zl5AOM", "닥터페이")
            NaverIdLoginSDK.logout()
        } catch (e: Exception) {
            // Handle any potential Naver logout errors
        }

        // Google logout
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener {
            // Move to login screen after all logout processes are complete
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // MainActivity.kt
    private fun n  (fragment: Fragment) {
        try {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, fragment)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading fragment", e)
            MyApplication.showToast("화면 전환 중 오류가 발생했습니다.")
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