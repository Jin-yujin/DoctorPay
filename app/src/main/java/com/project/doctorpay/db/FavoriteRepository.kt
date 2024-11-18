package com.project.doctorpay.db

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FavoriteRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FavoriteRepository"
    }

    // Data class for favorite hospital
    data class FavoriteHospital(
        val hospitalID: String = "",
        val userId: String = "",
        val timestamp: Long = 0
    )

    // Check auth and get user ID
    private fun checkAuthAndGetUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")
    }

    // Get user favorites collection reference
    private fun getUserFavoritesCollection(userId: String) =
        firestore.collection("users").document(userId).collection("favorites")

    suspend fun addFavorite(hospital: HospitalInfo) {
        try {
            val userId = checkAuthAndGetUserId()
            Log.d(TAG, "Adding favorite - Hospital: ${hospital.name}, User: $userId")

            val favorite = FavoriteHospital(
                hospitalID = hospital.ykiho,
                userId = userId,
                timestamp = System.currentTimeMillis()
            )

            getUserFavoritesCollection(userId)
                .document(hospital.ykiho)
                .set(favorite)
                .await()

            Log.d(TAG, "Successfully added favorite for ${hospital.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding favorite for ${hospital.name}", e)
            when (e) {
                is IllegalStateException -> throw e
                else -> throw Exception("Failed to add favorite: ${e.message}")
            }
        }
    }

    suspend fun removeFavorite(ykiho: String) {
        try {
            val userId = checkAuthAndGetUserId()
            getUserFavoritesCollection(userId)
                .document(ykiho)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing favorite", e)
            when (e) {
                is IllegalStateException -> throw e
                else -> throw Exception("Failed to remove favorite: ${e.message}")
            }
        }
    }

    suspend fun isFavorite(ykiho: String): Boolean {
        return try {
            val userId = checkAuthAndGetUserId()
            val docRef = getUserFavoritesCollection(userId).document(ykiho)
            docRef.get().await().exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking favorite status", e)
            false
        }
    }

    suspend fun getFavoriteYkihos(): List<String> {
        return try {
            val userId = checkAuthAndGetUserId()
            getUserFavoritesCollection(userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(FavoriteHospital::class.java)?.hospitalID }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorites", e)
            emptyList()
        }
    }
}