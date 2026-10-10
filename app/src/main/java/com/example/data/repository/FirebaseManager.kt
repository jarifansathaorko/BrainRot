package com.example.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FriendData(
    val uid: String = "",
    val username: String = "Unknown",
    val todayScrolls: Int = 0
)

class FirebaseManager(private val context: Context) {
    private val TAG = "FirebaseManager"
    
    private val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getInstance() != null
        } catch (e: Exception) {
            false
        }
    
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    
    val currentUid: String?
        get() = if (isFirebaseInitialized) auth.currentUser?.uid else null

    suspend fun signInAnonymously() {
        if (!isFirebaseInitialized) return
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
                Log.d(TAG, "Signed in anonymously: ${auth.currentUser?.uid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign in anonymously", e)
        }
    }

    suspend fun updateUsername(username: String) {
        if (!isFirebaseInitialized) return
        val uid = currentUid ?: return
        try {
            db.collection("users").document(uid).set(mapOf("username" to username)).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update username", e)
        }
    }

    suspend fun syncTodayScrolls(scrolls: Int) {
        if (!isFirebaseInitialized) return
        val uid = currentUid ?: return
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        try {
            // Update daily_scrolls subcollection
            db.collection("users").document(uid)
                .collection("daily_scrolls").document(dateString)
                .set(mapOf("count" to scrolls)).await()
                
            // Also update top-level document for quick friend queries
            db.collection("users").document(uid).update("todayScrolls", scrolls).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync scrolls", e)
        }
    }

    suspend fun connectFriend(friendUid: String): Boolean {
        if (!isFirebaseInitialized) return false
        val uid = currentUid ?: return false
        if (uid == friendUid) return false // Can't connect to self
        
        return try {
            // Add friend to current user's friends list
            db.collection("users").document(uid)
                .collection("friends").document(friendUid)
                .set(mapOf("connectedAt" to FieldValue.serverTimestamp())).await()
                
            // Add current user to friend's friends list
            db.collection("users").document(friendUid)
                .collection("friends").document(uid)
                .set(mapOf("connectedAt" to FieldValue.serverTimestamp())).await()
                
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect friend", e)
            false
        }
    }

    suspend fun getFriends(): List<FriendData> {
        if (!isFirebaseInitialized) return emptyList()
        val uid = currentUid ?: return emptyList()
        return try {
            val friendsSnapshot = db.collection("users").document(uid).collection("friends").get().await()
            val friendIds = friendsSnapshot.documents.map { it.id }
            
            val friendList = mutableListOf<FriendData>()
            for (fUid in friendIds) {
                val doc = db.collection("users").document(fUid).get().await()
                if (doc.exists()) {
                    val username = doc.getString("username") ?: "Unknown"
                    val todayScrolls = doc.getLong("todayScrolls")?.toInt() ?: 0
                    friendList.add(FriendData(uid = fUid, username = username, todayScrolls = todayScrolls))
                }
            }
            friendList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get friends", e)
            emptyList()
        }
    }
}
