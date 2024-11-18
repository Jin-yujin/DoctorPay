package com.project.doctorpay.ui.reviews

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

                val reviewList = snapshot?.documents?.map { document ->
                    document.toObject(Review::class.java)!!
                } ?: emptyList()

                // 리뷰 목록을 임시 저장
                val updatedReviews = reviewList.toMutableList()

                // 사용자 정보를 한 번에 가져오기 위한 고유 사용자 ID 목록
                val uniqueUserIds = reviewList.map { it.userId }.distinct()

                // 모든 사용자 정보를 한 번에 가져오기
                val userTasks = uniqueUserIds.map { userId ->
                    db.collection("users")
                        .document(userId)
                        .get()
                }

                // 모든 사용자 정보 요청이 완료되면 처리
                Tasks.whenAllSuccess<DocumentSnapshot>(userTasks)
                    .addOnSuccessListener { documentSnapshots ->
                        val userMap = documentSnapshots.associate { doc ->
                            doc.id to (doc.getString("nickname") ?: "익명")
                        }

                        // 각 리뷰에 사용자 닉네임 설정
                        updatedReviews.replaceAll { review ->
                            review.copy(userName = userMap[review.userId] ?: "익명")
                        }

                        // 최종 업데이트된 리뷰 목록 설정
                        _reviews.value = updatedReviews

                        // 평균 평점 계산
                        if (updatedReviews.isNotEmpty()) {
                            val avg = updatedReviews.map { it.rating }.average().toFloat()
                            _averageRating.value = avg
                        }
                    }
            }
    }

    fun addReview(hospitalId: String, rating: Float, content: String, department: String) {
        viewModelScope.launch {
            _reviewStatus.value = ReviewStatus.Loading
            try {
                val review = Review(
                    id = UUID.randomUUID().toString(),
                    userId = auth.currentUser?.uid ?: "",
                    hospitalId = hospitalId,
                    content = content,
                    rating = rating,
                    department = department,
                    timestamp = System.currentTimeMillis()
                )

                db.collection("reviews")
                    .document(review.id)
                    .set(review)
                    .await()

                _reviewStatus.value = ReviewStatus.Success
                loadReviews(hospitalId)
            } catch (e: Exception) {
                _reviewStatus.value = ReviewStatus.Error(e.message ?: "리뷰 등록 실패")
            }
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

    fun updateReview(review: Review, newRating: Float, newContent: String, newDepartment: String) {
        _reviewStatus.value = ReviewStatus.Loading
        val updatedReview = review.copy(
            rating = newRating,
            content = newContent,
            department = newDepartment
        )

        db.collection("reviews")
            .document(review.id)
            .set(updatedReview)
            .addOnSuccessListener {
                _reviewStatus.value = ReviewStatus.Success
                loadReviews(review.hospitalId)
            }
            .addOnFailureListener { e ->
                _reviewStatus.value = ReviewStatus.Error(e.message ?: "리뷰 수정 실패")
            }
    }

    fun deleteReview(review: Review) {
        _reviewStatus.value = ReviewStatus.Loading

        db.collection("reviews")
            .document(review.id)
            .delete()
            .addOnSuccessListener {
                _reviewStatus.value = ReviewStatus.Success
                updateHospitalRating(review.hospitalId)
            }
            .addOnFailureListener { e ->
                Log.e("ReviewViewModel", "Error deleting review", e)
                _reviewStatus.value = ReviewStatus.Error("리뷰 삭제에 실패했습니다")
            }
    }
}