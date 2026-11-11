package com.example.fangkemeng.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fangkemeng.model.CampaignMap
import com.example.fangkemeng.model.PlayerProfile
import com.example.fangkemeng.ui.PokeButton
import com.example.fangkemeng.ui.PokeColors
import com.pico.spatial.ui.design.Text

/** 本局结果只读取已结算数据，页面打开与关闭均不产生业务写入。 */
@Composable
internal fun RunResultContent(profile: PlayerProfile, reason: RunResultReason, onReturnToCamp: () -> Unit) {
    CampPage("本局结算", "营地金币", profile.gold) {
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(when (reason) {
                RunResultReason.CLEARED -> "挑战通关"
                RunResultReason.DEFEATED -> "挑战失败"
                RunResultReason.ABANDONED -> "已放弃本局"
            }, color = PokeColors.BrandRed, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("已完成 ${profile.run.completedCount}/${CampaignMap.DEPTHS} 层",
                color = PokeColors.Ink, fontSize = 24.sp, modifier = Modifier.padding(top = 24.dp))
            Text("本次带回 ${profile.run.settledGold} 金币", color = PokeColors.Ink,
                fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 32.dp))
            Text("已计入营地钱包 · 当前余额 ${profile.gold}", color = PokeColors.TextMid,
                fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
            PokeButton(onReturnToCamp, Modifier.width(288.dp).padding(top = 48.dp).height(56.dp), selected = true) {
                Text("返回营地", fontSize = 20.sp)
            }
        }
    }
}
