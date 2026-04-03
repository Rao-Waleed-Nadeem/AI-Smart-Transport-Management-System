package com.example.ai_smarttransportsystem.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.ai_smarttransportsystem.data.model.Supervisor
import com.example.ai_smarttransportsystem.data.model.User
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SupervisorRepository @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val usersCollection = firestore.collection("users")
    private val supervisorsCollection = firestore.collection("supervisors")
    private val busesCollection = firestore.collection("buses")

    private val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * Get supervisor's own data from subcollection
     * Path: users/{uid}/supervisors/{uid}
     */
    suspend fun getCurrentSupervisor(forceRefresh:Boolean=false): Result<Supervisor?> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val doc = supervisorsCollection.document(uid).get(source).await()
            val supervisor = if (doc.exists()) mapDocToSupervisor(doc) else null
            Result.success(supervisor)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasSupervisorData(): Result<Boolean> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val doc = supervisorsCollection.document(uid).get().await()
            Result.success(doc.exists() && doc.getString("assigned_bus") != null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get assigned bus ID for the current supervisor
     */
    suspend fun getAssignedBusId(forceRefresh:Boolean=false): Result<String?> {
        val result = getCurrentSupervisor(forceRefresh)
        return when {
            result.isSuccess -> Result.success(result.getOrNull()?.assignedBus)
            else -> Result.failure(result.exceptionOrNull() ?: Exception("Failed to load supervisor data"))
        }
    }

    /**
     * Get assigned route ID for the current supervisor
     */
    suspend fun getAssignedRouteId(forceRefresh:Boolean=false): Result<String?> {
        val result = getCurrentSupervisor(forceRefresh)
        return when {
            result.isSuccess -> Result.success(result.getOrNull()?.assignedRoute)
            else -> Result.failure(result.exceptionOrNull() ?: Exception("Failed to load supervisor data"))
        }
    }

    // Later we can add:
    // - updateLocation (for GPS tracking)
    // - getToday'sStudents (via collection group or join query later)
    // - saveAttendance

    /**
     * Get all users with role = "supervisor" for the assign supervisor screen.
     * Returns list of Pair(uid, User) so the Activity has both the ID and display data.
     */
    suspend fun getAllSupervisorsFromUsers(forceRefresh:Boolean=false): Result<List<Pair<String, User>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            val snapshot = usersCollection
                .whereEqualTo("role", "supervisor")
                .get(source)
                .await()
            val list = snapshot.documents.mapNotNull { doc ->
                val user = User(
                    uid     = doc.id,
                    name    = doc.getString("name")    ?: "",
                    email   = doc.getString("email")   ?: "",
                    role    = doc.getString("role")    ?: "supervisor",
                    contact = doc.getString("contact") ?: ""
                )
                Pair(doc.id, user)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Write supervisor document and stamp supervisor_id back onto the bus.
     * Supervisor document path: supervisors/{supervisorUid}  (top-level collection)
     * Bus update: buses/{busDocId}.supervisor_id = supervisorUid
     */
    suspend fun assignSupervisorToBus(
        supervisorUid: String,
        busDocId: String,
        routeId: String?
    ): Result<Unit> {
        return try {
            // Doc ID = supervisorUid, so no need to store supervisor_id as a field
            val supervisorData: Map<String, Any?> = mapOf(
                "assigned_bus"   to busDocId,
                "assigned_route" to routeId,
                "created_at"     to Timestamp.now()
            )
            supervisorsCollection.document(supervisorUid).set(supervisorData).await()

            // 2 — Stamp supervisor_id onto the bus document
            busesCollection.document(busDocId)
                .update("supervisor_id", supervisorUid)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapDocToSupervisor(doc: com.google.firebase.firestore.DocumentSnapshot): Supervisor {
        val d = doc.data ?: emptyMap<String, Any>()
        return Supervisor(
            assignedBus   = d["assigned_bus"]   as? String,
            assignedRoute = d["assigned_route"] as? String,
            createdAt     = (d["created_at"]    as? com.google.firebase.Timestamp) ?: com.google.firebase.Timestamp.now()
        )
    }
    /**
     * Returns supervisors who are NOT yet assigned.
     * Logic:
     * 1. Fetch all users with role="supervisor".
     * 2. Fetch all document IDs from the supervisors/ collection
     *    (each doc ID = supervisorUid means they are already assigned).
     * 3. Exclude any user whose UID appears in the supervisors collection.
     */
    suspend fun getAvailableSupervisors(forceRefresh:Boolean=false): Result<List<Pair<String, User>>> {
        return try {
            val source = if (forceRefresh)
                com.google.firebase.firestore.Source.SERVER
            else
                com.google.firebase.firestore.Source.DEFAULT
            // 1. All supervisor users
            val usersSnap = usersCollection
                .whereEqualTo("role", "supervisor")
                .get(source)
                .await()
            val allSupervisors = usersSnap.documents.mapNotNull { doc ->
                val user = User(
                    uid     = doc.id,
                    name    = doc.getString("name")    ?: "",
                    email   = doc.getString("email")   ?: "",
                    role    = "supervisor",
                    contact = doc.getString("contact") ?: ""
                )
                Pair(doc.id, user)
            }

            // 2. Already-assigned supervisor UIDs (doc IDs in supervisors/)
            val assignedSnap = supervisorsCollection.get(source).await()
            val assignedUids = assignedSnap.documents
                .map { it.id }
                .toSet()

            // 3. Return only unassigned ones
            val available = allSupervisors.filter { (uid, _) -> uid !in assignedUids }
            Result.success(available)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}