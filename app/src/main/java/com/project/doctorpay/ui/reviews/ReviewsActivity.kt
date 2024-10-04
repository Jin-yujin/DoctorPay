package com.project.doctorpay.ui.reviews

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ActivityReviewsBinding

class ReviewsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReviewsBinding
    private lateinit var reviewAdapter: ReviewAdapter
    private var currentPage = 1
    private val pageSize = 20
    private var isLoading = false
    private lateinit var hospitalName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        hospitalName = intent.getStringExtra("HOSPITAL_NAME") ?: "Unknown Hospital"

        binding.toolbar.title = "$hospitalName 리뷰"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        setupRecyclerView()
        loadReviews()
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter()
        binding.recyclerViewReviews.apply {
            layoutManager = LinearLayoutManager(this@ReviewsActivity)
            adapter = reviewAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0 && totalItemCount >= pageSize) {
                        loadReviews()
                    }
                }
            })
        }
    }

    private fun loadReviews() {
        isLoading = true
        // TODO: Replace this with actual API call to fetch reviews
        val newReviews = fetchMockReviews()
        reviewAdapter.addReviews(newReviews)
        currentPage++
        isLoading = false
    }

    private fun fetchMockReviews(): List<Review> {
        return List(10) { index ->
            Review(
                "User ${currentPage * 10 + index}",
                "This is a sample review content for $hospitalName.",
                (1..5).random().toFloat(),
                "2023-05-${(1..30).random().toString().padStart(2, '0')}"
            )
        }
    }
}