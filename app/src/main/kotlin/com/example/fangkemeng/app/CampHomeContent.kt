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
import com.pico.spatial.ui.design.OptionDefaults
import com.pico.spatial.ui.design.Text

/** 营地只呈现永久成长与挑战入口，不把历史本局结果当成首页内容。 */
@Composable
internal fun CampHomeContent(
    profile: PlayerProfile, stateRevision: Int, focusedSlot: Int,
    onFocusSlot: (Int) -> Unit, onChallenge: () -> Unit, onManage: () -> Unit,
    partner: @Composable (Modifier, Boolean, CreatureId) -> Unit,
) {
    var showingHelp by remember { mutableStateOf(false) }
    val run = remember(stateRevision, profile.run) { profile.run }
    val id = profile.party.getOrNull(focusedSlot) ?: profile.party.first()
    val species = DemoData.speciesOf(id.name)
    CampPage("方可梦 · 营地", "营地金币", profile.gold) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.width(288.dp).fillMaxHeight()) {
                Text("方可梦", color = PokeColors.Ink, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Text("训练营", color = PokeColors.BrandRed, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("准备伙伴，沿七层路线\n挑战训练营首领。", color = PokeColors.TextMid,
                    fontSize = 20.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 24.dp))
                if (run.active) {
                    Text(if (run.pendingRewardNode != null) "第 ${run.completedCount} 层已胜\n战后奖励待领取"
                        else "挑战进行中 · 第 ${run.layer} 层\n${if (run.battle != null) "当前战斗已保存" else "队伍与路线已保存"}",
                        color = PokeColors.BrandRed, fontSize = 18.sp, lineHeight = 28.sp,
                        modifier = Modifier.padding(top = 24.dp))
                }
                Spacer(Modifier.weight(1f))
                PokeButton(onChallenge, Modifier.fillMaxWidth().height(56.dp), selected = true) {
                    Text(if (run.active) "继续挑战" else "开始挑战", fontSize = 20.sp)
                }
                PokeButton(onManage, Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp)) {
                    Text("伙伴管理", fontSize = 18.sp)
                }
                PokeButton({ showingHelp = true }, Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp)) {
                    Text("玩法", fontSize = 18.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                partner(Modifier.fillMaxWidth().weight(1f), !showingHelp, id)
                Text(species?.name.orEmpty(), color = PokeColors.Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("${species?.element?.displayName} · 等级 ${profile.levelOf(id)}",
                    color = PokeColors.BrandRed, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
                Text(profile.loadoutOf(id).joinToString(" · ") { it.name }, color = PokeColors.TextMid,
                    fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.party.forEachIndexed { index, creature ->
                        Option(selected = id == creature, onSelectChange = { if (it) onFocusSlot(index) },
                            modifier = Modifier.weight(1f).height(88.dp), colors = partnerOptionColors()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(DemoData.speciesOf(creature.name)?.name.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("${if (index == 0) "首发 · " else ""}等级 ${profile.levelOf(creature)}", fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                Text("已拥有 ${profile.unlocked.size} 位伙伴 · 最高第 ${profile.bestLayer} 层 · 通关 ${profile.runsCleared} 次",
                    color = PokeColors.TextMid, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
        }
    }
    if (showingHelp) HelpSheet { showingHelp = false }
}

@Composable
internal fun partnerOptionColors() = OptionDefaults.optionColors(
    checkedContainerColor = PokeColors.Ink, checkedContentColor = PokeColors.Gold,
    unCheckedContainerColor = PokeColors.PaperDeep, unCheckedContentColor = PokeColors.Ink,
)
