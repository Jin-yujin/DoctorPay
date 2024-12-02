package com.project.doctorpay.ui.reviews

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.project.doctorpay.R
import com.project.doctorpay.databinding.ItemReviewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter(private val showHospitalName: Boolean = false) : ListAdapter<Review, ReviewAdapter.ReviewViewHolder>(ReviewDiffCallback()) {
    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private var actionListener: ReviewActionListener? = null
    private var originalList = listOf<Review>()
    private var currentFilter = ReviewFilter()

    // 수정/삭제 이벤트 처리를 위한 인터페이스
    interface ReviewActionListener {
        fun onEditReview(review: Review)
        fun onDeleteReview(review: Review)
    }

    fun setActionListener(listener: ReviewActionListener) {
        actionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding, currentUserId, actionListener, showHospitalName)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = getItem(position)
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
        originalList = newList
        applyFilter(currentFilter)
    }

    fun applyFilter(filter: ReviewFilter) {
        currentFilter = filter
        val filteredList = originalList.filter { review ->
            val departmentMatch = filter.department == "전체" || review.department == filter.department
            val ratingMatch = review.rating.toInt() in filter.ratingRange
            departmentMatch && ratingMatch
        }
        submitList(filteredList)
    }

    class ReviewViewHolder(
        private val binding: ItemReviewBinding,
        private val currentUserId: String?,
        private val actionListener: ReviewActionListener?,
        private val showHospitalName: Boolean
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(review: Review) {
            binding.apply {
                tvReviewerName.text = review.userName
                tvHospitalName.text = review.hospitalName
                tvDepartment.text = review.department
                ratingBar.rating = review.rating
                tvReviewContent.text = review.content
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                tvReviewDate.text = dateFormat.format(Date(review.timestamp))
                // 병원 이름 표시 여부 설정
                tvHospitalName.visibility = if (showHospitalName) View.VISIBLE else View.GONE
                if (showHospitalName) {
                    tvHospitalName.text = review.hospitalName
                }

                // 사용자 정보 가져오기 및 표시
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(review.userId)
                    .get()
                    .addOnSuccessListener { document ->
                        val gender = document.getString("gender") ?: ""
                        val age = document.getString("age") ?: ""
                        // 성별과 나이대 정보를 조합
                        val userInfo = if (gender.isNotEmpty() && age.isNotEmpty()) {
                            "($gender • $age)"
                        } else {
                            ""
                        }
                        tvUserInfo.text = userInfo
                    }
                    .addOnFailureListener {
                        tvUserInfo.text = ""
                    }

                // 본인 리뷰일 때만 메뉴 표시
                val isCurrentUserReview = review.userId == currentUserId
                reviewMenu.visibility = if (isCurrentUserReview) View.VISIBLE else View.GONE

                // 팝업 메뉴 설정
                reviewMenu.setOnClickListener { view ->
                    if (isCurrentUserReview) {
                        showPopupMenu(view, review)
                    }
                }
            }
        }

        private fun showPopupMenu(view: View, review: Review) {
            val popup = PopupMenu(view.context, view)
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.review_menu, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.ic_edit -> {  // review_menu.xml의 ID와 일치하도록 수정
                        actionListener?.onEditReview(review)
                        true
                    }
                    R.id.ic_delete -> {  // review_menu.xml의 ID와 일치하도록 수정
                        actionListener?.onDeleteReview(review)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
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
    val hospitalName: String = "",
    val userId: String = "",
    val userName: String = "",
    val userNickname: String = "",
    val rating: Float = 0f,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isVerifiedVisit: Boolean = false,
    val department: String = ""
)