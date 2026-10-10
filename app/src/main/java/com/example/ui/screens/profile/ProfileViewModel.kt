package com.example.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollyApp
import com.example.data.local.entity.AchievementEntity
import com.example.data.repository.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.data.repository.FriendData

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val gamificationRepo = ScrollyApp.instance.gamificationRepository
    private val socialRepo = ScrollyApp.instance.socialRepository

    private val _userProfile = MutableStateFlow(gamificationRepo.getUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendData>>(emptyList())
    val friends: StateFlow<List<FriendData>> = _friends.asStateFlow()

    val achievements: StateFlow<List<AchievementEntity>> = gamificationRepo.getAchievementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFriends()
    }

    fun updateUsername(newUsername: String) {
        if (newUsername.isBlank()) return
        viewModelScope.launch {
            gamificationRepo.updateUsername(newUsername)
            _userProfile.value = gamificationRepo.getUserProfile()
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            _friends.value = socialRepo.getFriends()
        }
    }
    
    fun getMyUid(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}
