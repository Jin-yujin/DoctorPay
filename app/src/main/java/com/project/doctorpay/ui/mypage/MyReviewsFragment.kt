package com.project.doctorpay.ui.mypage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        reviewAdapter = ReviewAdapter().apply {
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
        viewModel.updateReview(review)
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