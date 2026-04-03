package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.ai_smarttransportsystem.data.model.Tracking
import kotlinx.coroutines.tasks.await

class TrackingRepository {

    private val auth      = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Collection: tracking/{supervisorUid}
    private val trackingCollection = firestore.collection("tracking")

    private val currentUid: String? get() = auth.currentUser?.uid

    // ── Manual mapping — avoids @get/@set PropertyName issues ────────────────

    private fun mapDocToTracking(doc: com.google.firebase.firestore.DocumentSnapshot): Tracking? {
        val d = doc.data ?: return null
        return Tracking(
            currentLatitude  = (d["current_latitude"]  as? Number)?.toDouble(),
            currentLongitude = (d["current_longitude"] as? Number)?.toDouble(),
            speed            = (d["speed"]             as? Number)?.toFloat(),
            createdAt        = (d["created_at"]        as? Timestamp) ?: Timestamp.now()
        )
    }

    // ── Supervisor writes: update own location ─────────────────────────────────
    // Doc ID = supervisorUid (the logged-in user's UID)

    suspend fun updateMyLocation(
        latitude: Double,
        longitude: Double,
        speed: Float? = null
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val data: Map<String, Any?> = mapOf(
                "current_latitude"  to latitude,
                "current_longitude" to longitude,
                "speed"             to speed,
                "supervisor_uid"    to uid,
                "created_at"        to Timestamp.now()
            )
            trackingCollection.document(uid).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Clear location when supervisor stops sharing (set to null so listeners know)
    suspend fun clearMyLocation(): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val data: Map<String, Any?> = mapOf(
                "current_latitude"  to null,
                "current_longitude" to null,
                "speed"             to null,
                "supervisor_uid"    to uid,
                "created_at"        to Timestamp.now()
            )
            trackingCollection.document(uid).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Student/Admin reads: listen to a specific supervisor's location ───────
    // supervisorUid is fetched from the bus → supervisor_id field

    fun listenToSupervisorLocation(
        supervisorUid: String,
        onUpdate: (Tracking?) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        return try {
            trackingCollection.document(supervisorUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { onError(error); return@addSnapshotListener }
                    val tracking = snapshot?.let { mapDocToTracking(it) }
                    onUpdate(tracking)
                }
        } catch (e: Exception) {
            onError(e); null
        }
    }

    // One-time read (fallback)
    suspend fun getLatestLocation(forceRefresh:Boolean=false,supervisorUid: String): Result<Tracking?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = trackingCollection.document(supervisorUid).get(source).await()
            Result.success(mapDocToTracking(doc))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}