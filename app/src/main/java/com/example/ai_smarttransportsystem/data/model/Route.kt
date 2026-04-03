package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.GeoPoint

/**
 * Represents a complete optimized bus route stored in Firestore.
 */
data class Route(

    var docId: String? = null,

    @get:PropertyName("bus_id")
    @set:PropertyName("bus_id")
    var busId: String? = null,

    @get:PropertyName("supervisor_id")
    @set:PropertyName("supervisor_id")
    var supervisorId: String? = null,

    @get:PropertyName("route_name")
    @set:PropertyName("route_name")
    var routeName: String? = null,

    @get:PropertyName("stop_ids")
    @set:PropertyName("stop_ids")
    var stopIds: List<String>? = null,

    @get:PropertyName("optimized_order")
    @set:PropertyName("optimized_order")
    var optimizedOrder: List<String>? = null,

    var geometry: List<GeoPoint>? = null,

    @get:PropertyName("total_distance_km")
    @set:PropertyName("total_distance_km")
    var totalDistanceKm: Double? = null,

    @get:PropertyName("fuel_cost")
    @set:PropertyName("fuel_cost")
    var fuelCost: Double? = null,

    @get:PropertyName("estimated_time_hours")
    @set:PropertyName("estimated_time_hours")
    var estimatedTimeHours: Double? = null,

    @get:PropertyName("num_stops")
    @set:PropertyName("num_stops")
    var numStops: Int? = null,

    @get:PropertyName("total_students")
    @set:PropertyName("total_students")
    var totalStudents: Int? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now(),

    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    var updatedAt: Timestamp = Timestamp.now()
)
