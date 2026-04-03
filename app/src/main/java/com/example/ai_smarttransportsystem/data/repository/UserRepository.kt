package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject   // optional later with Hilt/Dagger

class UserRepository @Inject constructor() {   // @Inject optional for now

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    // For registration
    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("No UID"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Save user document
    suspend fun saveUser(user: User): Result<Unit> {
        return try {
            usersCollection.document(user.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get user by UID (for role check)
    suspend fun getUser(forceRefresh:Boolean=false,uid: String): Result<User?> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = usersCollection.document(uid).get(source).await()
            val user = if (doc.exists()) mapDocToUser(doc) else null
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUsersByRole(forceRefresh:Boolean=false,role: String): Result<List<User>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = usersCollection.whereEqualTo("role", role).get(source).await()
            val users = snapshot.documents.map { mapDocToUser(it) }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    fun signOut() {
        auth.signOut()
    }

    private fun mapDocToUser(doc: com.google.firebase.firestore.DocumentSnapshot): User {
        val d = doc.data ?: emptyMap<String, Any>()
        return User(
            uid       = doc.id,
            name      = (d["name"]       as? String) ?: "",
            email     = (d["email"]      as? String) ?: "",
            role      = (d["role"]       as? String) ?: "",
            contact   = (d["contact"]    as? String) ?: "",
            createdAt = (d["created_at"] as? com.google.firebase.Timestamp) ?: com.google.firebase.Timestamp.now()
        )
    }
}