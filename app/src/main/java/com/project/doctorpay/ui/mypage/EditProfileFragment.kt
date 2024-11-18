package com.project.doctorpay.ui.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentEditProfileBinding

class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRegionSpinner()
        loadUserProfile()
        setupSaveButton()
    }

    private fun setupRegionSpinner() {
        val regions = arrayOf("서울", "경기", "인천", "강원", "충북", "충남", "대전", "세종",
            "전북", "전남", "광주", "경북", "경남", "대구", "울산", "부산", "제주")
        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, regions)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.regionSpinner.adapter = adapter
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        // 닉네임 설정 (수정 불가)
                        binding.nicknameTextView.text = document.getString("nickname") ?: ""

                        // 나이대 설정
                        val age = document.getString("age")
                        val ageButtonId = when (age) {
                            "20세 미만" -> R.id.age20Radio
                            "20-29세" -> R.id.age20_29Radio
                            "30-39세" -> R.id.age30_39Radio
                            "40-49세" -> R.id.age40_49Radio
                            "50세 이상" -> R.id.age50PlusRadio
                            else -> null
                        }
                        ageButtonId?.let { binding.ageRadioGroup.check(it) }

                        val gender = document.getString("gender")
                        val genderButtonId = when (gender) {
                            "남자" -> R.id.maleRadio
                            "여자" -> R.id.femaleRadio
                            "기타" -> R.id.otherGenderRadio
                            else -> null
                        }
                        genderButtonId?.let { binding.genderRadioGroup.check(it) }

                        // 지역 설정
                        val region = document.getString("region")
                        val regionPosition = (binding.regionSpinner.adapter as ArrayAdapter<String>)
                            .getPosition(region)
                        if (regionPosition >= 0) {
                            binding.regionSpinner.setSelection(regionPosition)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "프로필 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            val age = when (binding.ageRadioGroup.checkedRadioButtonId) {
                R.id.age20Radio -> "20세 미만"
                R.id.age20_29Radio -> "20-29세"
                R.id.age30_39Radio -> "30-39세"
                R.id.age40_49Radio -> "40-49세"
                R.id.age50PlusRadio -> "50세 이상"
                else -> ""
            }
            val gender = when (binding.genderRadioGroup.checkedRadioButtonId) {
                R.id.maleRadio -> "남자"
                R.id.femaleRadio -> "여자"
                R.id.otherGenderRadio -> "기타"
                else -> ""
            }
            val region = binding.regionSpinner.selectedItem.toString()

            if (age.isNotEmpty() && gender.isNotEmpty()) {
                updateProfile(age, gender, region)
            } else {
                Toast.makeText(context, "모든 항목을 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProfile(age: String, gender: String, region: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userUpdates = hashMapOf<String, Any>(
                "age" to age,
                "gender" to gender,
                "region" to region
            )

            db.collection("users").document(currentUser.uid)
                .update(userUpdates)
                .addOnSuccessListener {
                    Toast.makeText(context, "프로필이 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "프로필 업데이트 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}