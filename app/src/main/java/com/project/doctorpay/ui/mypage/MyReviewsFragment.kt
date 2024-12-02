package com.project.doctorpay.ui.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentMyReviewsBinding
import com.project.doctorpay.ui.reviews.Review
import com.project.doctorpay.ui.reviews.ReviewAdapter

class MyReviewsFragment : Fragment(), ReviewAdapter.ReviewActionListener {
    private var _binding: FragmentMyReviewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyReviewsViewModel by viewModels()
    private lateinit var reviewAdapter: ReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyReviewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupObservers()

        viewModel.loadMyReviews()
    }

    private fun setupToolbar() {
        binding.apply {
            btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }
            toolbarTitle.text = "내 리뷰"
        }
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter(showHospitalName = true).apply {
            setActionListener(this@MyReviewsFragment)
        }
        binding.recyclerViewMyReviews.apply {
            adapter = reviewAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            reviewAdapter.updateList(reviews)
            binding.emptyView.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerViewMyReviews.visibility = if (reviews.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.reviewStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is MyReviewsViewModel.ReviewStatus.Success -> {
                    binding.progressBar.visibility = View.GONE
                    if (status.isDelete) {
                        Toast.makeText(context, "리뷰가 삭제되었습니다", Toast.LENGTH_SHORT).show()
                    }
                }
                is MyReviewsViewModel.ReviewStatus.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                }
                is MyReviewsViewModel.ReviewStatus.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onEditReview(review: Review) {
        viewModel.loadHospitalDepartments(review.hospitalId) { departments ->
            showEditDialog(review, departments)
        }
    }

    private fun showEditDialog(review: Review, departments: List<String>) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_add_review)
            .create()

        dialog.show()

        val allDepartments = listOf("진료과 선택") + departments

        val departmentSpinner = dialog.findViewById<Spinner>(R.id.departmentSpinner)!!
        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)!!
        val contentEdit = dialog.findViewById<EditText>(R.id.reviewContent)!!

        // 진료과 스피너 설정
        departmentSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            allDepartments
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // 기존 데이터 설정
        val departmentPosition = allDepartments.indexOf(review.department)
        if (departmentPosition != -1) {
            departmentSpinner.setSelection(departmentPosition)
        } else {
            // 현재 진료과가 목록에 없는 경우, 목록에 추가하고 선택
            val newAllDepartments = allDepartments + listOf(review.department)
            departmentSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                newAllDepartments
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            departmentSpinner.setSelection(newAllDepartments.indexOf(review.department))
        }

        ratingBar.rating = review.rating
        contentEdit.setText(review.content)

        dialog.findViewById<Button>(R.id.submitButton)?.setOnClickListener {
            val selectedDepartment = departmentSpinner.selectedItem.toString()
            val newRating = ratingBar.rating
            val newContent = contentEdit.text.toString()

            when {
                selectedDepartment == "진료과 선택" -> {
                    Toast.makeText(context, "진료과를 선택해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newRating == 0f -> {
                    Toast.makeText(context, "별점을 선택해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newContent.isBlank() -> {
                    Toast.makeText(context, "리뷰 내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                else -> {
                    val updatedReview = review.copy(
                        rating = newRating,
                        content = newContent,
                        department = selectedDepartment,
                        timestamp = System.currentTimeMillis()  // 수정 시간 업데이트
                    )
                    viewModel.updateReview(updatedReview)
                    dialog.dismiss()
                }
            }
        }

        dialog.findViewById<Button>(R.id.cancelButton)?.setOnClickListener {
            dialog.dismiss()
        }
    }

    override fun onDeleteReview(review: Review) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("리뷰 삭제")
            .setMessage("정말로 이 리뷰를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteReview(review)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}