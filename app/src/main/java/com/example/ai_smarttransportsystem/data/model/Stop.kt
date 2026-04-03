package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Stop(
    @get:PropertyName("stop_name")
    @set:PropertyName("stop_name")
    var stopName: String? = null,

    var latitude: Double? = null,
    var longitude: Double? = null,

    @get:PropertyName("snap_distance_m")
    @set:PropertyName("snap_distance_m")
    var snapDistanceM: Double? = null,

    @get:PropertyName("distance_to_university_km")
    @set:PropertyName("distance_to_university_km")
    var distanceToUniversityKm: Double? = null,

    @get:PropertyName("fee_per_student_pkr")
    @set:PropertyName("fee_per_student_pkr")
    var feePerStudentPkr: Double? = null,

    @get:PropertyName("student_count")
    @set:PropertyName("student_count")
    var studentCount: Int? = null,

    @get:PropertyName("student_ids")
    @set:PropertyName("student_ids")
    var studentIds: List<String>? = null,

    @get:PropertyName("route_id")
    @set:PropertyName("route_id")
    var routeId: String? = null,

    @get:PropertyName("is_merged")
    @set:PropertyName("is_merged")
    var isMerged: Boolean? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp? = null,

    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    var updatedAt: Timestamp? = null
) {
    companion object {
        fun createManual(
            name: String,
            latitude: Double,
            longitude: Double
        ): Stop = Stop(
            stopName = name.trim().ifEmpty { "Unnamed Stop" },
            latitude = latitude,
            longitude = longitude,
            snapDistanceM = 0.0,
            isMerged = false,
            studentCount = 0,
            studentIds = emptyList(),
            routeId = null,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }
}