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

class ReviewFragment : Fragment(), ReviewAdapter.ReviewActionListener {
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

        binding.recyclerViewReviews.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.averageRatingText.text = "0.0"
        binding.averageRatingBar.rating = 0f

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupAddReviewButton()
    }

    // 리뷰 수정 다이얼로그
    // ReviewActionListener 인터페이스의 메소드 구현
    override fun onEditReview(review: Review) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(R.layout.dialog_add_review)
            .create()

        dialog.show()

        dialog.findViewById<RatingBar>(R.id.ratingBar)?.rating = review.rating
        dialog.findViewById<EditText>(R.id.reviewContent)?.setText(review.content)

        dialog.findViewById<Button>(R.id.submitButton)?.setOnClickListener {
            val newRating = dialog.findViewById<RatingBar>(R.id.ratingBar)?.rating ?: 0f
            val newContent = dialog.findViewById<EditText>(R.id.reviewContent)?.text.toString()

            if (newRating == 0f) {
                Toast.makeText(context, "별점을 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newContent.isBlank()) {
                Toast.makeText(context, "리뷰 내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateReview(review, newRating, newContent)
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.cancelButton)?.setOnClickListener {
            dialog.dismiss()
        }
    }

    // 리뷰 삭제 확인 다이얼로그
    // ReviewActionListener 인터페이스의 메소드 구현
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

    private fun setupToolbar() {
        binding.apply {
            btnBack.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            arguments?.getString("hospitalName")?.let { hospitalName ->
                toolbarTitle.text = " $hospitalName 리뷰"
            } ?: run {
                toolbarTitle.text = "리뷰"
            }
        }
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter().apply {
            setActionListener(this@ReviewFragment)  // ReviewFragment를 리스너로 설정
        }
        binding.recyclerViewReviews.apply {
            adapter = reviewAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        viewModel.reviews.observe(viewLifecycleOwner) { reviews ->
            Log.d("ReviewFragment", "Received ${reviews.size} reviews")
            reviewAdapter.updateList(reviews)

            // 리뷰가 없을 때의 처리
            binding.emptyView.isVisible = reviews.isEmpty()
            binding.recyclerViewReviews.isVisible = reviews.isNotEmpty()

            // 평균 평점 계산 및 표시
            if (reviews.isEmpty()) {
                binding.recyclerViewReviews.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.averageRatingText.text = "0.0"
                binding.averageRatingBar.rating = 0f
            } else {
                binding.recyclerViewReviews.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
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