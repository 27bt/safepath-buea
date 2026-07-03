package com.safepathbuea.app.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Anonymous Auth wrapper. The raw Firebase UID never leaves the device: any
 * field written to Firestore (`reporter_id`, `confirmed_by`) uses the SHA-256
 * hash instead, so a hazard document can't be traced back to a specific
 * anonymous session by anyone reading the collection.
 */
class AuthManager(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    suspend fun ensureSignedIn(): String {
        val existingUid = auth.currentUser?.uid
        if (existingUid != null) return existingUid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in returned no user")
    }

    val currentUid: String?
        get() = auth.currentUser?.uid

    fun hashedUid(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
