package com.project.doctorpay.ui.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.project.doctorpay.R

class MyPageFragment : Fragment() {

    private lateinit var tvVersion: TextView

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

        // 약관 및 정책
        val termsAndPolicyLayout: View = view.findViewById(R.id.layoutTermsAndPolicy)
        termsAndPolicyLayout.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TermsAndPolicyFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}