package com.project.doctorpay.ui.reviews

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

class ReviewViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _reviews = MutableLiveData<List<Review>>()
    val reviews: LiveData<List<Review>> = _reviews

    private val _averageRating = MutableLiveData<Float>()
    val averageRating: LiveData<Float> = _averageRating

    private val _reviewStatus = MutableLiveData<ReviewStatus>()
    val reviewStatus: LiveData<ReviewStatus> = _reviewStatus

    sealed class ReviewStatus {
        object Success : ReviewStatus()
        data class Error(val message: String) : ReviewStatus()
        object Loading : ReviewStatus()
    }

    fun loadReviews(hospitalId: String) {
        Log.d("ReviewViewModel", "Loading reviews for hospital: $hospitalId")

        db.collection("reviews")
            .whereEqualTo("hospitalId", hospitalId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ReviewViewModel", "Listen failed.", e)
                    return@addSnapshotListener
                }

                val reviewList = snapshot?.documents?.mapNotNull { document ->
                    val review = document.toObject(Review::class.java)
                    review?.userId?.let { userId ->
                        // 각 리뷰에 대해 사용자 정보 가져오기
                        db.collection("users")
                            .document(userId)
                            .get()
                            .addOnSuccessListener { userDoc ->
                                val nickname = userDoc.getString("nickname") ?: "익명"
                                val updatedReview = review.copy(userName = nickname)
                                _reviews.value = _reviews.value?.map {
                                    if (it.id == review.id) updatedReview else it
                                }
                            }
                    }
                    review
                } ?: emptyList()

                _reviews.value = reviewList

                if (reviewList.isNotEmpty()) {
                    val avg = reviewList.map { it.rating }.average().toFloat()
                    _averageRating.value = avg
                }
            }
    }

    fun loadUserReviews() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("reviews")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val reviewList = documents.toObjects(Review::class.java)
                _reviews.value = reviewList
            }
            .addOnFailureListener { e ->
                Log.e("ReviewViewModel", "Error loading user reviews", e)
                _reviewStatus.value = ReviewStatus.Error("리뷰 목록을 불러오는데 실패했습니다")
            }
    }

    fun addReview(hospitalId: String, rating: Float, content: String) {
        _reviewStatus.value = ReviewStatus.Loading
        val userId = auth.currentUser?.uid ?: return

        // 사용자 정보 가져오기
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val nickname = document.getString("nickname") ?: "익명"

                val review = Review(
                    id = UUID.randomUUID().toString(),
                    hospitalId = hospitalId,
                    userId = userId,
                    userName = nickname,
                    rating = rating,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )

                db.collection("reviews")
                    .document(review.id)
                    .set(review)
                    .addOnSuccessListener {
                        // 리뷰가 성공적으로 추가되면 현재 리뷰 목록에 새 리뷰를 추가
                        val currentReviews = _reviews.value?.toMutableList() ?: mutableListOf()
                        currentReviews.add(0, review)  // 최신 리뷰를 맨 앞에 추가
                        _reviews.value = currentReviews

                        // 평균 평점 업데이트
                        val newAvg = currentReviews.map { it.rating }.average().toFloat()
                        _averageRating.value = newAvg

                        updateHospitalRating(hospitalId)
                        _reviewStatus.value = ReviewStatus.Success
                    }
                    .addOnFailureListener { e ->
                        Log.e("ReviewViewModel", "Error adding review", e)
                        _reviewStatus.value = ReviewStatus.Error("리뷰 등록에 실패했습니다")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ReviewViewModel", "Error getting user info", e)
                _reviewStatus.value = ReviewStatus.Error("사용자 정보를 가져오는데 실패했습니다")
            }
    }

    private fun updateHospitalRating(hospitalId: String) {
        db.collection("reviews")
            .whereEqualTo("hospitalId", hospitalId)
            .get()
            .addOnSuccessListener { documents ->
                val reviews = documents.toObjects(Review::class.java)
                if (reviews.isNotEmpty()) {
                    val averageRating = reviews.map { it.rating }.average()
                    db.collection("hospitals")
                        .document(hospitalId)
                        .update("rating", averageRating)
                }
            }
    }
}