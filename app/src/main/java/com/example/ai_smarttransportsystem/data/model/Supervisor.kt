package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Supervisor(
    @get:PropertyName("assigned_bus")
    @set:PropertyName("assigned_bus")
    var assignedBus: String? = null,

    @get:PropertyName("assigned_route")
    @set:PropertyName("assigned_route")
    var assignedRoute: String? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now()
)
