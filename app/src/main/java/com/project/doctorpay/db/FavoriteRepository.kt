package com.project.doctorpay.db

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FavoriteRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val favoritesCollection = firestore.collection("favorites")

    companion object {
        private const val TAG = "FavoriteRepository"
    }

    // 인증 상태 확인 및 사용자 ID 반환
    private fun checkAuthAndGetUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("로그인이 필요합니다")
    }

    suspend fun addFavorite(hospital: HospitalInfo) {
        try {
            val userId = checkAuthAndGetUserId()
            Log.d(TAG, "Adding favorite - Hospital: ${hospital.name}, User: $userId")

            val favorite = FavoriteHospital(
                hospitalID = hospital.ykiho,
                userId = userId,
                timestamp = System.currentTimeMillis()
            )

            favoritesCollection
                .document("${userId}_${hospital.ykiho}")
                .set(favorite)
                .await()

            Log.d(TAG, "Successfully added favorite for ${hospital.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding favorite for ${hospital.name}", e)
            when (e) {
                is IllegalStateException -> throw e  // 로그인 관련 에러
                else -> throw Exception("즐겨찾기 추가 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    suspend fun removeFavorite(ykiho: String) {
        try {
            val userId = checkAuthAndGetUserId()
            favoritesCollection
                .document("${userId}_${ykiho}")
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing favorite", e)
            when (e) {
                is IllegalStateException -> throw e
                else -> throw Exception("즐겨찾기 제거 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    suspend fun isFavorite(ykiho: String): Boolean {
        return try {
            val userId = checkAuthAndGetUserId()
            val docRef = favoritesCollection.document("${userId}_${ykiho}")
            docRef.get().await().exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking favorite status", e)
            false  // 에러 발생 시 기본적으로 false 반환
        }
    }

    suspend fun getFavoriteYkihos(): List<String> {
        return try {
            val userId = checkAuthAndGetUserId()
            favoritesCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(FavoriteHospital::class.java)?.hospitalID }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorites", e)
            emptyList()  // 에러 발생 시 빈 리스트 반환
        }
    }
}