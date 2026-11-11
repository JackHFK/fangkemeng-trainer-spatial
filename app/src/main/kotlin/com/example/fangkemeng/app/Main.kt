package com.example.fangkemeng.app

import com.example.fangkemeng.battle.BattleStageContent
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage

/** Spatial 容器声明入口：2D 主页面 WindowContainer + 战斗 Stage。 */
fun mainApp(scope: SpatialAppScope) = with(scope) {
    val navigation = GameNavigationState
    DefaultWindowContainer {
        HomeRoot(navigation)
    }

    Stage(id = BATTLE_STAGE_ID) {
        BattleStageContent(navigation)
    }
}

const val HOME_WINDOW_CONTAINER_ID: String = "Home"
const val BATTLE_STAGE_ID: String = "battle-stage"
