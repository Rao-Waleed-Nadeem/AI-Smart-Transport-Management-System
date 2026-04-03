package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Attendance(
    val date: String? = null,                       // format: "2026-03-14"
    @get:PropertyName("bus_id")
    @set:PropertyName("bus_id")
    var busId: String? = null,
    @get:PropertyName("supervisor_id")
    @set:PropertyName("supervisor_id")
    var supervisorId: String? = null,
    val students: Map<String, Boolean>? = emptyMap(),  // studentId → present (true/false)
    val createdAt: Timestamp? = null
)