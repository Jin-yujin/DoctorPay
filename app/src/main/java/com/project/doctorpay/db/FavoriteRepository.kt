package com.project.doctorpay.db

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import com.project.doctorpay.MyApplication
import kotlinx.coroutines.tasks.await

class FavoriteRepository {
    private val firestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
    }
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FavoriteRepository"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_FAVORITES = "favorites"
    }

    data class FavoriteHospital(
        val hospitalID: String = "",
        val userId: String = "",
        val timestamp: Long = 0
    )

    private fun checkAuthAndGetUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")
    }

    private fun getUserFavoritesCollection(userId: String) =
        firestore.collection(COLLECTION_USERS).document(userId).collection(COLLECTION_FAVORITES)

    private fun handleOfflineOperation(operation: String): Boolean {
        return !isNetworkAvailable().also { offline ->
            if (offline) {
                Log.w(TAG, "Device is offline, $operation operation will be queued")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = MyApplication.getInstance()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                )
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

            handleOfflineOperation("add favorite")

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
            handleOfflineOperation("remove favorite")

            getUserFavoritesCollection(userId)
                .document(ykiho)
                .delete()
                .await()

            Log.d(TAG, "Successfully removed favorite: $ykiho")
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

            docRef.get(Source.CACHE).await().exists().also { exists ->
                if (handleOfflineOperation("check favorite")) {
                    Log.d(TAG, "Using cached favorite status: $exists for $ykiho")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking favorite status", e)
            false
        }
    }

    suspend fun getFavoriteYkihos(): List<String> {
        return try {
            val userId = checkAuthAndGetUserId()
            handleOfflineOperation("get favorites")

            getUserFavoritesCollection(userId)
                .get(Source.CACHE)
                .await()
                .documents
                .mapNotNull { it.toObject(FavoriteHospital::class.java)?.hospitalID }
                .also { list ->
                    Log.d(TAG, "Retrieved ${list.size} favorites for user $userId")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting favorites", e)
            emptyList()
        }
    }
}