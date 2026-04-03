package com.example.ai_smarttransportsystem.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class User(
    @DocumentId
    var uid: String = "",
    
    var name: String = "",
    var email: String = "",
    var role: String = "",  // "student", "supervisor", "admin"
    var contact: String = "",

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    var createdAt: Timestamp = Timestamp.now()
)
