package com.example.fangkemeng.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.fangkemeng.battle.BattleCommandPanel
import com.example.fangkemeng.battle.BattleController
import com.example.fangkemeng.model.BattleSessionConfig
import com.example.fangkemeng.model.CampaignFlow
import com.example.fangkemeng.model.PlayerProfile
import com.example.fangkemeng.model.PlayerState
import com.example.fangkemeng.model.SceneCatalog
import com.pico.spatial.ui.design.PicoTheme

/** 调试构建专用：复用正式组件核对首页、准备页及退出确认。 */
class UiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "home"
        setContent {
            PicoTheme {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val scale = minOf(maxWidth.value / 1000, maxHeight.value / 800)
                    val configuration = android.content.res.Configuration(LocalConfiguration.current).apply {
                        screenWidthDp = 1000
                    }
                    CompositionLocalProvider(LocalConfiguration provides configuration) {
                        Box(Modifier.requiredSize(1000.dp, 800.dp).graphicsLayer {
                            scaleX = scale; scaleY = scale
                        }, contentAlignment = Alignment.Center) {
                            if (screen == "exit") {
                                val controller = remember {
                                    val profile = PlayerState.profile
                                    if (!profile.run.active) CampaignFlow.beginRun(profile)
                                    val node = requireNotNull(profile.run.currentNode)
                                    BattleSessionConfig.currentNode = node
                                    BattleSessionConfig.selectedStage = SceneCatalog.stageFor(node.arena)
                                    BattleController()
                                }
                                BattleCommandPanel(controller,
                                    onSelectMove = controller::selectMove,
                                    onOpenSwitch = controller::openSwitchMenu,
                                    onSwitchTo = controller::switchMember,
                                    onCloseSwitch = controller::closeSwitchMenu,
                                    onUsePotion = controller::useFirstPotion,
                                    onUseRevive = controller::useRevive,
                                    onAssist = controller::triggerCompanionAssist,
                                    onReturnToMap = { finish() })
                            } else {
                                val profile = remember { PlayerProfile() }
                                var focused by remember { mutableStateOf(0) }
                                LaunchedEffect(screen) {
                                    GameNavigationState.showDestination(when (screen) {
                                        "prepare" -> HomeDestination.Preparation(PreparationPurpose.START_CHALLENGE)
                                        "manage" -> HomeDestination.Preparation(PreparationPurpose.MANAGE_PARTNERS)
                                        "route" -> {
                                            CampaignFlow.beginRun(profile)
                                            HomeDestination.Route
                                        }
                                        "result" -> HomeDestination.RunResult(RunResultReason.ABANDONED)
                                        else -> HomeDestination.Camp
                                    })
                                }
                                HomeContent(profile, focusedSlot = focused,
                                    onFocusSlot = { focused = it },
                                    onConfirmPreparation = { _, _, _ -> },
                                    onEnterBattle = {})
                            }
                        }
                    }
                }
            }
        }
    }
}
