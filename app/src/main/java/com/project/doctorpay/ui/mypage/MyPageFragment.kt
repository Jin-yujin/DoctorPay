package com.project.doctorpay.ui.mypage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.doctorpay.MainActivity
import com.project.doctorpay.R
import com.project.doctorpay.ui.member.LoginActivity

class MyPageFragment : Fragment() {

    private lateinit var tvVersion: TextView
    private lateinit var tvNickname: TextView
    private lateinit var tvUserInfo: TextView
    private lateinit var tvLogout: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mypage, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvVersion = view.findViewById(R.id.tvVersion)
        tvNickname = view.findViewById(R.id.tvNickname)
        tvUserInfo = view.findViewById(R.id.tvUserInfo)
        tvLogout = view.findViewById(R.id.tvLogout)

        //알림
        val btnAlramSet: View = view.findViewById(R.id.btnAlramSet)
        btnAlramSet.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AlarmFragment())
                .addToBackStack(null)
                .commit()
        }

        // 약관 및 정책
        val btnTermsAndPolicy: View = view.findViewById(R.id.btnTermsAndPolicy)
        btnTermsAndPolicy.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TermsAndPolicyFragment())
                .addToBackStack(null)
                .commit()
        }

        // 로그아웃 기능 추가
        tvLogout.setOnClickListener {
            logout()
        }

        loadUserData()
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val nickname = document.getString("nickname") ?: "닉네임"
                        val gender = document.getString("gender") ?: "성별"
                        val age = document.getString("age") ?: "나이대"
                        val region = document.getString("region") ?: "지역"

                        tvNickname.text = nickname
                        tvUserInfo.text = "$gender • $age • $region"
                    } else {
                        Toast.makeText(context, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "데이터 로드 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(context, "로그인 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        (activity as? MainActivity)?.logout()

        // MainActivity의 logout() 함수 호출 후 LoginActivity로 이동
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }
}