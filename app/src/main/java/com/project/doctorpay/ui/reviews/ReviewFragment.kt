package com.project.doctorpay.ui.reviews

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Spinner
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

    private lateinit var reviewFilter: ReviewFilter
    private lateinit var departmentSpinner: Spinner
    private lateinit var ratingSpinner: Spinner

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

        setupFilterViews()
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

        val departments = arguments?.getStringArrayList("departments")?.toList() ?: listOf()
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
                    viewModel.updateReview(
                        review = review,
                        newRating = newRating,
                        newContent = newContent,
                        newDepartment = selectedDepartment
                    )
                    dialog.dismiss()
                }
            }
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

    private fun setupFilterViews() {
        reviewFilter = ReviewFilter()

        // 진료과 목록 가져오기
        val departments = arguments?.getStringArrayList("departments")?.toList() ?: listOf()
        val allDepartments = listOf("전체") + departments

        // 진료과 스피너 설정
        departmentSpinner = binding.spinnerDepartment.apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                allDepartments
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    reviewFilter.department = allDepartments[position]
                    reviewAdapter.applyFilter(reviewFilter)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // 별점 스피너 설정 - 변경된 부분
        val ratings = listOf("전체 점수", "5점대", "4점대", "3점대", "2점대", "1점대")
        ratingSpinner = binding.spinnerRating.apply {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                ratings
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // 선택된 점수대 설정
                    reviewFilter.ratingRange = when (position) {
                        0 -> 0..5  // 전체
                        1 -> 5..5  // 5점대
                        2 -> 4..4  // 4점대
                        3 -> 3..3  // 3점대
                        4 -> 2..2  // 2점대
                        5 -> 1..1  // 1점대
                        else -> 0..5
                    }
                    reviewAdapter.applyFilter(reviewFilter)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
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

                // 리뷰 목록이 업데이트될 때마다 맨 위로 스크롤
                binding.recyclerViewReviews.post {
                    binding.recyclerViewReviews.smoothScrollToPosition(0)
                }
            }
        }

        // 리뷰 상태 관찰
        viewModel.reviewStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is ReviewViewModel.ReviewStatus.Success -> {
                    if (status.isDelete) {
                        Toast.makeText(context, "리뷰가 삭제되었습니다", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "리뷰가 등록되었습니다", Toast.LENGTH_SHORT).show()
                    }
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

        val departments = arguments?.getStringArrayList("departments")?.toList() ?: listOf()
        val allDepartments = listOf("진료과 선택") + departments

        val departmentSpinner = dialog.findViewById<Spinner>(R.id.departmentSpinner)!!
        val ratingBar = dialog.findViewById<RatingBar>(R.id.ratingBar)!!
        val contentEdit = dialog.findViewById<EditText>(R.id.reviewContent)!!
        val submitButton = dialog.findViewById<Button>(R.id.submitButton)!!
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)!!

        // 진료과 스피너 설정
        departmentSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            allDepartments
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        submitButton.setOnClickListener {
            val selectedDepartment = departmentSpinner.selectedItem.toString()
            val rating = ratingBar.rating
            val content = contentEdit.text.toString()

            when {
                selectedDepartment == "진료과 선택" -> {
                    Toast.makeText(context, "진료과를 선택해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                rating == 0f -> {
                    Toast.makeText(context, "별점을 선택해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                content.isBlank() -> {
                    Toast.makeText(context, "리뷰 내용을 입력해주세요", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                else -> {
                    viewModel.addReview(
                        hospitalId = hospitalId,
                        rating = rating,
                        content = content,
                        department = selectedDepartment
                    )
                    dialog.dismiss()
                }
            }
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