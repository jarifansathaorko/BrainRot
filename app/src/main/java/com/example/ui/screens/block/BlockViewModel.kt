package com.example.ui.screens.block

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollyApp
import com.example.data.local.entity.AppLimitEntity
import com.example.data.repository.HealthyChallenge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlockViewModel(application: Application) : AndroidViewModel(application) {

    private val trackingRepo = ScrollyApp.instance.trackingRepository
    private val gamificationRepo = ScrollyApp.instance.gamificationRepository

    val appLimits: StateFlow<List<AppLimitEntity>> = trackingRepo.getAppLimitsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val challenges: List<HealthyChallenge> = gamificationRepo.getHealthyChallenges()

    fun updateLimit(packageName: String, newLimit: Int, warningThreshold: Int, isEnabled: Boolean) {
        viewModelScope.launch {
            trackingRepo.updateAppLimit(packageName, newLimit, warningThreshold, isEnabled)
        }
    }

    fun completeChallengeAndEarn(packageName: String, bonusScrolls: Int) {
        viewModelScope.launch {
            trackingRepo.unblockAppTemporarily(packageName, bonusScrolls)
        }
    }
}
