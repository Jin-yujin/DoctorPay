package com.project.doctorpay.db

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FavoriteRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val favoritesCollection = firestore.collection("favorites")

    suspend fun addFavorite(hospitalId: String) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val favoriteDoc = favoritesCollection.document(userId)

        favoriteDoc.set(mapOf(
            "hospitals" to FieldValue.arrayUnion(hospitalId)
        ), SetOptions.merge()).await()
    }

    suspend fun removeFavorite(hospitalId: String) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val favoriteDoc = favoritesCollection.document(userId)

        favoriteDoc.update(
            "hospitals", FieldValue.arrayRemove(hospitalId)
        ).await()
    }

    suspend fun getFavorites(): List<String> {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val favoriteDoc = favoritesCollection.document(userId).get().await()

        return favoriteDoc.get("hospitals") as? List<String> ?: emptyList()
    }
}