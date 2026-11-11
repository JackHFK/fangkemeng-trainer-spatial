package com.example.fangkemeng.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fangkemeng.model.Element
import com.example.fangkemeng.model.BattleEngine
import com.example.fangkemeng.model.ItemKind
import com.example.fangkemeng.model.Move
import com.example.fangkemeng.model.StatusEffect
import com.example.fangkemeng.ui.HudBar
import com.example.fangkemeng.ui.PokeColors
import com.pico.spatial.ui.design.Text

private val PanelWidth = 480.dp
private val EnemyPlateWidth = 300.dp
private val StatusWidth = 330.dp

/**
 * 头前统一指令面板：只保留「当前伙伴状态 + 招式 + 换伙伴/道具」。
 *
 * 视觉沿用首页的纸面票根语言（米白底、墨线、红金强调、硬偏移投影），
 * 不再使用整块黑框；敌方信息从面板移除，改为擂台上的头顶名牌。
 */
@Composable
fun BattleCommandPanel(
    controller: BattleController,
    onSelectMove: (Move) -> Unit,
    onOpenSwitch: () -> Unit,
    onSwitchTo: (Int) -> Unit,
    onCloseSwitch: () -> Unit,
    onUsePotion: () -> Unit,
    onUseRevive: () -> Unit,
    onAssist: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyShowItems: Boolean = false,
    onReturnToMap: (() -> Unit)? = null,
) {
    // 换人/结算发生时会自动收起道具页，避免回合切换后残留在面板里。
    var itemsOpen by remember(controller.switchMenuVisible, controller.resolving, controller.phase) {
        mutableStateOf(initiallyShowItems)
    }
    val inTurn = controller.phase == GamePhase.BATTLE_TURN

    Box(modifier.width(PanelWidth)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 6.dp)
                .background(PokeColors.Ink.copy(alpha = 0.18f)),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = 6.dp, bottom = 6.dp)
                .background(PokeColors.Paper),
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(PokeColors.BrandRed))
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                when {
                    controller.exitConfirmationVisible -> ExitConfirmation(
                        onCancel = controller::cancelExitBattle,
                        onConfirm = { onReturnToMap?.let { controller.confirmExitBattle(it) } },
                    )
                    controller.enemyReplacing -> Text(
                        "对手派出 ${controller.enemyName}",
                        color = PokeColors.BrandRed, style = com.pico.spatial.ui.design.PicoTheme.typography.bodyMedium,
                    )
                    itemsOpen && inTurn && !controller.resolving && !controller.charging ->
                        ItemSheet(
                            controller = controller,
                            onUsePotion = { onUsePotion(); itemsOpen = false },
                            onUseRevive = { onUseRevive(); itemsOpen = false },
                            onAssist = { onAssist(); itemsOpen = false },
                            onClose = { itemsOpen = false },
                        )
                    controller.switchMenuVisible -> SwitchSheet(
                        controller = controller,
                        onSwitchTo = { onSwitchTo(it); itemsOpen = false },
                        onClose = onCloseSwitch,
                    )
                    controller.phase == GamePhase.BATTLE_SETUP -> SetupBody()
                    inTurn -> TurnBody(
                        controller = controller,
                        onSelectMove = onSelectMove,
                        onOpenSwitch = onOpenSwitch,
                        onOpenItems = { itemsOpen = true },
                    )
                    else -> Text(
                        text = "对手入场中…",
                        color = PokeColors.TextMid,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                if (!controller.exitConfirmationVisible && onReturnToMap != null) {
                    com.example.fangkemeng.ui.PokeButton(controller::requestExitBattle,
                        Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                        enabled = controller.canReturnToMap) { Text("保存并返回路线", fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun ExitConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("保存并返回路线？", color = PokeColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("保存当前战斗，返回路线。", color = PokeColors.TextMid, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.example.fangkemeng.ui.PokeButton(onCancel, Modifier.weight(1f).height(44.dp), selected = true) {
                Text("继续战斗", fontSize = 14.sp)
            }
            com.example.fangkemeng.ui.PokeButton(onConfirm, Modifier.weight(1f).height(44.dp)) {
                Text("保存并返回路线", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SetupBody() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "握拳抓球，挥手松开投向擂台",
            color = PokeColors.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TurnBody(
    controller: BattleController,
    onSelectMove: (Move) -> Unit,
    onOpenSwitch: () -> Unit,
    onOpenItems: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        PlayerSummary(controller)
        Spacer(Modifier.height(8.dp))
        when {
            controller.charging -> ChargeRow(controller)
            controller.resolving -> Text(
                text = "对手行动中…",
                color = PokeColors.TextMid,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            else -> {
                MoveGrid(controller = controller, onSelectMove = onSelectMove)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuChip(
                        label = "换伙伴",
                        enabled = controller.playerParty.any { !it.fainted && !it.active },
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSwitch,
                    )
                    MenuChip(
                        label = "背包",
                        enabled = controller.items.isNotEmpty() || controller.companionAssistReady,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenItems,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSummary(controller: BattleController) {
    val ratio = if (controller.playerMaxHp <= 0) 0f
    else (controller.playerHp.toFloat() / controller.playerMaxHp).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = controller.playerName,
                color = PokeColors.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(PokeColors.Gold).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(
                    text = "LV.${controller.playerLevel}",
                    color = PokeColors.Ink,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(4.dp))
            Tag(text = controller.playerElement.displayName, color = elementColorOf(controller.playerElement))
            controller.playerStatus?.let { status ->
                Spacer(Modifier.width(4.dp))
                Tag(text = status.displayName, color = statusColorOf(status))
            }
        }
        Spacer(Modifier.height(6.dp))
        HudBar(ratio = ratio, height = 10.dp, fill = PokeColors.hpColor(ratio))
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (controller.playerMaxHp <= 1) "待入场"
                else "HP ${controller.playerHp} / ${controller.playerMaxHp}",
                color = PokeColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "能量 ${controller.playerEnergy}/${controller.playerMaxEnergy} · 回合+${BattleEngine.ENERGY_REGEN}",
                color = PokeColors.TextMid,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(Modifier.background(color).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 横向招式卡：3 张一排，压缩面板高度，给擂台和敌方模型留出视野。 */
@Composable
private fun MoveGrid(controller: BattleController, onSelectMove: (Move) -> Unit) {
    val moves = controller.playerMoves()
    val energy = controller.playerEnergy
    Column(Modifier.fillMaxWidth()) {
        moves.chunked(3).forEach { rowMoves ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowMoves.forEach { move ->
                    MoveCard(
                        move = move,
                        usable = energy >= move.cost,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectMove(move) },
                    )
                }
                repeat(3 - rowMoves.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MoveCard(move: Move, usable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(56.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(PokeColors.Ink.copy(alpha = 0.10f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 3.dp, bottom = 3.dp)
                .background(if (usable) PokeColors.PaperDim else PokeColors.PaperDeep)
                .clickable(enabled = usable) { onClick() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(elementColorOf(move.element)),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = move.name,
                    color = if (usable) PokeColors.Ink else PokeColors.TextLo,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "威力 ${move.power} · 能量 ${move.cost}",
                color = if (usable) PokeColors.TextMid else PokeColors.TextLo,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ChargeRow(controller: BattleController) {
    val progress = controller.chargeProgress
    val canBoost = controller.canBoostCharge
    val cost = controller.currentCastEnergyCost
    val extra = cost - (controller.pendingMove?.cost ?: 0)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = if (canBoost) "挥手蓄力，握拳释放 · ${(progress * 100).toInt()}%" else "握拳释放 · 普通施法",
            color = PokeColors.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        HudBar(ratio = progress.coerceIn(0f, 1f), height = 12.dp, fill = PokeColors.BrandRed)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "本招消耗 $cost · " + when {
                extra > 0 -> "含蓄力 $extra"
                canBoost -> "蓄力增强另耗 1"
                else -> "能量不足以增强"
            },
            color = PokeColors.TextMid,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SwitchSheet(
    controller: BattleController,
    onSwitchTo: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (controller.forceSwitch) "伙伴倒下 · 换人" else "换伙伴",
            color = PokeColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        controller.playerParty.forEach { slot ->
            val selectable = !slot.fainted && !slot.active
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(x = 3.dp, y = 3.dp)
                        .background(PokeColors.Ink.copy(alpha = 0.10f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 3.dp, bottom = 3.dp)
                        .background(if (selectable) PokeColors.PaperDim else PokeColors.PaperDeep)
                        .clickable(enabled = selectable) { onSwitchTo(slot.index) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = slot.name,
                        color = if (selectable) PokeColors.Ink else PokeColors.TextLo,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Tag(text = slot.element.displayName, color = elementColorOf(slot.element))
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when {
                            slot.active -> "出战中"
                            slot.fainted -> "已倒下"
                            else -> "Lv.${slot.level} · ${slot.hp}/${slot.maxHp}"
                        },
                        color = if (selectable) PokeColors.TextMid else PokeColors.TextLo,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (!controller.forceSwitch) {
            Spacer(Modifier.height(6.dp))
            MenuChip(label = "返回", modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

@Composable
private fun ItemSheet(
    controller: BattleController,
    onUsePotion: () -> Unit,
    onUseRevive: () -> Unit,
    onAssist: () -> Unit,
    onClose: () -> Unit,
) {
    val potions = controller.items.count { it.kind == ItemKind.HP_POTION }
    val revives = controller.items.count { it.kind == ItemKind.REVIVE }
    val fullHp = controller.playerHp >= controller.playerMaxHp
    val hasFainted = controller.playerParty.any { it.fainted }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "背包",
            color = PokeColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        if (potions > 0) {
            ItemRow(
                title = "伤药",
                count = potions,
                detail = if (fullHp) "生命已满" else "回复 35 生命",
                enabled = !fullHp,
                onClick = onUsePotion,
            )
        }
        if (revives > 0) {
            ItemRow(
                title = "复活符",
                count = revives,
                detail = if (hasFainted) "复活伙伴，回复 50% 生命" else "无倒下伙伴",
                enabled = hasFainted,
                onClick = onUseRevive,
            )
        }
        if (controller.companionAssistReady) {
            ItemRow(
                title = "伙伴援助",
                count = 1,
                detail = "回复 22 生命",
                enabled = true,
                onClick = onAssist,
            )
        }
        if (potions == 0 && revives == 0 && !controller.companionAssistReady) {
            Text(
                text = "背包为空",
                color = PokeColors.TextMid,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        MenuChip(label = "返回", modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

@Composable
private fun ItemRow(
    title: String,
    count: Int,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(PokeColors.Ink.copy(alpha = 0.10f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 3.dp, bottom = 3.dp)
                .background(if (enabled) PokeColors.PaperDim else PokeColors.PaperDeep)
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (enabled) PokeColors.Ink else PokeColors.TextLo,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "×$count",
                color = if (enabled) PokeColors.BrandRed else PokeColors.TextLo,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = detail,
                color = if (enabled) PokeColors.TextMid else PokeColors.TextLo,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(PokeColors.Ink.copy(alpha = if (enabled) 0.16f else 0.06f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 3.dp, bottom = 3.dp)
                .background(if (enabled) PokeColors.Ink else PokeColors.PaperDeep)
                .clickable(enabled = enabled) { onClick() }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (enabled) PokeColors.PaperText else PokeColors.TextLo,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 常驻小信息条：只保留伙伴名字、HP 与能量，避免大面板挡视野。 */
@Composable
fun BattleStatusStrip(controller: BattleController, modifier: Modifier = Modifier) {
    val ratio = if (controller.playerMaxHp <= 0) 0f
    else (controller.playerHp.toFloat() / controller.playerMaxHp).coerceIn(0f, 1f)

    Box(modifier.width(StatusWidth)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(PokeColors.Ink.copy(alpha = 0.16f)),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = 3.dp, bottom = 3.dp)
                .background(PokeColors.Paper)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = controller.playerName,
                    color = PokeColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.background(PokeColors.Gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(
                        text = "LV.${controller.playerLevel}",
                        color = PokeColors.Ink,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Tag(text = controller.playerElement.displayName, color = elementColorOf(controller.playerElement))
                controller.playerStatus?.let { status ->
                    Spacer(Modifier.width(4.dp))
                    Tag(text = status.displayName, color = statusColorOf(status))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "能量 ${controller.playerEnergy}/${controller.playerMaxEnergy}",
                    color = PokeColors.TextMid,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            HudBar(ratio = ratio, height = 7.dp, fill = PokeColors.hpColor(ratio))
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (controller.playerMaxHp <= 1) "待入场"
                    else "HP ${controller.playerHp}/${controller.playerMaxHp}",
                    color = PokeColors.Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        controller.charging -> if (controller.canBoostCharge) "挥手蓄力，握拳释放 · ${(controller.chargeProgress * 100).toInt()}%" else "握拳释放 · 普通施法"
                        controller.resolving -> "对手行动中…"
                        else -> "张开左手选招"
                    },
                    color = PokeColors.TextMid,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** 敌方头顶名牌：名字在上，HP 条与数值在下。 */
@Composable
fun EnemyPlate(
    name: String,
    level: Int,
    element: Element,
    hp: Int,
    maxHp: Int,
    status: StatusEffect?,
    modifier: Modifier = Modifier,
) {
    val ratio = if (maxHp <= 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)
    Box(modifier.width(EnemyPlateWidth)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(PokeColors.Ink.copy(alpha = 0.20f)),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, bottom = 4.dp)
                .background(PokeColors.Paper)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    color = PokeColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Tag(text = element.displayName, color = elementColorOf(element))
                Box(Modifier.background(PokeColors.Gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text(
                        text = "LV.$level",
                        color = PokeColors.Ink,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                status?.let {
                    Spacer(Modifier.width(4.dp))
                    Tag(text = it.displayName, color = statusColorOf(it))
                }
            }
            Spacer(Modifier.height(4.dp))
            HudBar(ratio = ratio, height = 8.dp, fill = PokeColors.hpColor(ratio))
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$hp / $maxHp",
                color = PokeColors.Ink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun elementColorOf(element: Element): Color = when (element) {
    Element.GRASS -> PokeColors.Mint
    Element.WATER -> PokeColors.Blue
    Element.FIRE -> PokeColors.BrandRed
    Element.ELECTRIC -> PokeColors.HpAmber
    Element.ROCK -> PokeColors.TextLo
    Element.NORMAL -> PokeColors.TextMid
}

private fun statusColorOf(status: StatusEffect): Color = when (status) {
    StatusEffect.BURN -> PokeColors.BrandRed
    StatusEffect.POISON -> PokeColors.Poison
    StatusEffect.PARALYZE -> PokeColors.HpAmber
}
