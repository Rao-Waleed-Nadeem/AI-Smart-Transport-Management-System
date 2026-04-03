package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Utils
import kotlinx.coroutines.tasks.await
import android.util.Log

class UtilsRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val utilsCollection = firestore.collection("utils")
    private val docId = "BtiDaU8Ljg7heipvL0Xr"

    suspend fun getUtils(): Result<Utils?> {
        return try {
            val doc = utilsCollection.document(docId).get().await()
            if (doc.exists()) {
                val d = doc.data ?: emptyMap<String, Any>()
                val utils = Utils(
                    busAverage = (d["bus_average"] as? Number)?.toDouble(),
                    // Firestore int64 maps to Long in Kotlin
                    daysInMonth = (d["days_in_month"] as? Number)?.toLong(),
                    isOptimized = d["is_optimized"] as? Boolean,
                    petrolPricePerLitre = (d["petrol_price_per_litre"] as? Number)?.toDouble(),
                    profitMarginPercent = (d["profit_margin_percent"] as? Number)?.toDouble(),
                    semesterMonths = (d["semester_months"] as? Number)?.toDouble(),
                    universityLatitude = (d["university_latitude"] as? Number)?.toDouble(),
                    universityLongitude = (d["university_longitude"] as? Number)?.toDouble()
                )
                Result.success(utils)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("UtilsRepository", "Error getting utils: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Mark routes as optimized in the database
     */
    suspend fun routeOptimizedToggle(isOptimized: Boolean): Result<Unit> {
        return try {
            // Using a simple Map for the update to ensure no type/mapping issues
            val updates = mapOf<String, Any>(
                "is_optimized" to isOptimized
            )
            
            utilsCollection.document(docId)
                .update(updates)
                .await()
                
            Log.d("UtilsRepository", "Firestore updated: is_optimized = $isOptimized")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UtilsRepository", "Firestore update failed: ${e.message}")
            Result.failure(e)
        }
    }
}
