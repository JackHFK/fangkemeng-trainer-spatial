package com.example.fangkemeng.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fangkemeng.model.*
import com.example.fangkemeng.ui.PokeColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.StageStyle
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** 营地、准备、路线和结算共用主窗口；只有路线的显式入口打开 Stage。 */
@Composable
fun HomeRoot(navigation: GameNavigationState = GameNavigationState) {
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    val profile = PlayerState.profile
    val revision = PlayerState.revision
    var focused by remember { mutableStateOf(0) }
    PicoTheme {
        HomeContent(
            profile = profile, stateRevision = revision, navigation = navigation, focusedSlot = focused,
            onFocusSlot = { focused = it },
            onConfirmPreparation = { party, loadouts, purpose ->
                if (profile.commitPreparation(party, loadouts)) {
                    focused = 0
                    if (purpose == PreparationPurpose.START_CHALLENGE) {
                        if (profile.run.completed) profile.startNewRun()
                        if (CampaignFlow.beginRun(profile)) {
                            PlayerState.persist()
                            navigation.showDestination(HomeDestination.Route)
                        }
                    } else {
                        PlayerState.persist()
                        navigation.showDestination(HomeDestination.Camp)
                    }
                }
            },
            onEnterBattle = { node ->
                val run = profile.run
                if (run.active && run.pendingRewardNode == null &&
                    (run.selectedNode == node.index || CampaignFlow.selectNextNode(profile, run.id, node.index))) {
                    PlayerState.persist()
                    BattleSessionConfig.currentNode = node
                    BattleSessionConfig.selectedStage = SceneCatalog.stageFor(node.arena)
                    scope.launch {
                        navigator.openStage(id = BATTLE_STAGE_ID, style = StageStyle.Mixed)
                    }
                }
            },
            onClaimReward = { choice, replace ->
                val run = profile.run
                run.pendingRewardNode?.let { node ->
                    if (CampaignFlow.pickReward(profile, run.id, node, choice.id, replace)) {
                        PlayerState.persist()
                        if (profile.run.phase == RunPhase.WON) {
                            navigation.showDestination(HomeDestination.RunResult(RunResultReason.CLEARED))
                        }
                    }
                }
            },
            onConfirmAbandon = {
                if (CampaignFlow.loseRun(profile, profile.run.id)) {
                    PlayerState.persist()
                    navigation.showDestination(HomeDestination.RunResult(RunResultReason.ABANDONED))
                }
            },
            onTrain = { id, level -> if (CampaignFlow.trainPartner(profile, id, level)) PlayerState.persist() },
            onRecruit = { entry -> if (CampaignFlow.recruitPartner(profile, entry.id)) PlayerState.persist() },
            onPurchaseSupply = { entry ->
                if (CampaignFlow.purchaseSupply(profile, profile.run.id, entry.id)) PlayerState.persist()
            },
            partner = { mod, active, id -> PartnerPreviewView(id, active, mod) },
        )
    }
}

/** 普通截图宿主复用这份原生布局。 */
@Composable
fun HomeContent(
    profile: PlayerProfile,
    stateRevision: Int = 0,
    navigation: GameNavigationState = GameNavigationState,
    focusedSlot: Int,
    onFocusSlot: (Int) -> Unit,
    onConfirmPreparation: (List<CreatureId>, Map<CreatureId, List<String>>, PreparationPurpose) -> Unit,
    onEnterBattle: (MapNode) -> Unit,
    onClaimReward: (RewardChoice, String?) -> Unit = { _, _ -> },
    onConfirmAbandon: () -> Unit = {},
    onTrain: (CreatureId, Int) -> Unit = { _, _ -> },
    onRecruit: (ShopEntry) -> Unit = {},
    onPurchaseSupply: (ShopEntry) -> Unit = {},
    partner: @Composable (Modifier, Boolean, CreatureId) -> Unit = { _, _, _ -> },
) {
    when (val destination = navigation.destination) {
        HomeDestination.Camp -> CampHomeContent(profile, stateRevision, focusedSlot, onFocusSlot,
            onChallenge = {
                navigation.showDestination(if (profile.run.active) HomeDestination.Route else
                    HomeDestination.Preparation(PreparationPurpose.START_CHALLENGE))
            },
            onManage = { navigation.showDestination(HomeDestination.Preparation(PreparationPurpose.MANAGE_PARTNERS)) },
            partner = partner)
        is HomeDestination.Preparation -> PreparationContent(profile, stateRevision, destination.purpose,
            onConfirm = onConfirmPreparation, onTrain = onTrain, onRecruit = onRecruit,
            onReturnToCamp = { navigation.showDestination(HomeDestination.Camp) }, partner = partner)
        HomeDestination.Route -> JourneyHomeContent(profile, stateRevision, onEnterBattle,
            onClaimReward, onConfirmAbandon, onPurchaseSupply,
            onReturnToCamp = { navigation.showDestination(HomeDestination.Camp) })
        is HomeDestination.RunResult -> RunResultContent(profile, destination.reason) {
            navigation.showDestination(HomeDestination.Camp)
        }
    }
}

@Composable
internal fun CampPage(title: String, goldLabel: String, gold: Int, content: @Composable ColumnScope.() -> Unit) {
    // design-style: opaque-root — 保留已批准的红金纸面主窗口，Manifest 已关闭系统底材质。
    // design-style: design-surface home-window-surface
    Box(Modifier.fillMaxSize().background(com.example.fangkemeng.ui.PokeColors.Paper)) {
        PosterBackdrop()
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                com.example.fangkemeng.ui.PokeballMark(32.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, color = PokeColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$goldLabel $gold", color = PokeColors.BrandRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
internal fun PosterBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width * .97f
        val cy = size.height * .05f
        val radius = 210.dp.toPx()
        for (row in 0..18) for (col in 0..18) {
            val x = cx - col * 11.dp.toPx()
            val y = cy + row * 11.dp.toPx()
            val distance = hypot(x - cx, y - cy)
            if (distance < radius) drawCircle(PokeColors.Ink.copy(alpha = .13f * (1f - distance / radius)), 1.4f, Offset(x, y))
        }
    }
}

@Composable
internal fun HelpSheet(onClose: () -> Unit) {
    SheetFrame("玩法", width = 640.dp, onClose = onClose, closeLabel = "知道了") {
        val lines = listOf(
            "投球与施法" to "右手握拳抓球，挥手松开投出；张开左手选招，挥右手蓄力，握拳释放。",
            "对战" to "属性克制决定伤害；每回合回复 2 能量，强化蓄力多耗 1 点。换人、道具、援助各占一回合，倒下后换人免费。",
            "伙伴" to "带 1–3 位伙伴，每位选满 3 招。伸手为猫充能，充满可治疗。",
            "营地" to "开局前招募、训练和编队。新伙伴按第三高等级招募，训练最高 20 级。",
            "旅程" to "7 层分支战斗，生命与遗物跨战保留，能量每战恢复。中途用本局金币补给，全队倒下结束，剩余金币带回营地。",
        )
        lines.forEach { (title, body) ->
            Text(title, color = PokeColors.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(body, color = PokeColors.TextHi, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        }
    }
}
