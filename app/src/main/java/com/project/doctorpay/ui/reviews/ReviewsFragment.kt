package com.project.doctorpay.ui.reviews

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.project.doctorpay.R
import com.project.doctorpay.databinding.FragmentReviewsBinding

class ReviewFragment : Fragment() {
    private var _binding: FragmentReviewsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReviewViewModel by viewModels()
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var hospitalId: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReviewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hospitalId = arguments?.getString("hospitalId") ?: return

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupAddReviewButton()
    }

    private fun setupToolbar() {
        binding.apply {
            btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            // 타이틀 설정 (병원 이름 + 리뷰)
            arguments?.getString("hospitalName")?.let { hospitalName ->
                toolbarTitle.text = " $hospitalName 리뷰"
            } ?: run {
                toolbarTitle.text = "리뷰"
            }
        }
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter()
        with(binding.recyclerViewReviews) {
            this.adapter = reviewAdapter
            this.layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            Log.d("ReviewFragment", "Received ${reviews.size} reviews")
            reviewAdapter.updateList(reviews)
            binding.emptyView.isVisible = reviews.isEmpty()

            // 평균 평점 계산
            if (reviews.isNotEmpty()) {
                val avgRating = reviews.map { it.rating }.average().toFloat()
                binding.averageRatingBar.rating = avgRating
                binding.averageRatingText.text = String.format("%.1f", avgRating)
            }
        }

        // 리뷰 상태 관찰
        viewModel.reviewStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is ReviewViewModel.ReviewStatus.Success -> {
                    Toast.makeText(context, "리뷰가 등록되었습니다", Toast.LENGTH_SHORT).show()
                }
                is ReviewViewModel.ReviewStatus.Error -> {
                    Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                }
                is ReviewViewModel.ReviewStatus.Loading -> {
                    // 로딩 표시 (필요한 경우)
                }
            }
        }

        Log.d("ReviewFragment", "Loading reviews for hospital: $hospitalId")
        viewModel.loadReviews(hospitalId)
    }

    private fun setupAddReviewButton() {
        binding.addReviewButton.setOnClickListener {
            showReviewDialog()
        }
    }

    private fun showReviewDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_add_review)
            .create()

        dialog.show()

        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)!!
        val contentEdit = dialog.findViewById<EditText>(R.id.reviewContent)!!
        val submitButton = dialog.findViewById<Button>(R.id.submitButton)!!
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)!!

        submitButton.setOnClickListener {
            val rating = ratingBar.rating
            val content = contentEdit.text.toString()

            if (rating == 0f) {
                Toast.makeText(context, "별점을 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (content.isBlank()) {
                Toast.makeText(context, "리뷰 내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addReview(hospitalId, rating, content)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}