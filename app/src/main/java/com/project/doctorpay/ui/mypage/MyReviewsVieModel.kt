package com.project.doctorpay.ui.mypage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.doctorpay.ui.reviews.Review

class MyReviewsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _reviews = MutableLiveData<List<Review>>()
    val reviews: LiveData<List<Review>> = _reviews

    private val _reviewStatus = MutableLiveData<ReviewStatus>()
    val reviewStatus: LiveData<ReviewStatus> = _reviewStatus

    sealed class ReviewStatus {
        object Success : ReviewStatus() {
            var isDelete: Boolean = false
        }
        data class Error(val message: String) : ReviewStatus()
        object Loading : ReviewStatus()
    }

    fun loadMyReviews() {
        _reviewStatus.value = ReviewStatus.Loading
        val userId = auth.currentUser?.uid ?: return

        db.collection("reviews")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _reviewStatus.value = ReviewStatus.Error("리뷰를 불러오는데 실패했습니다")
                    return@addSnapshotListener
                }

                val reviewList = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(Review::class.java)
                } ?: emptyList()

                _reviews.value = reviewList
                _reviewStatus.value = ReviewStatus.Success
            }
    }

    fun updateReview(review: Review) {
        _reviewStatus.value = ReviewStatus.Loading
        db.collection("reviews")
            .document(review.id)
            .set(review)
            .addOnSuccessListener {
                ReviewStatus.Success.isDelete = false
                _reviewStatus.value = ReviewStatus.Success
            }
            .addOnFailureListener {
                _reviewStatus.value = ReviewStatus.Error("리뷰 수정에 실패했습니다")
            }
    }

    fun deleteReview(review: Review) {
        _reviewStatus.value = ReviewStatus.Loading
        db.collection("reviews")
            .document(review.id)
            .delete()
            .addOnSuccessListener {
                ReviewStatus.Success.isDelete = true
                _reviewStatus.value = ReviewStatus.Success
            }
            .addOnFailureListener {
                _reviewStatus.value = ReviewStatus.Error("리뷰 삭제에 실패했습니다")
            }
    }
}