package com.example.fangkemeng.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PreparationPurpose { START_CHALLENGE, MANAGE_PARTNERS }
enum class RunResultReason { CLEARED, DEFEATED, ABANDONED }

sealed interface HomeDestination {
    data object Camp : HomeDestination
    data class Preparation(val purpose: PreparationPurpose) : HomeDestination
    data object Route : HomeDestination
    data class RunResult(val reason: RunResultReason) : HomeDestination
}

/** 仅表达当前主窗口页面；挑战、金币和战斗恢复仍由玩家存档负责。 */
object GameNavigationState {
    var destination by mutableStateOf<HomeDestination>(HomeDestination.Camp)
        private set

    fun showDestination(destination: HomeDestination) {
        this.destination = destination
    }

    fun resetForLaunch() = showDestination(HomeDestination.Camp)
}
