package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Stop
import kotlinx.coroutines.tasks.await

class StopRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val stopsCollection = firestore.collection("stops")

    // ── Manual mapping — Stop uses @get/@set PropertyName which toObject() misreads ──
    private fun mapDocToStop(doc: DocumentSnapshot): Stop {
        val d = doc.data ?: emptyMap<String, Any>()
        return Stop(
            stopName               = d["stop_name"]                  as? String,
            latitude               = (d["latitude"]                  as? Number)?.toDouble(),
            longitude              = (d["longitude"]                 as? Number)?.toDouble(),
            snapDistanceM          = (d["snap_distance_m"]           as? Number)?.toDouble(),
            distanceToUniversityKm = (d["distance_to_university_km"] as? Number)?.toDouble(),
            feePerStudentPkr       = (d["fee_per_student_pkr"]       as? Number)?.toDouble(),
            studentCount           = (d["student_count"]             as? Number)?.toInt(),
            studentIds             = (d["student_ids"]               as? List<*>)?.filterIsInstance<String>(),
            routeId                = d["route_id"]                   as? String,
            isMerged               = d["is_merged"]                  as? Boolean,
            createdAt              = d["created_at"]                 as? Timestamp,
            updatedAt              = d["updated_at"]                 as? Timestamp
        )
    }

    suspend fun getAllStops(forceRefresh:Boolean=false): Result<List<Stop>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = stopsCollection.get(source).await()
            val stops = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToStop(doc) }.getOrNull()
            }
            Result.success(stops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStopById(forceRefresh:Boolean=false,stopId: String): Result<Stop?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = stopsCollection.document(stopId).get(source).await()
            val stop = if (doc.exists()) runCatching { mapDocToStop(doc) }.getOrNull() else null
            Result.success(stop)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch multiple stops by their document IDs in one round trip using
     * whereIn (max 10 per Firestore limit). For routes with more than 10 stops
     * we batch into chunks. Returns a map of docId → Stop so callers can
     * reorder by optimized_order without re-sorting.
     */
    suspend fun getStopsByIds(forceRefresh:Boolean=false,stopIds: List<String>): Result<Map<String, Stop>> {
        if (stopIds.isEmpty()) return Result.success(emptyMap())
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val result = mutableMapOf<String, Stop>()
            stopIds.chunked(10).forEach { chunk ->
                val snapshot = stopsCollection
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get(source)
                    .await()
                snapshot.documents.forEach { doc ->
                    runCatching { result[doc.id] = mapDocToStop(doc) }
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStopsForRoute(forceRefresh:Boolean=false,routeId: String): Result<List<Stop>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = stopsCollection
                .whereEqualTo("route_id", routeId)
                .get(source)
                .await()
            val stops = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToStop(doc) }.getOrNull()
            }
            Result.success(stops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStopsForStudent(forceRefresh:Boolean=false,studentId: String): Result<List<Stop>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = stopsCollection
                .whereArrayContains("student_ids", studentId)
                .get(source)
                .await()
            val stops = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToStop(doc) }.getOrNull()
            }
            Result.success(stops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStudentAssignment(
        stopId: String,
        studentIds: List<String>,
        studentCount: Int
    ): Result<Unit> {
        return try {
            stopsCollection.document(stopId).update(
                mapOf(
                    "student_ids"  to studentIds,
                    "student_count" to studentCount,
                    "updated_at"   to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}