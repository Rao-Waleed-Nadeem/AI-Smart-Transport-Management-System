package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Fee(
    var semester: String? = null,
    var amount: Double? = null,

    @get:PropertyName("payment_status")
    @set:PropertyName("payment_status")
    var paymentStatus: String? = "unpaid",

    @get:PropertyName("route_distance")
    @set:PropertyName("route_distance")
    var routeDistance: Double? = null,

    @get:PropertyName("student_id")
    @set:PropertyName("student_id")
    var studentId: String? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now(),

    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    var updatedAt: Timestamp = Timestamp.now()
)
