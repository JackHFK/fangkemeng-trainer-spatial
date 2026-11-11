package com.example.fangkemeng.app

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

/** Spatial 启动 Activity，内容由 mainApp 声明的容器渲染。 */
class LaunchActivity : SpatialLaunchActivity() {
    private val requestHandTracking =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null && intent.isLauncherEntry()) {
            GameNavigationState.resetForLaunch()
        }
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, HAND_TRACKING_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestHandTracking.launch(HAND_TRACKING_PERMISSION)
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.isLauncherEntry()) GameNavigationState.resetForLaunch()
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun Intent.isLauncherEntry() =
        action == Intent.ACTION_MAIN && hasCategory(Intent.CATEGORY_LAUNCHER)

    private companion object {
        const val HAND_TRACKING_PERMISSION = "com.picovr.permission.HAND_TRACKING"
    }
}
