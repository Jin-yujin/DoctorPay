package com.project.doctorpay

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.project.doctorpay.ui.calender.CalendarFragment
import com.project.doctorpay.ui.favorite.FavoriteFragment
import com.project.doctorpay.ui.home.HomeFragment
import com.project.doctorpay.ui.map.MapViewFragment
import com.project.doctorpay.ui.mypage.MyPageFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check if user is logged in
        // 임시 로그인 기능 없앨 때 주석 풀기
//        if (!isLoggedIn()) {
//            // If not logged in, redirect to LoginActivity
//            val intent = Intent(this, LoginActivity::class.java)
//            startActivity(intent)
//            finish()
//            return
//        }


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
}