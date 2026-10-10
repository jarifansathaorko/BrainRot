package com.example.ui.screens.battles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollyApp
import com.example.data.local.entity.FriendBattleEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BattlesViewModel(application: Application) : AndroidViewModel(application) {

    private val socialRepo = ScrollyApp.instance.socialRepository
    private val database = ScrollyApp.instance.database

    val battles: StateFlow<List<FriendBattleEntity>> = socialRepo.getBattlesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFriendChallenge(username: String, friendName: String) {
        viewModelScope.launch {
            val newBattle = FriendBattleEntity(
                id = "battle_${System.currentTimeMillis()}",
                friendName = friendName,
                friendUsername = username,
                userScrolls = 36,
                friendScrolls = (50..120).random(),
                date = "Today",
                status = "WINNING"
            )
            database.scrollyDao().upsertBattle(newBattle)
        }
    }
}
