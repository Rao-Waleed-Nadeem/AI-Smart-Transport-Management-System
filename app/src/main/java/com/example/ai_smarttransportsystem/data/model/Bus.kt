package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Bus(
    @get:PropertyName("bus_number")
    @set:PropertyName("bus_number")
    var busNumber: String? = null,

    @get:PropertyName("plate_number")
    @set:PropertyName("plate_number")
    var plateNumber: String? = null,

    var capacity: Int? = 40,

    @get:PropertyName("route_id")
    @set:PropertyName("route_id")
    var routeId: String? = null,

    @get:PropertyName("supervisor_id")
    @set:PropertyName("supervisor_id")
    var supervisorId: String? = null,

    var status: String? = "active",

    @get:PropertyName("is_available")
    @set:PropertyName("is_available")
    var isAvailable: Boolean? = true,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now()
)
