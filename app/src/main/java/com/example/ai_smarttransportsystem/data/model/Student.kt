package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.Exclude

data class Student(
    @get:Exclude
    var uid: String? = null,

    var name: String? = null,
    var email: String? = null,

    @get:PropertyName("roll_no")
    @set:PropertyName("roll_no")
    var rollNo: String? = null,

    var semester: String? = null,

    @get:PropertyName("seat_number")
    @set:PropertyName("seat_number")
    var seatNumber: String? = null,

    @get:PropertyName("registration_status")
    @set:PropertyName("registration_status")
    var registrationStatus: String? = "pending",

    @get:PropertyName("fee_status")
    @set:PropertyName("fee_status")
    var feeStatus: String? = "unpaid",

    @get:PropertyName("route_id")
    @set:PropertyName("route_id")
    var routeId: String? = null,

    @get:PropertyName("bus_id")
    @set:PropertyName("bus_id")
    var busId: String? = null,

    @get:PropertyName("stop_id")
    @set:PropertyName("stop_id")
    var stopId: String? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now()
)
