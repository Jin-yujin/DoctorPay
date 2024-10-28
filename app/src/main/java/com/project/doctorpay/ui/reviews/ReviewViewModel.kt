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

                val reviewList = snapshot?.toObjects(Review::class.java) ?: emptyList()
                Log.d("ReviewViewModel", "Loaded ${reviewList.size} reviews")
                reviewList.forEach {
                    Log.d("ReviewViewModel", "Review: $it")
                }

                _reviews.value = reviewList

                if (reviewList.isNotEmpty()) {
                    val avg = reviewList.map { it.rating }.average().toFloat()
                    _averageRating.value = avg
                    Log.d("ReviewViewModel", "Average rating: $avg")
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

        val review = Review(
            id = UUID.randomUUID().toString(),
            hospitalId = hospitalId,
            userId = auth.currentUser?.uid ?: "",
            userName = auth.currentUser?.displayName ?: "익명",
            rating = rating,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        // 리뷰를 Firestore의 reviews 컬렉션에 저장
        db.collection("reviews")
            .document(review.id)
            .set(review)
            .addOnSuccessListener {
                // 리뷰 추가 후 즉시 새로운 데이터 로드
                loadReviews(hospitalId)
                _reviewStatus.value = ReviewStatus.Success
            }
            .addOnFailureListener { e ->
                Log.e("ReviewViewModel", "Error adding review", e)
                _reviewStatus.value = ReviewStatus.Error("리뷰 등록에 실패했습니다")
            }
    }

    private fun getUserNickname(): String {
        // TODO: SharedPreferences나 Firestore에서 사용자 닉네임을 가져오는 로직 구현
        return auth.currentUser?.displayName ?: "익명"
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