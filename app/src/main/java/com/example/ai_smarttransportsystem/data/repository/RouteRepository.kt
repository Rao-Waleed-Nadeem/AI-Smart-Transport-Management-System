package com.example.ai_smarttransportsystem.data.repository

import com.example.ai_smarttransportsystem.data.model.Route
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await

class RouteRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val routesCollection = firestore.collection("routes")

    // ── Read operations ───────────────────────────────────────────────────────

    suspend fun getRouteById(forceRefresh:Boolean=false,routeId: String): Result<Route?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = routesCollection.document(routeId).get(source).await()
            Result.success(if (doc.exists()) mapDocToRoute(doc) else null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllRoutes(forceRefresh:Boolean=false): Result<List<Route>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = routesCollection.get(source).await()
            val routes = snapshot.documents.mapNotNull { doc ->
                runCatching { mapDocToRoute(doc) }.getOrNull()
            }
            Result.success(routes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRouteForBus(forceRefresh:Boolean=false,busId: String): Result<Route?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = routesCollection
                .whereEqualTo("bus_id", busId)
                .limit(1)
                .get(source)
                .await()
            val route = snapshot.documents.firstOrNull()?.let {
                runCatching { mapDocToRoute(it) }.getOrNull()
            }
            Result.success(route)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestRoute(forceRefresh:Boolean=false): Result<Route?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = routesCollection
                .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get(source)
                .await()
            val route = snapshot.documents.firstOrNull()?.let {
                runCatching { mapDocToRoute(it) }.getOrNull()
            }
            Result.success(route)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /** Set bus_id on a route document after the admin assigns a bus. */
    suspend fun assignBusToRoute(routeId: String, busDocId: String): Result<Unit> {
        return try {
            routesCollection.document(routeId).update(
                mapOf(
                    "bus_id"     to busDocId,
                    "updated_at" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun assignSupervisorToRoute(routeId: String, supervisorDocId: String): Result<Unit> {
        return try {
            routesCollection.document(routeId).update(
                mapOf(
                    "supervisor_id"     to supervisorDocId,
                    "updated_at" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun mapDocToRoute(doc: DocumentSnapshot): Route {
        val d = doc.data ?: emptyMap<String, Any>()

        val geometry: List<GeoPoint>? = when (val raw = d["geometry"]) {
            is List<*> -> raw.mapNotNull { item ->
                when (item) {
                    is GeoPoint -> item
                    is Map<*, *> -> {
                        val lat = (item["lat"] as? Number)?.toDouble()
                        val lng = (item["lng"] as? Number)?.toDouble()
                        if (lat != null && lng != null) GeoPoint(lat, lng) else null
                    }
                    else -> null
                }
            }
            else -> null
        }

        return Route(
            docId              = doc.id,
            busId              = d["bus_id"]               as? String,
            supervisorId       = d["supervisor_id"]        as? String,
            routeName          = d["route_name"]           as? String,
            stopIds            = (d["stop_ids"]            as? List<*>)?.filterIsInstance<String>(),
            optimizedOrder     = (d["optimized_order"]     as? List<*>)?.filterIsInstance<String>(),
            geometry           = geometry,
            totalDistanceKm    = (d["total_distance_km"]   as? Number)?.toDouble(),
            fuelCost           = (d["fuel_cost"]           as? Number)?.toDouble(),
            estimatedTimeHours = (d["estimated_time_hours"] as? Number)?.toDouble(),
            numStops           = (d["num_stops"]           as? Number)?.toInt(),
            totalStudents      = (d["total_students"]      as? Number)?.toInt(),
            createdAt          = (d["created_at"]          as? Timestamp) ?: Timestamp.now(),
            updatedAt          = (d["updated_at"]          as? Timestamp) ?: Timestamp.now(),
        )
    }
}
