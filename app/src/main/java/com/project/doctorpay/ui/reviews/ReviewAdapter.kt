package com.project.doctorpay.ui.reviews

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.databinding.ItemReviewBinding

class ReviewAdapter : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {
    private val reviews = mutableListOf<Review>()

    fun addReviews(newReviews: List<Review>) {
        val startPosition = reviews.size
        reviews.addAll(newReviews)
        notifyItemRangeInserted(startPosition, newReviews.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(reviews[position])
    }

    override fun getItemCount() = reviews.size

    class ReviewViewHolder(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(review: Review) {
            binding.tvReviewerName.text = review.userName
            binding.tvReviewContent.text = review.content
            binding.ratingBar.rating = review.rating
            binding.tvReviewDate.text = review.date
        }
    }
}

data class Review(
    val userName: String,
    val content: String,
    val rating: Float,
    val date: String
)