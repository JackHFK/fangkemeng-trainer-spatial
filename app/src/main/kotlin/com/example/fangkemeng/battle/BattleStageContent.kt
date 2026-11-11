package com.example.fangkemeng.battle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.fangkemeng.app.HOME_WINDOW_CONTAINER_ID
import com.example.fangkemeng.app.GameFrameSystem
import com.example.fangkemeng.app.SpatialFrameLoop
import com.example.fangkemeng.app.MinimizeHomeOnStageResume
import com.example.fangkemeng.app.GameNavigationState
import com.example.fangkemeng.app.HomeDestination
import com.example.fangkemeng.app.RunResultReason
import com.example.fangkemeng.model.BattleSessionConfig
import com.example.fangkemeng.model.PlayerState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.tracking.hmd.HMDTrackingData
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.launch

private const val PANEL_COMMAND = 2
private const val PANEL_ENEMY = 8
private const val PANEL_RESULT = 6
private const val PANEL_STATUS = 3
private const val PANEL_LEFT = 4

/** 战斗 Stage 内容：手部追踪 + 头前统一指令面板 + 3D 场景。 */
@Composable
fun BattleStageContent(navigation: GameNavigationState = GameNavigationState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigator = LocalSpatialNavigator.current
    // 已挂载的控制器保留到当次胜负反馈退出；恢复旧 Stage 时先检查业务入口。
    val controller = remember {
        PlayerState.profile.run.let { run ->
            if (run.active && run.pendingRewardNode == null && run.currentNode != null) BattleController() else null
        }
    }
    if (controller == null) {
        LaunchedEffect(Unit) {
            navigation.showDestination(if (PlayerState.profile.run.active) HomeDestination.Route else HomeDestination.Camp)
            navigator.restoreWindowContainer(HOME_WINDOW_CONTAINER_ID)
            navigator.closeStage()
        }
        return
    }
    MinimizeHomeOnStageResume()

    val saveAndReturnToRoute: () -> Unit = {
        if (controller.canReturnToMap) scope.launch {
            controller.persistBattle()
            navigation.showDestination(HomeDestination.Route)
            navigator.restoreWindowContainer(HOME_WINDOW_CONTAINER_ID)
            navigator.closeStage()
        }
    }
    val leaveResult: () -> Unit = {
        scope.launch {
            navigation.showDestination(if (controller.resultPlayerWon) HomeDestination.Route else
                HomeDestination.RunResult(RunResultReason.DEFEATED))
            navigator.restoreWindowContainer(HOME_WINDOW_CONTAINER_ID)
            navigator.closeStage()
        }
    }

    val handProvider = remember { HandTrackingProvider() }
    val handData by handProvider.dataFlow.collectAsState(initial = HandTrackingData(null, null, 0L))

    val hmdProvider = remember { HMDTrackingProvider() }
    val hmdData by hmdProvider.dataFlow.collectAsState(
        initial = HMDTrackingData(HMDPose(Vector3.ZERO, Quat.identity()), 0L),
    )

    DisposableEffect(handProvider, hmdProvider) {
        handProvider.start()
        hmdProvider.start()
        onDispose {
            handProvider.stop()
            hmdProvider.stop()
        }
    }

    // 头部跟随锚点：常驻小信息条与结算面板挂在头前方；敌方名牌挂在敌方头顶；
    // 完整指令面板在左手张开时挂到左手附近。
    val hmdAnchor = remember { Entity() }
    val enemyAnchor = remember { Entity() }
    val leftAnchor = remember { Entity() }
    val leftAnchorReady = remember { mutableStateOf(false) }
    val sceneRef = remember { arrayOfNulls<BattleScene>(1) }
    val loop = remember { SpatialFrameLoop() }
    val node = controller.currentNode

    DisposableEffect(loop) {
        GameFrameSystem.add(loop)
        onDispose {
            GameFrameSystem.remove(loop)
            sceneRef[0]?.dispose()
        }
    }

    val leftTracked = handData.left != null
    val leftReady = leftTracked && leftAnchorReady.value
    val inTurn = controller.phase == GamePhase.BATTLE_TURN
    // 左手可用：完整指令面板只在张开时出现；常驻只保留一条小信息条。
    val showLeftPanel = !controller.exitConfirmationVisible && leftReady && inTurn && !controller.resolving &&
        (controller.leftMenuVisible || controller.switchMenuVisible)
    val showHeadPanel = controller.exitConfirmationVisible || controller.phase == GamePhase.ARENA_IDLE ||
        controller.phase == GamePhase.BATTLE_SETUP ||
        (inTurn && !leftReady)
    val showStatus = !controller.exitConfirmationVisible && leftReady && inTurn && !showLeftPanel

    val commandContent: @Composable () -> Unit = {
        BattleCommandPanel(
            controller = controller,
            onSelectMove = { controller.selectMove(it) },
            onOpenSwitch = { controller.openSwitchMenu() },
            onSwitchTo = { controller.switchMember(it) },
            onCloseSwitch = { controller.closeSwitchMenu() },
            onUsePotion = { controller.useFirstPotion() },
            onUseRevive = { controller.useRevive() },
            onAssist = { controller.triggerCompanionAssist() },
            onReturnToMap = saveAndReturnToRoute,
        )
    }

    val frameTick by rememberUpdatedState<(Float) -> Unit>({ dt ->
                hmdAnchor.components.get(TransformComponent::class.java)?.apply {
                    setPosition(hmdData.hmdPose.position)
                    setQuaternion(hmdData.hmdPose.rotation)
                }
                sceneRef[0]?.fangkemengHeadPosition(isPlayer = false)?.let { head ->
                    // 名牌贴在敌方头顶上方，朝向始终跟随头显，保证名牌正面可见。
                    enemyAnchor.components.get(TransformComponent::class.java)?.apply {
                        setPosition(Vector3(head.x, head.y + 0.08f, head.z + 0.35f))
                        setQuaternion(hmdData.hmdPose.rotation)
                    }
                }
                // 左手张开时，完整指令面板跟随左掌位置。
                var anchorReady = false
                controller.leftMenuAnchor()?.let { raw ->
                    sceneRef[0]?.toContent(raw)?.let { local ->
                        leftAnchor.components.get(TransformComponent::class.java)?.apply {
                            setPosition(local)
                            setQuaternion(hmdData.hmdPose.rotation)
                        }
                        anchorReady = true
                    }
                }
                leftAnchorReady.value = anchorReady
                val right = HandJointMapper.rightJoints(handData)
                val left = HandJointMapper.leftJoints(handData)
                sceneRef[0]?.updateCompanionCat(dt, hmdData.hmdPose.position, hmdData.hmdPose.rotation)
                controller.tick(dt, right, left)
                sceneRef[0]?.updateCompanionTouch(dt, right, left)
    })

    PicoTheme {
        SpatialView(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectSpatialTapGesture(context, TargetEntity.any()) { }
            },
            attachments = {
                AttachmentPanel(PANEL_COMMAND) {
                    if (showHeadPanel) commandContent()
                }
                AttachmentPanel(PANEL_STATUS) {
                    if (showStatus) BattleStatusStrip(controller)
                }
                AttachmentPanel(PANEL_LEFT) {
                    if (showLeftPanel) commandContent()
                }
                AttachmentPanel(PANEL_ENEMY) {
                    if (controller.phase == GamePhase.BATTLE_TURN && controller.enemyHp > 0) {
                        EnemyPlate(
                            name = controller.enemyName,
                            level = controller.enemyLevel,
                            element = controller.enemyElement,
                            hp = controller.enemyHp,
                            maxHp = controller.enemyMaxHp,
                            status = controller.enemyStatus,
                        )
                    }
                }
                AttachmentPanel(PANEL_RESULT) {
                    if (controller.phase == GamePhase.RESULT) {
                        ResultOverlay(
                            playerWon = controller.resultPlayerWon,
                            layer = controller.battle?.layer ?: 1,
                            onExit = leaveResult,
                        )
                    }
                }
            },
        ) { content, attachments ->
            content.addEntity(hmdAnchor)
            content.addEntity(enemyAnchor)
            content.addEntity(leftAnchor)

            BattleSessionConfig.selectedStage = com.example.fangkemeng.model.SceneCatalog.stageFor(node.arena)
            val scene = BattleScene(content)
            sceneRef[0] = scene
            controller.attachScene(scene)
            scene.setupStaticScene()
            scope.launch {
                scene.buildSelectedStage()
                // 关卡固定对手：进入即刷新对手，玩家只需投球开战。
                controller.chooseNpcOpponent()
                controller.resumeBattleIfSaved()
            }

            val placed = mutableSetOf<Int>()
            var commandSlot: Entity? = null
            var commandAtEyeLevel: Boolean? = null
            fun placeHead(id: Int, local: Vector3, scaleBy: Float = 1f) {
                if (id in placed) return
                attachments.entity(id)?.let { panel ->
                    val slot = Entity().apply {
                        components.get(TransformComponent::class.java)?.apply {
                            setPosition(local)
                            if (scaleBy != 1f) scaleBy(scaleBy)
                        }
                        addChild(panel)
                    }
                    hmdAnchor.addChild(slot)
                    if (id == PANEL_COMMAND) commandSlot = slot
                    placed += id
                }
            }
            // 投球说明正对视线；回合指令仍使用偏下位置，留出战场视野。
            fun bindPanels() {
            placeHead(PANEL_COMMAND, Vector3(0f, -0.22f, -0.62f), 0.70f)
            val atEyeLevel = controller.exitConfirmationVisible || controller.phase != GamePhase.BATTLE_TURN
            commandSlot?.let { slot ->
                if (commandAtEyeLevel != atEyeLevel) {
                    slot.components.get(TransformComponent::class.java)?.apply {
                        setPosition(if (atEyeLevel) Vector3(0f, 0.02f, -0.85f) else Vector3(0f, -0.22f, -0.62f))
                    }
                    commandAtEyeLevel = atEyeLevel
                }
            }
            // 常驻小信息条：只显示伙伴名字/HP/能量，尽量不挡擂台。
            placeHead(PANEL_STATUS, Vector3(0f, -0.30f, -0.60f), 0.50f)
            placeHead(PANEL_RESULT, Vector3(0f, 0.04f, -0.85f))
            // 左手张开时的完整指令面板：跟随左掌，朝向跟随头显。
            if (PANEL_LEFT !in placed) attachments.entity(PANEL_LEFT)?.let { panel ->
                val slot = Entity().apply {
                    components.get(TransformComponent::class.java)?.scaleBy(0.72f)
                    addChild(panel)
                }
                leftAnchor.addChild(slot)
                placed += PANEL_LEFT
            }
            if (PANEL_ENEMY !in placed) attachments.entity(PANEL_ENEMY)?.let { panel ->
                val slot = Entity().apply {
                    components.get(TransformComponent::class.java)?.scaleBy(3.0f)
                    addChild(panel)
                }
                enemyAnchor.addChild(slot)
                placed += PANEL_ENEMY
            }
            }
            loop.attach(hmdAnchor) {
                bindPanels()
                frameTick(it)
            }
        }
    }
}
