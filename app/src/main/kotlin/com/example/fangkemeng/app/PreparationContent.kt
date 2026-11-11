package com.example.fangkemeng.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fangkemeng.model.*
import com.example.fangkemeng.ui.PokeButton
import com.example.fangkemeng.ui.PokeColors
import com.pico.spatial.ui.design.Option
import com.pico.spatial.ui.design.Text

private enum class PreparationModal { NONE, PARTY, TRAINING, RECRUIT }

/** 整个准备页持有一个草稿，返回营地不会提交编队或首发。 */
@Composable
internal fun PreparationContent(
    profile: PlayerProfile, stateRevision: Int, purpose: PreparationPurpose,
    onConfirm: (List<CreatureId>, Map<CreatureId, List<String>>, PreparationPurpose) -> Unit,
    onTrain: (CreatureId, Int) -> Unit, onRecruit: (ShopEntry) -> Unit,
    onReturnToCamp: () -> Unit,
    partner: @Composable (Modifier, Boolean, CreatureId) -> Unit,
) {
    val draft = remember(profile, purpose) { PreparationDraft(profile) }
    var draftRevision by remember { mutableStateOf(0) }
    var focused by remember { mutableStateOf(draft.party.first()) }
    var modal by remember { mutableStateOf(PreparationModal.NONE) }
    val locked = profile.run.active
    val issue = remember(draftRevision, stateRevision) { draft.issue }
    val species = DemoData.speciesOf(focused.name)
    val start = purpose == PreparationPurpose.START_CHALLENGE
    CampPage(if (start) "挑战准备" else "伙伴管理", "营地金币", profile.gold) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(288.dp).fillMaxHeight()) {
                Text(if (locked) "当前队伍" else "选择首发", color = PokeColors.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                draft.party.forEachIndexed { index, creature ->
                    Option(selected = focused == creature, onSelectChange = { if (it) focused = creature },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(60.dp), colors = partnerOptionColors()) {
                        Text("${DemoData.speciesOf(creature.name)?.name} · 等级 ${profile.levelOf(creature)}${if (locked && index == 0) " · 首发" else ""}",
                            fontSize = 18.sp)
                    }
                }
                Text(draft.moves(focused).joinToString(" · "), color = PokeColors.Ink, fontSize = 18.sp, lineHeight = 26.sp,
                    modifier = Modifier.padding(vertical = 16.dp))
                if (locked) Text("本局队伍已锁定。\n结束挑战后可编队、训练和招募。", color = PokeColors.BrandRed,
                    fontSize = 18.sp, lineHeight = 28.sp)
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PokeButton({ modal = PreparationModal.PARTY }) { Text("编队", fontSize = 16.sp) }
                        PokeButton({ modal = PreparationModal.TRAINING }) { Text("训练", fontSize = 16.sp) }
                        PokeButton({ modal = PreparationModal.RECRUIT }) { Text("招募", fontSize = 16.sp) }
                    }
                    Text("返回不提交草稿。\n训练与招募确认后立即保存。", color = PokeColors.TextMid,
                        fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 16.dp))
                }
                Spacer(Modifier.weight(1f))
                if (issue != null && !locked) Text(issue, color = PokeColors.BrandRed, fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
                if (!locked) PokeButton({
                    val party = listOf(focused) + draft.party.filter { it != focused }
                    onConfirm(party, draft.loadouts.mapValues { it.value.toList() }, purpose)
                }, Modifier.fillMaxWidth().height(56.dp), selected = true, enabled = issue == null) {
                    Text(if (start) "确认队伍，查看路线" else "保存并返回营地", fontSize = 18.sp)
                }
                PokeButton(onReturnToCamp, Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp)) {
                    Text("返回营地", fontSize = 18.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                partner(Modifier.fillMaxWidth().weight(1f), modal == PreparationModal.NONE, focused)
                Text(species?.name.orEmpty(), color = PokeColors.Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${species?.element?.displayName} · 等级 ${profile.levelOf(focused)}", color = PokeColors.BrandRed, fontSize = 20.sp,
                    modifier = Modifier.padding(top = 8.dp))
                Text("生命 ${CreatureGrowth.maxHp(profile.levelOf(focused))} · 速度 ${CreatureGrowth.speed(profile.levelOf(focused))}",
                    color = PokeColors.TextMid, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp))
            }
        }
    }
    when (modal) {
        PreparationModal.PARTY -> PartySheet(profile, onConfirm = { party, loadouts ->
            draft.party.clear(); draft.party.addAll(party)
            draft.loadouts.putAll(loadouts)
            if (focused !in party) focused = party.first()
            draftRevision++
            modal = PreparationModal.NONE
        }, onClose = { modal = PreparationModal.NONE }, initialParty = draft.party, initialLoadouts = draft.loadouts)
        PreparationModal.TRAINING -> TrainingSheet(profile, stateRevision, focused, onTrain) { modal = PreparationModal.NONE }
        PreparationModal.RECRUIT -> ShopSheet(CampaignMap.partnerStock(profile), profile.gold, onRecruit,
            onClose = { modal = PreparationModal.NONE })
        PreparationModal.NONE -> Unit
    }
}
