package com.example.fangkemeng.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 战场风格：每种风格决定配色与道具组合，见 `VoxelStages`。 */
enum class ArenaTheme {
    MEADOW, ARENA, FOREST, VOLCANIC, RUINS, STORM, CRYSTAL, SKY, THRONE,
}

/** 可在进入 Full Space 前选择的战斗场景。 */
data class StageSceneOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val tags: List<String> = emptyList(),
    /** 程序化体素擂台风格。 */
    val theme: ArenaTheme = ArenaTheme.ARENA,
    /** 首领战场：更大的看台、王座与双圈围栏。 */
    val boss: Boolean = false,
)

object SceneCatalog {
    /** 场景目录独立于战斗层；节点冻结 arena 索引。 */
    val stages: List<StageSceneOption> = listOf(
        StageSceneOption(
            id = "meadow_ground",
            name = "青草训练场",
            subtitle = "开阔草皮与木栅栏，适合第一场热身",
            tags = listOf("昼", "草地"),
            theme = ArenaTheme.MEADOW,
        ),
        StageSceneOption(
            id = "old_battle_arena",
            name = "旧日竞技场",
            subtitle = "石柱与红金条幅，标准圆形擂台",
            tags = listOf("昼", "石台"),
            theme = ArenaTheme.ARENA,
        ),
        StageSceneOption(
            id = "forest_hut",
            name = "林间空地",
            subtitle = "树影里的低模空地，草丛更利于观察落点",
            tags = listOf("晨", "草丛"),
            theme = ArenaTheme.FOREST,
        ),
        StageSceneOption(
            id = "lava_forge",
            name = "熔岩锻台",
            subtitle = "暗红岩台与火盆，热浪压低视线",
            tags = listOf("暮", "熔岩"),
            theme = ArenaTheme.VOLCANIC,
        ),
        StageSceneOption(
            id = "stone_ruins",
            name = "岩脊遗迹",
            subtitle = "断裂石柱围出的窄场，岩系占优",
            tags = listOf("昼", "遗迹"),
            theme = ArenaTheme.RUINS,
        ),
        StageSceneOption(
            id = "storm_altar",
            name = "风暴高台",
            subtitle = "靛蓝台面与电光晶柱，视野被风切碎",
            tags = listOf("夜", "雷暴"),
            theme = ArenaTheme.STORM,
        ),
        StageSceneOption(
            id = "alien_forest",
            name = "异星森林",
            subtitle = "晶簇与低雾，龙与鱼的舞台",
            tags = listOf("夜", "异星"),
            theme = ArenaTheme.CRYSTAL,
        ),
        StageSceneOption(
            id = "sky_terrace",
            name = "云端看台",
            subtitle = "悬空石台与云团，边线之外就是虚空",
            tags = listOf("昼", "高空"),
            theme = ArenaTheme.SKY,
        ),
        StageSceneOption(
            id = "throne_hall",
            name = "训练营王座",
            subtitle = "双层看台、金纹王座与八柱旗阵，首领专用",
            tags = listOf("夜", "首领"),
            theme = ArenaTheme.THRONE,
            boss = true,
        ),
    )

    /** 按场景目录索引选择战场。 */
    fun stageFor(level: Int): StageSceneOption =
        stages.getOrNull(level - 1) ?: stages.first()

    val defaultStage: StageSceneOption = stages.first()

}

/** WindowContainer 与 Stage 之间共享的本地会话选择。 */
object BattleSessionConfig {
    var selectedStage: StageSceneOption = SceneCatalog.defaultStage
    /** 进入 Stage 时携带的主线节点。 */
    var currentNode: MapNode? = null
    /** 下一站由常驻首页完成关闭与重开，避免 Stage 自身的协程随关闭失效。 */
    var pendingContinue by androidx.compose.runtime.mutableStateOf<MapNode?>(null)
}
