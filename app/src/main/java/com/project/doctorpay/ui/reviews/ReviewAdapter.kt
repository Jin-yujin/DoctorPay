package com.project.doctorpay.ui.reviews

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.project.doctorpay.databinding.ItemReviewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter : ListAdapter<Review, ReviewAdapter.ReviewViewHolder>(ReviewDiffCallback()) {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = getItem(position)
        // 사용자 정보 가져오기
        db.collection("users")
            .document(review.userId)
            .get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "익명"
                holder.bind(review.copy(userName = nickname))
            }
            .addOnFailureListener {
                holder.bind(review)
            }
    }

    // 리스트 업데이트 함수
    fun updateList(newList: List<Review>) {
        submitList(null)  // 기존 리스트 초기화
        submitList(newList.toList())  // 새 리스트 설정
    }

    class ReviewViewHolder(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(review: Review) {
            binding.apply {
                tvReviewerName.text = review.userName
                tvReviewContent.text = review.content
                ratingBar.rating = review.rating
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                tvReviewDate.text = dateFormat.format(Date(review.timestamp))
            }
        }
    }

    private class ReviewDiffCallback : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Review, newItem: Review): Boolean {
            return oldItem == newItem
        }
    }

}

data class Review(
    val id: String = "",
    val hospitalId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userNickname: String = "",
    val rating: Float = 0f,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isVerifiedVisit: Boolean = false
)