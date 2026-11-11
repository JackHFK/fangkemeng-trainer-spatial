package com.example.fangkemeng.app

import android.app.Application
import com.example.fangkemeng.battle.SkillPrimitives
import com.example.fangkemeng.model.PlayerState
import com.example.fangkemeng.model.SaveStore
import com.pico.spatial.ui.foundation.dsl.launch

/** 应用入口：把 Spatial 容器声明交给 SDK。 */
class FangkemengApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SaveStore.init(this)
        SkillPrimitives.init(this)
        // 必须先 init 再读取存档，否则 PlayerState 会拿到默认档案。
        PlayerState.reload()
        GameNavigationState.resetForLaunch()
        launch(::mainApp)
    }
}
