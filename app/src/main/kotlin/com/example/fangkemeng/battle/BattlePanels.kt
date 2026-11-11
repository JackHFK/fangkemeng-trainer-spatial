package com.example.fangkemeng.battle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fangkemeng.model.Element
import com.example.fangkemeng.model.Move
import com.example.fangkemeng.model.Fangkemeng
import com.example.fangkemeng.model.StatusEffect
import com.example.fangkemeng.model.StageSceneOption
import com.example.fangkemeng.ui.HudBar
import com.example.fangkemeng.ui.HudTitle
import com.example.fangkemeng.ui.PokeColors
import com.example.fangkemeng.ui.StageTicket
import com.example.fangkemeng.ui.TicketSurface
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Text

private fun elementColor(element: Element): Color = when (element) {
    Element.GRASS -> PokeColors.Mint
    Element.WATER -> PokeColors.Blue
    Element.FIRE -> PokeColors.BrandRed
    Element.ELECTRIC -> PokeColors.HpAmber
    Element.ROCK -> PokeColors.TextLo
    Element.NORMAL -> PokeColors.TextMid
}

private fun statusColor(status: StatusEffect): Color = when (status) {
    StatusEffect.BURN -> PokeColors.BrandRed
    StatusEffect.POISON -> PokeColors.Poison
    StatusEffect.PARALYZE -> PokeColors.HpAmber
}

/** 方可梦头顶 HP 卡：编号 + 黑标题条 + 大号数字 + 分段刻度。 */
@Composable
fun HpBarPanel(
    name: String,
    hp: Int,
    maxHp: Int,
    side: String,
    index: String = "P1",
    level: Int = 10,
    element: Element = Element.NORMAL,
    status: StatusEffect? = null,
    energy: Int = 6,
    maxEnergy: Int = 6,
    party: List<PartySlotView> = emptyList(),
) {
    val ratio = if (maxHp <= 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)
    val hpColor = PokeColors.hpColor(ratio)
    Box(modifier = Modifier.width(300.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(PokeColors.Ink.copy(alpha = 0.30f)),
        )
        Box(Modifier.padding(end = 4.dp, bottom = 4.dp).background(PokeColors.Ink)) {
            Column(Modifier.padding(1.dp).background(PokeColors.Paper)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PokeColors.Ink)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = index,
                        color = PokeColors.Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = name,
                        color = PokeColors.PaperText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(PokeColors.Gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(
                            text = "LV.$level",
                            color = PokeColors.Ink,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(elementColor(element)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text(
                            text = element.displayName,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (status != null) {
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.background(statusColor(status)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text(
                                text = status.displayName,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = side,
                        color = PokeColors.BrandRedHi,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                    )
                }
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$hp",
                            color = PokeColors.TextHi,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " / $maxHp",
                            color = PokeColors.TextLo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "HP",
                            color = PokeColors.TextLo,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    HudBar(ratio = ratio, height = 10.dp, fill = hpColor)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                ratio > 0.5f -> "状态良好"
                                ratio > 0.2f -> "体力下降"
                                else -> "濒危"
                            },
                            color = PokeColors.TextMid,
                            fontSize = 10.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${(ratio * 100).toInt()}%",
                            color = hpColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "能量",
                            color = PokeColors.TextLo,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        repeat(maxEnergy) { i ->
                            Box(
                                Modifier
                                    .padding(end = 3.dp)
                                    .size(width = 14.dp, height = 7.dp)
                                    .background(if (i < energy) PokeColors.Gold else PokeColors.Ink.copy(alpha = 0.15f)),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (party.isNotEmpty()) {
                            repeat(party.size) { i ->
                                val slot = party[i]
                                Box(
                                    Modifier
                                        .padding(start = 4.dp)
                                        .size(9.dp)
                                        .background(
                                            when {
                                                slot.fainted -> PokeColors.Ink.copy(alpha = 0.18f)
                                                slot.active -> PokeColors.BrandRed
                                                else -> PokeColors.Mint
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 跟随左手的统一菜单：内容随阶段变化。
 * ARENA_IDLE→主菜单；BATTLE_SETUP→提示握右手投球；BATTLE_TURN→技能/物品。
 */
@Composable
fun LeftHandMenuByPhase(
    phase: GamePhase,
    moves: List<Move>,
    charging: Boolean,
    chargeProgress: Float,
    companionEnergy: Float,
    companionAssistReady: Boolean,
    onChooseNpc: () -> Unit,
    onThrowBall: () -> Unit,
    onSelectMove: (Move) -> Unit,
    onSpeakMove: (Move) -> Unit,
    onCompanionAssist: () -> Unit,
    onUsePotion: () -> Unit,
    onBackHome: () -> Unit,
    energy: Int = 6,
    party: List<PartySlotView> = emptyList(),
    forcedSwitch: Boolean = false,
    onOpenSwitch: () -> Unit = {},
    onSwitchTo: (Int) -> Unit = {},
    onUseRevive: () -> Unit = {},
) {
    when (phase) {
        GamePhase.ARENA_IDLE -> MenuShell(title = "主菜单") {
            MenuTicket("挑选 NPC 对手", trailing = "PVE", dark = true, onClick = onChooseNpc)
            Spacer(Modifier.height(8.dp))
            MenuTicket("退出游戏", onClick = onBackHome)
        }
        GamePhase.BATTLE_SETUP -> MenuShell(title = "投出精灵球") {
            MenuTicket("投出精灵球", trailing = "THROW", dark = true, onClick = onThrowBall)
            Spacer(Modifier.height(8.dp))
            MenuTicket("返回主页", onClick = onBackHome)
        }
        GamePhase.BATTLE_TURN -> if (charging) {
            MenuShell(title = "蓄力中 · 挥动右手") {
                ChargeGauge(chargeProgress)
            }
        } else if (forcedSwitch) {
            MenuShell(title = "精灵倒下 · 必须换人", trailing = "SWITCH") {
                val candidates = party.filter { !it.fainted && !it.active }
                candidates.forEach { slot ->
                    MenuTicket(
                        label = slot.name,
                        trailing = "HP ${slot.hp}/${slot.maxHp}",
                        dark = true,
                        element = slot.element,
                        onClick = { onSwitchTo(slot.index) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (candidates.isEmpty()) {
                    Text(text = "没有可换上的精灵", color = PokeColors.TextMid, fontSize = 12.sp)
                }
            }
        } else {
            MenuShell(title = "行动指令", trailing = "ENERGY $energy/${Fangkemeng.MAX_ENERGY}") {
                moves.forEachIndexed { i, move ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            MenuTicket(
                                label = move.name,
                                trailing = "PWR ${move.power} · E${move.cost}",
                                index = "%02d".format(i + 1),
                                element = move.element,
                                enabled = energy >= move.cost,
                                onClick = { onSelectMove(move) },
                            )
                        }
                        MenuTicket(
                            label = "MIC",
                            dark = true,
                            width = 56.dp,
                            enabled = energy >= move.cost,
                            onClick = { onSpeakMove(move) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (companionAssistReady) {
                    MenuTicket("伙伴援助 · 治疗", trailing = "READY", dark = true, onClick = onCompanionAssist)
                } else {
                    CompanionEnergyBar(companionEnergy)
                }
                Spacer(Modifier.height(8.dp))
                MenuTicket("换人", trailing = "SWITCH", element = null, onClick = onOpenSwitch)
                Spacer(Modifier.height(8.dp))
                MenuTicket("使用伤药", trailing = "ITEM", onClick = onUsePotion)
                Spacer(Modifier.height(8.dp))
                MenuTicket("复活符", trailing = "REVIVE", onClick = onUseRevive)
            }
        }
        GamePhase.RESULT -> {}
    }
}

/** 换人面板：3 个队伍槽位，倒下与出战中的置灰。 */
@Composable
fun SwitchMenu(
    party: List<PartySlotView>,
    forced: Boolean,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    MenuShell(title = if (forced) "必须换人" else "更换出战", trailing = "PARTY · 03") {
        party.forEach { slot ->
            MenuTicket(
                label = slot.name,
                trailing = "HP ${slot.hp}/${slot.maxHp}" + if (slot.fainted) " · 倒下" else "",
                element = slot.element,
                dark = slot.active,
                enabled = !slot.fainted && !slot.active,
                onClick = { onPick(slot.index) },
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!forced) {
            Spacer(Modifier.height(2.dp))
            MenuTicket("取消", trailing = "CLOSE", onClick = onClose)
        }
    }
}

@Composable
private fun ChargeGauge(progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}",
                color = PokeColors.TextHi,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "%", color = PokeColors.BrandRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.background(PokeColors.Gold).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    text = "×%.2f".format(1f + progress),
                    color = PokeColors.Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(22.dp)) {
            HudBar(
                ratio = progress,
                modifier = Modifier.align(Alignment.Center),
                height = 18.dp,
                fill = PokeColors.Gold,
                segments = 20,
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * 0.75f - 1.dp)
                    .width(2.dp)
                    .height(22.dp)
                    .background(PokeColors.Ink),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "握拳立即释放", color = PokeColors.TextMid, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(text = "MAX ×2.00", color = PokeColors.TextLo, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompanionEnergyBar(energy: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "把手伸向伙伴猫充能", color = PokeColors.TextMid, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(energy.coerceIn(0f, 1f) * 100).toInt()}%",
                color = PokeColors.Mint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        HudBar(ratio = energy, height = 8.dp, fill = PokeColors.Mint, segments = 8)
    }
}

@Composable
private fun MenuShell(
    title: String,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.width(360.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(PokeColors.Ink.copy(alpha = 0.28f)),
        )
        Box(Modifier.padding(end = 4.dp, bottom = 4.dp).background(PokeColors.Ink)) {
            Column(
                modifier = Modifier.padding(1.dp).background(PokeColors.Paper).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTitle(title)
                    Spacer(Modifier.weight(1f))
                    if (trailing != null) {
                        Text(
                            text = trailing,
                            color = PokeColors.TextLo,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(PokeColors.Ink.copy(alpha = 0.16f)))
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

/** 票根式菜单按钮：编号 + 虚线撕口 + 标签，dark=true 时为黑底反白。 */
@Composable
private fun MenuTicket(
    label: String,
    trailing: String? = null,
    index: String? = null,
    dark: Boolean = false,
    width: Dp? = null,
    enabled: Boolean = true,
    element: Element? = null,
    onClick: () -> Unit,
) {
    TicketSurface(
        modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).height(50.dp),
        selected = dark,
        shadow = if (dark) PokeColors.BrandRed else PokeColors.Ink.copy(alpha = 0.22f),
        onClick = { if (enabled) onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (element != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (enabled) elementColor(element) else PokeColors.Ink.copy(alpha = 0.25f)),
                )
                Spacer(Modifier.width(8.dp))
            }
            if (index != null) {
                Text(
                    text = index,
                    modifier = Modifier.width(20.dp),
                    color = if (dark) PokeColors.Gold else PokeColors.TextMid,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Canvas(Modifier.width(1.dp).height(26.dp)) {
                    drawLine(
                        color = if (dark) PokeColors.PaperTextDim else PokeColors.Ink.copy(alpha = 0.4f),
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = label,
                color = when {
                    !enabled -> PokeColors.TextLo.copy(alpha = 0.55f)
                    dark -> PokeColors.PaperText
                    else -> PokeColors.TextHi
                },
                fontSize = if (width != null) 9.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (width != null) 1.2.sp else 0.sp,
            )
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = trailing,
                    color = if (dark) PokeColors.Gold else PokeColors.TextLo,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (width != null) 1.2.sp else 0.sp,
                )
            }
        }
    }
}

/**
 * 战场部署面板（Stage 内）：右侧票根列表直接切换真实 3D 场景，即为所选战场提供实时预览；
 * 底部黑条为主行动，未召唤对手时为「召唤对手」，进入布置阶段后为「投出精灵球」。
 */
@Composable
fun DeployPanel(
    stages: List<StageSceneOption>,
    selectedStage: StageSceneOption,
    phase: GamePhase,
    onSelectStage: (StageSceneOption) -> Unit,
    onPrimary: () -> Unit,
    onBackHome: () -> Unit,
) {
    val primaryLabel = if (phase == GamePhase.BATTLE_SETUP) "投出精灵球" else "召唤对手"
    Box(Modifier.width(360.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(PokeColors.Ink.copy(alpha = 0.30f)),
        )
        Box(Modifier.padding(end = 4.dp, bottom = 4.dp).background(PokeColors.Ink)) {
            Column(Modifier.padding(1.dp).background(PokeColors.Paper).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudTitle("战场部署")
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.background(PokeColors.Gold).padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text(
                            text = "LIVE · 3D",
                            color = PokeColors.Ink,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.0.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(PokeColors.Ink.copy(alpha = 0.16f)))
                Spacer(Modifier.height(10.dp))
                stages.forEachIndexed { index, stage ->
                    StageTicket(
                        name = stage.name,
                        subtitle = stage.subtitle,
                        index = index,
                        selected = stage.id == selectedStage.id,
                        tags = stage.tags,
                        onClick = { onSelectStage(stage) },
                    )
                    if (index < stages.lastIndex) Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(50.dp)) {
                    TicketSurface(
                        modifier = Modifier.fillMaxSize(),
                        selected = true,
                        shadow = PokeColors.BrandRed,
                        onClick = onPrimary,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = primaryLabel,
                                color = PokeColors.PaperText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(text = "›", color = PokeColors.Gold, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "切换票根即时预览 3D 战场",
                        color = PokeColors.TextLo,
                        fontSize = 9.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "返回主页 ×",
                        modifier = Modifier.clickable(onClick = onBackHome).padding(4.dp),
                        color = PokeColors.TextMid,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** 战斗结算覆盖层：海报式胜利卡，含关卡进度、金币、升级与解锁。 */
@Composable
fun ResultOverlay(
    playerWon: Boolean,
    layer: Int,
    onExit: () -> Unit,
) {
    com.example.fangkemeng.app.SheetFrame(
        title = if (playerWon) "战斗胜利" else "战斗失败",
        width = 470.dp, onClose = null, asAttachment = true,
    ) {
        Text("第 $layer 层 · ${if (playerWon) "这场挑战已完成" else "全队伙伴已倒下"}",
            color = PokeColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(if (playerWon) "返回路线选择战后奖励。" else "本局挑战结束，前往本局结算。",
            color = PokeColors.TextMid, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
        com.example.fangkemeng.ui.PokeButton(onExit, Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp), selected = true) {
            Text(if (playerWon) "返回路线领取奖励" else "查看本局结算", fontSize = 20.sp)
        }
    }
}
