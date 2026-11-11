package com.example.fangkemeng.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fangkemeng.model.*
import com.example.fangkemeng.ui.PokeButton
import com.example.fangkemeng.ui.PokeColors
import com.pico.spatial.ui.design.Text

private enum class RouteModal { NONE, SUPPLIES, ABANDON }

/** 挑战路线拥有本局信息和战后奖励；聚焦节点不触发 Stage。 */
@Composable
internal fun JourneyHomeContent(
    profile: PlayerProfile, stateRevision: Int, onEnterBattle: (MapNode) -> Unit,
    onClaimReward: (RewardChoice, String?) -> Unit, onConfirmAbandon: () -> Unit,
    onPurchaseSupply: (ShopEntry) -> Unit, onReturnToCamp: () -> Unit,
) {
    var modal by remember { mutableStateOf(RouteModal.NONE) }
    val run = remember(stateRevision, profile.run) { profile.run }
    var focusedNode by remember(run.id, run.path, run.selectedNode, run.pendingRewardNode) {
        mutableStateOf(run.selectedNode ?: run.availableNodes.singleOrNull()?.index)
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    CampPage("挑战路线", "本局金币", run.gold) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            profile.party.forEachIndexed { index, creature ->
                val hp = run.battle?.player?.getOrNull(index)?.hp ?: run.hp[creature] ?: 0
                val max = CreatureGrowth.maxHp(profile.levelOf(creature))
                Column(Modifier.weight(1f)) {
                    Text("${DemoData.speciesOf(creature.name)?.name}  $hp/$max",
                        color = if (hp == 0) PokeColors.BrandRed else PokeColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().height(6.dp).background(PokeColors.PaperDeep)) {
                        Box(Modifier.fillMaxWidth(hp.toFloat() / max).height(6.dp).background(PokeColors.Mint))
                    }
                }
            }
        }
        Text("伤药 ${profile.potions} · 复活符 ${profile.revives} · ${run.relics.mapNotNull(Relic::of).joinToString(" / ") { it.displayName }.ifEmpty { "遗物 0/3" }}",
            color = PokeColors.TextMid, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))
        MapColumn(run, Modifier.fillMaxWidth(), focusedNode) { focusedNode = it.index }
        Spacer(Modifier.weight(1f))
        val node = focusedNode?.let(run::node)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(node?.let { "第 ${it.depth} 层 · ${it.type.displayName}" } ?: "选择下一场挑战",
                    color = PokeColors.BrandRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (node != null) Text("${node.subtitle} · 金币 +${node.rewardGold}", color = PokeColors.TextMid, fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (run.shopAvailable) PokeButton({ modal = RouteModal.SUPPLIES }, Modifier.height(56.dp)) {
                Text("补给站", fontSize = 18.sp)
            }
            PokeButton({ node?.let(onEnterBattle) }, Modifier.width(224.dp).height(56.dp), selected = true,
                enabled = run.active && run.pendingRewardNode == null && node != null) {
                Text(if (run.battle != null) "继续第 ${node?.depth ?: run.layer} 层战斗" else "进入第 ${node?.depth ?: run.layer} 层", fontSize = 19.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PokeButton(onReturnToCamp, Modifier.height(48.dp)) { Text("返回营地", fontSize = 18.sp) }
            Text("保留本局，稍后继续", color = PokeColors.TextMid, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            PokeButton({ modal = RouteModal.ABANDON }, Modifier.height(48.dp)) { Text("放弃本局", fontSize = 16.sp) }
        }
    }
    // 主窗口实际可见时才弹奖励；Stage 胜利反馈保留到玩家明确离开。
    if (run.pendingRewardNode != null && lifecycle.isAtLeast(Lifecycle.State.RESUMED)) {
        SupplySheet(run, run.gold, onClaimReward, rewardGold = run.node(run.pendingRewardNode)?.rewardGold ?: 0)
    } else when (modal) {
        RouteModal.SUPPLIES -> ShopSheet(CampaignMap.supplyStock(profile), run.gold, onPurchaseSupply,
            onClose = { modal = RouteModal.NONE }, title = "补给站",
            inventory = "伤药 ${profile.potions} · 复活符 ${profile.revives} · 遗物 ${run.relics.size}/3")
        RouteModal.ABANDON -> SheetFrame("放弃本局？", onClose = { modal = RouteModal.NONE }, closeLabel = "继续挑战") {
            Text("本局将结束，带回 ${run.gold} 金币。\n未完成的战斗不计胜利。", color = PokeColors.Ink,
                fontSize = 20.sp, lineHeight = 30.sp)
            PokeButton(onConfirmAbandon, Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp), selected = true) {
                Text("确认放弃", fontSize = 20.sp)
            }
        }
        RouteModal.NONE -> Unit
    }
}
