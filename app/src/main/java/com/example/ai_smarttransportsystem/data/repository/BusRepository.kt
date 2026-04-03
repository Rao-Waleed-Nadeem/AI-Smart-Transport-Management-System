package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Bus
import kotlinx.coroutines.tasks.await

class BusRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val busesCollection = firestore.collection("buses")

    // ── All reads use manual mapping — toObject(Bus::class.java) crashes
    //    because @get:PropertyName/@set:PropertyName on Kotlin data classes
    //    is not reliably picked up by Firestore's Java reflection. ─────────────

    private fun mapDocToBus(doc: DocumentSnapshot): Bus {
        return Bus(
            busNumber    = doc.getString("bus_number"),
            plateNumber  = doc.getString("plate_number"),
            capacity     = (doc.get("capacity") as? Number)?.toInt() ?: 40,
            routeId      = doc.getString("route_id"),
            supervisorId = doc.getString("supervisor_id"),
            status       = doc.getString("status") ?: "active",
            isAvailable  = doc.getBoolean("is_available") ?: true,
            createdAt    = (doc.get("created_at") as? com.google.firebase.Timestamp)
                ?: com.google.firebase.Timestamp.now()
        )
    }

    /** Add a new bus — returns the generated Firestore doc ID */
    suspend fun addBus(bus: Bus): Result<String> {
        return try {
            val docRef = busesCollection.add(bus).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get all buses as plain list (used for dashboard count) */
    suspend fun getAllBuses(): Result<List<Bus>> {
        return try {
            val snapshot = busesCollection.get().await()
            val buses = snapshot.documents.mapNotNull { runCatching { mapDocToBus(it) }.getOrNull() }
            Result.success(buses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get all buses with their Firestore doc IDs — used by Assign Bus screen */
    suspend fun getAllBusesWithIds(forceRefresh:Boolean=false): Result<List<Pair<String, Bus>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = busesCollection.get(source).await()
            val pairs = snapshot.documents.mapNotNull { doc ->
                runCatching { Pair(doc.id, mapDocToBus(doc)) }.getOrNull()
            }
            Result.success(pairs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get a single bus by Firestore document ID */
    suspend fun getBusById(forceRefresh:Boolean=false,busId: String): Result<Bus?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = busesCollection.document(busId).get(source).await()
            val bus = if (doc.exists()) mapDocToBus(doc) else null
            Result.success(bus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get the bus assigned to a specific supervisor */
    suspend fun getBusForSupervisor(forceRefresh:Boolean=false,supervisorId: String): Result<Bus?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = busesCollection
                .whereEqualTo("supervisor_id", supervisorId)
                .limit(1)
                .get(source)
                .await()
            val bus = snapshot.documents.firstOrNull()?.let {
                runCatching { mapDocToBus(it) }.getOrNull()
            }
            Result.success(bus)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Partial update on a bus document */
    suspend fun updateBus(busId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            busesCollection.document(busId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get all assigned buses (is_available = false) with their doc IDs */
    suspend fun getAssignedBusesWithIds(forceRefresh:Boolean=false): Result<List<Pair<String, Bus>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = busesCollection
                .whereEqualTo("is_available", false)
                .get(source)
                .await()
            val pairs = snapshot.documents.mapNotNull { doc ->
                runCatching { Pair(doc.id, mapDocToBus(doc)) }.getOrNull()
            }
            Result.success(pairs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get buses that have a route assigned but NO supervisor yet.
     * Fetches is_available=false buses and filters client-side because
     * Firestore cannot query "field is null".
     * [filterRouteId] — when coming from a specific route card, only return
     * the bus for that route.
     */
    suspend fun getBusesWithRouteNoSupervisor(
        forceRefresh:Boolean=false,
        filterRouteId: String? = null
    ): Result<List<Pair<String, Bus>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = busesCollection
                .whereEqualTo("is_available", false)
                .get(source)
                .await()
            val pairs = snapshot.documents.mapNotNull { doc ->
                runCatching { Pair(doc.id, mapDocToBus(doc)) }.getOrNull()
            }.filter { (_, bus) ->
                !bus.routeId.isNullOrEmpty() &&
                        bus.supervisorId.isNullOrEmpty() &&
                        (filterRouteId == null || bus.routeId == filterRouteId)
            }
            Result.success(pairs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}