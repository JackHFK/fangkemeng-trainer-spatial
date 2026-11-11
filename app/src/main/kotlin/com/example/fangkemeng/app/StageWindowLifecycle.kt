package com.example.fangkemeng.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.launch

/** Stage 实际恢复后收起首页，避免异步开关 Stage 时首页恢复事件覆盖早期调用。 */
@Composable
internal fun MinimizeHomeOnStageResume() {
    val owner = LocalLifecycleOwner.current
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    DisposableEffect(owner, navigator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    navigator.minimizeWindowContainer(HOME_WINDOW_CONTAINER_ID)
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
