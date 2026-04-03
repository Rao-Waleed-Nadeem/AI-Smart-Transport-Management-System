package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Tracking(
    @get:PropertyName("current_latitude")
    @set:PropertyName("current_latitude")
    var currentLatitude: Double? = null,

    @get:PropertyName("current_longitude")
    @set:PropertyName("current_longitude")
    var currentLongitude: Double? = null,

    var speed: Float? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now()
)
