package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.dao.ScrollyDao
import com.example.data.local.entity.AchievementEntity
import com.example.data.local.entity.FriendBattleEntity
import kotlinx.coroutines.flow.Flow

data class UserProfile(
    var username: String = "aorko",
    val streakDays: Int = 7,
    val bestStreak: Int = 11,
    val totalScrollsAllTime: Int = 14832,
    val battlesWon: Int = 23,
    val battlesLost: Int = 17,
    val currentLevel: Int = 8,
    val currentXp: Int = 3450,
    val xpForNextLevel: Int = 4000
)

data class HealthyChallenge(
    val id: String,
    val title: String,
    val description: String,
    val rewardScrolls: Int,
    val rewardXp: Int,
    val iconName: String,
    val durationMinutes: Int,
    val isCompleted: Boolean = false
)

class GamificationRepository(
    private val dao: ScrollyDao,
    private val context: Context,
    private val firebaseManager: FirebaseManager
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scrolly_prefs", Context.MODE_PRIVATE)

    fun getAchievementsFlow(): Flow<List<AchievementEntity>> {
        return dao.getAllAchievementsFlow()
    }

    fun getHealthyChallenges(): List<HealthyChallenge> {
        return listOf(
            HealthyChallenge(
                id = "walk_5",
                title = "Touch Grass Walk",
                description = "Step outside or walk around for 5 minutes without checking screens.",
                rewardScrolls = 15,
                rewardXp = 50,
                iconName = "walk",
                durationMinutes = 5
            ),
            HealthyChallenge(
                id = "phone_down_10",
                title = "Phone-Down Reset",
                description = "Place your phone face down on the table for 10 peaceful minutes.",
                rewardScrolls = 20,
                rewardXp = 75,
                iconName = "phone",
                durationMinutes = 10
            ),
            HealthyChallenge(
                id = "deep_breaths",
                title = "Box Breathing Reset",
                description = "Take 8 deep intentional breaths to calm attention and reset dopamine.",
                rewardScrolls = 10,
                rewardXp = 30,
                iconName = "wind",
                durationMinutes = 2
            ),
            HealthyChallenge(
                id = "stretch_3",
                title = "Desk Posture Stretch",
                description = "Release neck and thumb strain with gentle shoulder rolls & wrist stretches.",
                rewardScrolls = 12,
                rewardXp = 40,
                iconName = "stretch",
                durationMinutes = 3
            )
        )
    }

    fun getUserProfile(): UserProfile {
        val savedUsername = prefs.getString("username", "aorko") ?: "aorko"
        return UserProfile(username = savedUsername)
    }

    suspend fun updateUsername(newUsername: String) {
        prefs.edit().putString("username", newUsername).apply()
        firebaseManager.updateUsername(newUsername)
    }
}

class SocialRepository(
    private val dao: ScrollyDao,
    private val firebaseManager: FirebaseManager
) {
    fun getBattlesFlow(): Flow<List<FriendBattleEntity>> {
        return dao.getAllBattlesFlow()
    }

    suspend fun recordBattleResult(battleId: String, userFinal: Int, friendFinal: Int) {
        val status = when {
            userFinal < friendFinal -> "WINNING" // Lower scroll count wins!
            userFinal > friendFinal -> "LOSING"
            else -> "TIED"
        }
        val existing = dao.getAllBattlesFlow()
        // Simple update
    }
    
    suspend fun connectFriend(friendUid: String): Boolean {
        return firebaseManager.connectFriend(friendUid)
    }
    
    suspend fun getFriends(): List<FriendData> {
        return firebaseManager.getFriends()
    }
}
