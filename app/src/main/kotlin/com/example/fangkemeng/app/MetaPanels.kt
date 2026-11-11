package com.example.fangkemeng.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fangkemeng.model.CampaignMap
import com.example.fangkemeng.model.CreatureId
import com.example.fangkemeng.model.CreatureGrowth
import com.example.fangkemeng.model.DemoData
import com.example.fangkemeng.model.MapNode
import com.example.fangkemeng.model.PlayerProfile
import com.example.fangkemeng.model.PreparationDraft
import com.example.fangkemeng.model.Relic
import com.example.fangkemeng.model.RunState
import com.example.fangkemeng.model.ShopEntry
import com.example.fangkemeng.model.RewardChoice
import com.example.fangkemeng.ui.PokeColors
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Option
import com.pico.spatial.ui.design.OptionDefaults
import com.pico.spatial.ui.design.Checkbox
import com.pico.spatial.ui.design.CheckBoxDefaults
import com.pico.spatial.ui.design.windows.BasicAlertDialog
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.example.fangkemeng.ui.PokeButton

/** 战斗层从左向右推进，分叉之间沿已选路线前进，汇合后再选择。 */
@Composable
fun MapColumn(
    run: RunState,
    modifier: Modifier = Modifier,
    focusedNode: Int? = null,
    onEnter: (MapNode) -> Unit,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("挑战路线", color = PokeColors.TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${run.completedCount} / ${CampaignMap.DEPTHS} 层",
                color = PokeColors.TextLo,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            (1..CampaignMap.DEPTHS).forEach { Text("第 $it 层", Modifier.weight(1f),
                color = PokeColors.TextMid, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val graphHeight = 304.dp
        BoxWithConstraints(Modifier.fillMaxWidth().height(graphHeight)) {
            val cell = maxWidth / CampaignMap.DEPTHS
            val legal = run.availableNodes.map { it.index }.toSet()
            fun point(node: MapNode, width: Float, height: Float) =
                Offset(width * (node.depth - .5f) / CampaignMap.DEPTHS, height * (.5f + node.lane * .31f))
            Canvas(Modifier.fillMaxSize()) {
                run.nodes.forEach { node -> node.next.mapNotNull(run::node).forEach { next ->
                    val a = point(node, size.width, size.height)
                    val b = point(next, size.width, size.height)
                    val visited = run.path.zipWithNext().any { it.first == node.index && it.second == next.index }
                    val color = if (visited) PokeColors.Mint else if (node.index == run.path.lastOrNull() && next.index in legal)
                        PokeColors.Gold else PokeColors.TextLo.copy(alpha = .28f)
                    drawLine(color, a, b, 2.5.dp.toPx())
                    val direction = (b - a) / (b - a).getDistance()
                    val tip = b - direction * 38.dp.toPx()
                    val normal = Offset(-direction.y, direction.x)
                    drawLine(color, tip, tip - direction * 8.dp.toPx() + normal * 4.dp.toPx(), 2.dp.toPx())
                    drawLine(color, tip, tip - direction * 8.dp.toPx() - normal * 4.dp.toPx(), 2.dp.toPx())
                } }
            }
            run.nodes.forEach { node ->
                val done = node.index in run.path
                val now = node.index == focusedNode || node.index == run.selectedNode
                val color = if (now) PokeColors.BrandRed else if (done) PokeColors.Mint else if (node.index in legal) PokeColors.Gold else PokeColors.TextLo
                Column(Modifier.offset(cell * (node.depth - .5f) - 40.dp, graphHeight * (.5f + node.lane * .31f) - 36.dp)
                    .width(80.dp).height(86.dp).spatialHoverEffect()
                    .clickable(enabled = node.index in legal) { onEnter(node) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(color, 27.dp.toPx())
                            if (now) drawCircle(PokeColors.Gold, 31.dp.toPx(), style = Stroke(2.dp.toPx()))
                        }
                        Text(when (node.type) {
                            com.example.fangkemeng.model.NodeType.BATTLE -> "战"
                            com.example.fangkemeng.model.NodeType.ELITE -> "精"
                            com.example.fangkemeng.model.NodeType.BOSS -> "王"
                        }, color = if (node.index in legal || now) PokeColors.Ink else PokeColors.Paper,
                            fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (done) "已胜" else node.type.displayName, color = color, fontSize = 14.sp)
                }
            }
        }
    }
}

/** 路线内战后奖励：三选一。 */
@Composable
fun SupplySheet(
    run: RunState,
    gold: Int,
    onPick: (RewardChoice, String?) -> Unit,
    rewardGold: Int = 0,
    unlockedText: String = "",
    asAttachment: Boolean = false,
) {
    var replacement by remember(run.id, run.pendingRewardNode) { mutableStateOf<RewardChoice?>(null) }
    val narrow = LocalConfiguration.current.screenWidthDp < 800
    SheetFrame(title = if (replacement != null) "替换遗物" else if (run.completedCount == CampaignMap.DEPTHS) "首领战后奖励 · 三选一" else "战后奖励 · 三选一",
        width = if (narrow) 704.dp else 720.dp, onClose = null, gold = gold, asAttachment = asAttachment) {
        run.pendingRewardNode?.let(run::node)?.let {
            Text("第 ${it.depth} 层 · ${it.title}", color = PokeColors.TextMid, fontSize = 16.sp)
        }
        Text("金币 +$rewardGold",
            color = PokeColors.TextHi, fontSize = 18.sp)
        if (unlockedText.isNotBlank()) {
            Text("解锁 $unlockedText",
                color = PokeColors.TextHi, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(20.dp))
        val options = replacement?.let { selected -> run.relics.mapNotNull(Relic::of).map {
            RewardChoice(it.id, it.displayName, it.description, selected.relicId)
        } } ?: run.rewardChoices
        val item: @Composable (RewardChoice, Modifier) -> Unit = { option, modifier ->
            PokeButton(onClick = {
                val selected = replacement
                if (selected != null) onPick(selected, option.id)
                else if (option.relicId != null && run.relics.size == Relic.MAX_EQUIPPED) replacement = option
                else onPick(option, null)
            }, modifier = modifier) {
                Column(Modifier.fillMaxWidth()) {
                    Text(option.name,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(option.description,
                        fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { item(it, Modifier.fillMaxWidth().height(112.dp)) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { item(it, Modifier.weight(1f).height(176.dp)) }
        }
        if (replacement != null) PokeButton({ replacement = null }, Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp)) {
            Text("返回", fontSize = 18.sp)
        }
    }
}

/** 已拥有伙伴训练；价格与数值直接来自战斗成长模型。 */
@Composable
fun TrainingSheet(
    profile: PlayerProfile,
    stateRevision: Int,
    initial: CreatureId,
    onTrain: (CreatureId, Int) -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    val level = remember(selected, stateRevision) { profile.levelOf(selected) }
    val price = CreatureGrowth.trainingCost(level)
    val species = DemoData.speciesOf(selected.name)
    SheetFrame("训练", width = 720.dp, gold = profile.gold, onClose = onClose, closeLabel = "返回") {
        Row(Modifier.fillMaxWidth().height(360.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(256.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoData.availableSpecies().filter { profile.isUnlocked(it.creatureId) }.forEach { mon ->
                    Option(selected = selected == mon.creatureId, onSelectChange = { if (it) selected = mon.creatureId },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = OptionDefaults.optionColors(
                            checkedContainerColor = PokeColors.Ink, checkedContentColor = PokeColors.Gold,
                            unCheckedContainerColor = PokeColors.PaperDeep, unCheckedContentColor = PokeColors.Ink,
                        )) { Text("${mon.name}  Lv.${profile.levelOf(mon.creatureId)}", fontSize = 16.sp) }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(species?.name ?: "", color = PokeColors.TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (price == null) "Lv.$level · 满级" else "Lv.$level → Lv.${level + 1}",
                    color = PokeColors.BrandRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (price != null) {
                    Text("生命 ${CreatureGrowth.maxHp(level)} → ${CreatureGrowth.maxHp(level + 1)}",
                        color = PokeColors.TextHi, fontSize = 20.sp)
                    val percent = (CreatureGrowth.damageMultiplier(level + 1) / CreatureGrowth.damageMultiplier(level) - 1f) * 100
                    Text("伤害 +%.1f%%".format(percent), color = PokeColors.TextHi, fontSize = 20.sp)
                    if (CreatureGrowth.speed(level + 1) != CreatureGrowth.speed(level)) {
                        Text("速度 ${CreatureGrowth.speed(level)} → ${CreatureGrowth.speed(level + 1)}", color = PokeColors.TextHi, fontSize = 20.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (profile.gold < price) Text("还差 ${price - profile.gold} 金币", color = PokeColors.BrandRed, fontSize = 16.sp)
                    PokeButton({ onTrain(selected, level) }, Modifier.fillMaxWidth().height(56.dp),
                        selected = true, enabled = profile.gold >= price) { Text("$price 金币 · 升级", fontSize = 18.sp) }
                }
            }
        }
    }
}

/** 商店弹层。 */
@Composable
fun ShopSheet(
    stock: List<ShopEntry>,
    gold: Int,
    onBuy: (ShopEntry) -> Unit,
    onClose: () -> Unit,
    title: String = "招募",
    inventory: String = "",
    feedback: String = "",
    asAttachment: Boolean = false,
) {
    SheetFrame(title = "$title · ${stock.size}", width = 720.dp, gold = gold, onClose = onClose,
        closeLabel = "返回", asAttachment = asAttachment) {
        if (inventory.isNotBlank()) Text(inventory, color = PokeColors.TextHi, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
        Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            stock.forEach { entry ->
                val affordable = gold >= entry.price && entry.available
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(entry.name, color = PokeColors.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(entry.description, color = PokeColors.TextMid, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 4.dp))
                        if (entry.available && !affordable) Text("还差 ${entry.price - gold} 金币",
                            color = PokeColors.BrandRed, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    PokeButton({ onBuy(entry) }, Modifier.width(152.dp).height(56.dp), selected = true, enabled = affordable) {
                        Text(if (!entry.available) entry.unavailableReason else "${entry.price} 金币", fontSize = 16.sp)
                    }
                }
            }
        }
        if (feedback.isNotBlank()) Text(feedback, color = PokeColors.BrandRed, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

/** 配队弹层：左侧已解锁精灵，右侧招式池勾选 3 个。 */
@Composable
fun PartySheet(
    profile: PlayerProfile,
    onConfirm: (List<CreatureId>, Map<CreatureId, List<String>>) -> Unit,
    onClose: () -> Unit,
    initialParty: List<CreatureId> = profile.party,
    initialLoadouts: Map<CreatureId, List<String>> = profile.loadouts,
) {
    var focused by remember { mutableStateOf(initialParty.first()) }
    val draft = remember(profile) { PreparationDraft(profile).apply {
        party.clear(); party.addAll(initialParty)
        loadouts.putAll(initialLoadouts.mapValues { it.value.toList() })
    } }
    var draftRevision by remember { mutableStateOf(0) }
    val issue = remember(draftRevision) { draft.issue }
    val picked = remember(focused, draftRevision) { draft.moves(focused) }
    SheetFrame(title = "编队", width = 720.dp, onClose = onClose, actions = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PokeButton(onClose, Modifier.weight(1f).height(56.dp)) { Text("取消", fontSize = 20.sp) }
            PokeButton({
                if (draft.issue == null) onConfirm(draft.party.toList(), draft.loadouts.mapValues { it.value.toList() })
            }, Modifier.weight(1f).height(56.dp), selected = true, enabled = issue == null) {
                Text("保存", fontSize = 20.sp)
            }
        }
    }) {
        Text("队伍 ${draft.party.size}/3",
            color = PokeColors.TextHi, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(272.dp).height(366.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DemoData.availableSpecies().filter { it.creatureId in profile.unlocked }.forEach { species ->
                    val inParty = species.creatureId in draft.party
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Option(selected = species.creatureId == focused, onSelectChange = { focused = species.creatureId },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = OptionDefaults.optionColors(
                                checkedContainerColor = PokeColors.Ink, checkedContentColor = PokeColors.Gold,
                                unCheckedContainerColor = PokeColors.PaperDeep, unCheckedContentColor = PokeColors.Ink,
                            ), contentPadding = PaddingValues(10.dp)) {
                            Text(species.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        PokeButton({
                            if (draft.togglePartner(species.creatureId)) draftRevision++
                        }, Modifier.width(88.dp).height(56.dp),
                            enabled = if (inParty) draft.party.size > 1 else draft.party.size < 3) {
                            Text(if (inParty) "移出" else "加入", fontSize = 16.sp)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).height(366.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("招式 ${picked.size}/3",
                    color = PokeColors.TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                DemoData.movesFor(focused).forEach { move ->
                    val on = move.name in picked
                    Option(selected = on, enabled = on || picked.size < 3,
                        onSelectChange = {
                            if (draft.toggleMove(focused, move.name)) draftRevision++
                        }, modifier = Modifier.fillMaxWidth().height(60.dp),
                        icon = {
                            Checkbox(checked = on, onCheckedChange = null,
                                colors = CheckBoxDefaults.checkboxColors(
                                    backgroundColor = if (on) PokeColors.Ink else PokeColors.PaperDeep,
                                    contentColor = if (on) PokeColors.Gold else PokeColors.Ink,
                                    borderColor = if (on) PokeColors.Gold else PokeColors.Ink,
                                ))
                        },
                        colors = OptionDefaults.optionColors(
                            checkedContainerColor = PokeColors.Ink, checkedContentColor = PokeColors.Gold,
                            unCheckedContainerColor = PokeColors.PaperDeep, unCheckedContentColor = PokeColors.Ink,
                        ), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Column {
                            Text(move.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${move.element.displayName} · 威力 ${move.power} · 能量 ${move.cost}", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        if (issue != null && picked.size == PreparationDraft.MOVE_COUNT) Text(issue, color = PokeColors.BrandRed,
            fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

/** 弹层统一外框：遮罩 + 纸面卡 + 红色硬投影。 */
@Composable
fun SheetFrame(
    title: String,
    width: androidx.compose.ui.unit.Dp = 520.dp,
    gold: Int? = null,
    onClose: (() -> Unit)?,
    closeLabel: String = "关闭",
    asAttachment: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val frame: @Composable () -> Unit = {
                // design-style: design-surface sheet-surface
                Column(Modifier.width(width).background(PokeColors.Paper).padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = PokeColors.TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (gold != null) {
                            Text("金币 $gold", color = PokeColors.TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    content()
                    if (actions != null) {
                        Spacer(Modifier.height(16.dp))
                        actions()
                    } else if (onClose != null) {
                        Spacer(Modifier.height(12.dp))
                        PokeButton(onClose, Modifier.fillMaxWidth().height(56.dp)) { Text(closeLabel, fontSize = 18.sp) }
                    }
                }
    }
    // SDK 6.2.6 SpatialDialogDelegate 只处理 isWindowContainer；Stage 分支不渲染。
    // Stage 已有 AttachmentPanel 负责居中、跟头和交互，复用同一内容面。
    if (asAttachment) frame()
    else BasicAlertDialog(
        onDismissRequest = { onClose?.invoke() },
        properties = DialogProperties(dismissOnBackPress = onClose != null, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) { frame() }
}
