package com.example.fangkemeng.battle

import com.example.fangkemeng.model.Element
import com.example.fangkemeng.model.Move

/** Generated from the user-approved effect-catalog.json; seconds and metres. */
internal data class SkillLayer(
    val kind: String, val label: String, val start: Double, val end: Double,
    val ease: String = "linear", val anchor: String = "source", val count: Int = 1,
    val radius: Double = .2, val width: Double = .03, val life: Double = .5,
    val mode: String = "", val socket: String = "mouth", val shape: String = "",
    val interval: Double = 0.0, val flight: Double = 0.0, val height: Double = .22,
    val pulses: Int = 0, val turns: Double = 4.0, val batch: Int = 0,
    val emitEnd: Double? = null,
)
internal data class SkillRecipe(
    val key: String, val name: String, val element: Element, val action: String,
    val cast: Double, val hit: Double, val end: Double, val layers: List<SkillLayer>,
) {
    val shape get() = layers.first { it.kind != "particles" }.kind
    val path get() = action
    val count get() = layers.first { it.kind != "particles" }.count
    val radius get() = layers.first { it.kind != "particles" }.radius
    val impact get() = key
    val impactSeconds get() = (end - hit - .01).toFloat()
}

internal object SkillVfx {
    val recipes = listOf(
        SkillRecipe("sproutStrike", "嫩芽击", Element.GRASS, "bud", 0.65, 1.3, 2.2, listOf(
            SkillLayer("particles", "背芽吸收花粉", 0.0, 0.7, ease = "easeInCubic", anchor = "source", count = 24, radius = 0.42, width = 0.03, life = 0.42, mode = "gather", socket = "bud"),
            SkillLayer("leaf", "嫩叶舒展", 0.25, 0.85, ease = "easeOutBack", anchor = "source", count = 1, radius = 0.38, width = 0.03, life = 0.5, socket = "bud"),
            SkillLayer("leaves", "弹性叶刃", 0.65, 1.32, ease = "easeOutCubic", anchor = "source", count = 1, radius = 0.4, width = 0.03, life = 0.5, mode = "travel"),
            SkillLayer("ribbon", "细叶光尾", 0.7, 1.35, ease = "linear", anchor = "source", count = 1, radius = 0.13, width = 0.025, life = 0.5, mode = "travel"),
            SkillLayer("slash", "目标叶切", 1.3, 1.6, ease = "easeOutCubic", anchor = "target", count = 1, radius = 0.45, width = 0.03, life = 0.5),
            SkillLayer("particles", "碎叶回落", 1.3, 2.2, ease = "linear", anchor = "target", count = 18, radius = 0.36, width = 0.03, life = 0.9, mode = "burst", shape = "leaf"),
        )),
        SkillRecipe("seedVolley", "种子连射", Element.GRASS, "bud", 0.55, 1.35, 2.25, listOf(
            SkillLayer("particles", "荚口花粉", 0.0, 0.6, ease = "easeInQuad", anchor = "source", count = 18, radius = 0.3, width = 0.03, life = 0.4, mode = "gather", socket = "bud"),
            SkillLayer("seeds", "三枚连续种子", 0.55, 1.38, ease = "easeOutQuad", anchor = "source", count = 3, radius = 0.09, width = 0.03, life = 0.5, mode = "volley", interval = 0.18, flight = 0.44),
            SkillLayer("particles", "种子尾屑", 0.55, 1.4, ease = "linear", anchor = "source", count = 30, radius = 0.1, width = 0.03, life = 0.2, mode = "trail"),
            SkillLayer("particles", "三次种皮裂解", 0.99, 2.25, ease = "linear", anchor = "target", count = 24, radius = 0.28, width = 0.03, life = 0.6, mode = "burst", shape = "seed", interval = 0.18, pulses = 3),
        )),
        SkillRecipe("vineWhip", "藤鞭", Element.GRASS, "whip", 0.95, 1.65, 2.8, listOf(
            SkillLayer("particles", "芽心蓄势", 0.0, 0.9, ease = "easeInCubic", anchor = "source", count = 26, radius = 0.48, width = 0.03, life = 0.55, mode = "gather", socket = "bud"),
            SkillLayer("vines", "双藤生长与抽甩", 0.25, 2.8, ease = "easeInOutCubic", anchor = "source", count = 2, radius = 0.045, width = 0.03, life = 0.5, mode = "whip"),
            SkillLayer("ribbon", "藤尖速度弧", 0.95, 1.8, ease = "linear", anchor = "source", count = 2, radius = 0.55, width = 0.028, life = 0.5, mode = "whip"),
            SkillLayer("slash", "交叉鞭痕", 1.65, 2.02, ease = "easeOutQuad", anchor = "target", count = 2, radius = 0.58, width = 0.03, life = 0.5),
            SkillLayer("particles", "藤叶抖落", 1.6, 2.65, ease = "linear", anchor = "target", count = 20, radius = 0.4, width = 0.03, life = 0.9, mode = "burst", shape = "leaf"),
        )),
        SkillRecipe("seedBomb", "种子炸弹", Element.GRASS, "lob", 1.1, 2.05, 3.2, listOf(
            SkillLayer("particles", "花粉汇入", 0.0, 0.85, ease = "easeInCubic", anchor = "source", count = 38, radius = 0.55, width = 0.03, life = 0.6, mode = "gather", socket = "bud"),
            SkillLayer("orb", "种皮球成形", 0.55, 1.1, ease = "easeInOutBack", anchor = "source", count = 1, radius = 0.16, width = 0.03, life = 0.5, socket = "bud", shape = "seed"),
            SkillLayer("seeds", "种子高抛", 1.1, 2.05, ease = "linear", anchor = "source", count = 1, radius = 0.16, width = 0.03, life = 0.5, mode = "lob", flight = 0.95, height = 1.0),
            SkillLayer("particles", "种皮六瓣爆裂", 2.05, 3.2, ease = "linear", anchor = "target", count = 18, radius = 0.7, width = 0.03, life = 1.15, mode = "burst", shape = "seed"),
            SkillLayer("leaves", "萌芽放射", 2.05, 2.6, ease = "easeOutBack", anchor = "target", count = 8, radius = 0.24, width = 0.03, life = 0.5, mode = "burst"),
            SkillLayer("particles", "低位粉尘", 2.05, 3.1, ease = "linear", anchor = "target", count = 28, radius = 0.6, width = 0.03, life = 0.8, mode = "ground"),
        )),
        SkillRecipe("hardRush", "硬化冲撞", Element.ROCK, "rush", 0.85, 1.45, 2.55, listOf(
            SkillLayer("armor", "岩甲贴合", 0.15, 2.4, ease = "easeOutCubic", anchor = "source", count = 6, radius = 0.43, width = 0.03, life = 0.5, mode = "rock"),
            SkillLayer("particles", "缝隙岩火星", 0.4, 1.5, ease = "linear", anchor = "source", count = 18, radius = 0.4, width = 0.03, life = 0.35, mode = "orbit"),
            SkillLayer("ribbon", "肩部速度线", 0.85, 1.48, ease = "linear", anchor = "source", count = 3, radius = 0.18, width = 0.025, life = 0.5, mode = "body"),
            SkillLayer("particles", "蹬地尘尾", 0.85, 1.6, ease = "linear", anchor = "body", count = 32, radius = 0.2, width = 0.03, life = 0.5, mode = "trail"),
            SkillLayer("particles", "接触碎岩", 1.45, 2.4, ease = "linear", anchor = "target", count = 22, radius = 0.5, width = 0.03, life = 0.9, mode = "burst", shape = "rock"),
        )),
        SkillRecipe("woodHammer", "木槌", Element.GRASS, "hammer", 1.2, 1.9, 3.15, listOf(
            SkillLayer("vines", "木槌支撑藤", 0.15, 2.8, ease = "easeInOutCubic", anchor = "source", count = 1, radius = 0.055, width = 0.03, life = 0.5, mode = "hammer"),
            SkillLayer("hammer", "年轮实体木槌", 0.45, 2.75, ease = "easeInCubic", anchor = "source", count = 1, radius = 0.32, width = 0.03, life = 0.5),
            SkillLayer("ribbon", "槌头掠风", 1.25, 1.94, ease = "linear", anchor = "source", count = 1, radius = 0.9, width = 0.06, life = 0.5, mode = "hammer"),
            SkillLayer("particles", "落点木屑", 1.9, 3.15, ease = "linear", anchor = "target", count = 28, radius = 0.65, width = 0.03, life = 1.1, mode = "burst", shape = "wood"),
            SkillLayer("shock", "压地尘圈", 1.9, 2.65, ease = "easeOutCubic", anchor = "target", count = 1, radius = 0.72, width = 0.07, life = 0.5),
        )),
        SkillRecipe("roar", "怒吼", Element.NORMAL, "roar", 0.55, 1.15, 2.1, listOf(
            SkillLayer("particles", "吸气气流", 0.0, 0.55, ease = "easeInQuad", anchor = "source", count = 20, radius = 0.42, width = 0.03, life = 0.35, mode = "gather", socket = "mouth"),
            SkillLayer("sound", "宽带声压壳", 0.55, 1.6, ease = "easeOutQuad", anchor = "source", count = 2, radius = 0.7, width = 0.08, life = 0.5, mode = "roar", interval = 0.18),
            SkillLayer("particles", "低位扬尘", 0.65, 1.9, ease = "linear", anchor = "source", count = 34, radius = 0.5, width = 0.03, life = 0.6, mode = "travel", shape = "dust"),
            SkillLayer("shock", "目标受压轮廓", 1.15, 1.6, ease = "easeOutQuad", anchor = "target", count = 1, radius = 0.45, width = 0.035, life = 0.5),
        )),
        SkillRecipe("bubble", "水泡", Element.WATER, "spit", 0.65, 1.55, 2.6, listOf(
            SkillLayer("particles", "口前凝水", 0.0, 0.65, ease = "easeInQuad", anchor = "source", count = 20, radius = 0.3, width = 0.03, life = 0.4, mode = "gather", socket = "mouth", shape = "drop"),
            SkillLayer("bubbles", "七枚漂浮水泡", 0.45, 1.65, ease = "easeInOutSine", anchor = "source", count = 7, radius = 0.14, width = 0.03, life = 0.5, mode = "travel"),
            SkillLayer("particles", "泡群细滴", 0.65, 1.65, ease = "linear", anchor = "source", count = 24, radius = 0.14, width = 0.03, life = 0.35, mode = "trail", shape = "drop"),
            SkillLayer("bubbles", "破裂膜环", 1.55, 1.86, ease = "easeOutCubic", anchor = "target", count = 7, radius = 0.25, width = 0.03, life = 0.5, mode = "pop"),
            SkillLayer("particles", "破泡水花", 1.55, 2.6, ease = "linear", anchor = "target", count = 42, radius = 0.45, width = 0.03, life = 0.85, mode = "burst", shape = "drop"),
        )),
        SkillRecipe("shellRush", "龟壳冲撞", Element.WATER, "shell", 0.9, 1.6, 2.8, listOf(
            SkillLayer("ring", "壳缘水切", 0.3, 1.8, ease = "easeInOutSine", anchor = "body", count = 1, radius = 0.38, width = 0.028, life = 0.5, mode = "shell"),
            SkillLayer("ribbon", "旋转壳尾迹", 0.9, 1.66, ease = "linear", anchor = "source", count = 2, radius = 0.25, width = 0.04, life = 0.5, mode = "body"),
            SkillLayer("particles", "壳缘甩水", 0.9, 2.2, ease = "linear", anchor = "body", count = 32, radius = 0.35, width = 0.03, life = 0.55, mode = "trail", shape = "drop"),
            SkillLayer("particles", "碰撞水扇", 1.6, 2.6, ease = "linear", anchor = "target", count = 36, radius = 0.65, width = 0.03, life = 0.85, mode = "burst", shape = "drop"),
        )),
        SkillRecipe("bite", "咬碎", Element.NORMAL, "bite", 0.65, 1.2, 2.3, listOf(
            SkillLayer("jaws", "上下双牙列", 0.68, 1.65, ease = "easeInCubic", anchor = "target", count = 8, radius = 0.38, width = 0.03, life = 0.5, mode = "bite"),
            SkillLayer("ribbon", "近身气流", 0.65, 1.2, ease = "linear", anchor = "source", count = 2, radius = 0.14, width = 0.018, life = 0.5, mode = "body"),
            SkillLayer("particles", "咬合碎屑", 1.2, 2.15, ease = "linear", anchor = "target", count = 16, radius = 0.3, width = 0.03, life = 0.65, mode = "burst", shape = "rock"),
            SkillLayer("slash", "短促压力痕", 1.2, 1.48, ease = "easeOutQuad", anchor = "target", count = 2, radius = 0.28, width = 0.04, life = 0.5),
        )),
        SkillRecipe("tide", "潮汐", Element.WATER, "wave", 1.15, 2.15, 3.5, listOf(
            SkillLayer("pool", "脚边蓄水", 0.0, 1.4, ease = "easeInOutSine", anchor = "body", count = 1, radius = 0.62, width = 0.035, life = 0.5),
            SkillLayer("wave", "低位潮头", 1.15, 2.75, ease = "easeInOutSine", anchor = "source", count = 1, radius = 0.65, width = 1.3, life = 0.5, mode = "tide"),
            SkillLayer("particles", "浪脊泡沫", 1.15, 2.8, ease = "linear", anchor = "source", count = 70, radius = 0.65, width = 0.03, life = 0.55, mode = "crest", shape = "foam"),
            SkillLayer("particles", "潮头落水", 2.15, 3.5, ease = "linear", anchor = "target", count = 54, radius = 0.7, width = 0.03, life = 1.05, mode = "burst", shape = "drop"),
            SkillLayer("pool", "回水余纹", 2.2, 3.5, ease = "easeOutQuad", anchor = "target", count = 1, radius = 0.75, width = 0.03, life = 0.5, mode = "return"),
        )),
        SkillRecipe("withdraw", "缩壳", Element.ROCK, "withdraw", 0.85, 1.35, 2.6, listOf(
            SkillLayer("armor", "收壳岩纹", 0.15, 2.3, ease = "easeInOutCubic", anchor = "body", count = 6, radius = 0.34, width = 0.03, life = 0.5, mode = "shellRock"),
            SkillLayer("ring", "壳心压缩环", 0.3, 1.1, ease = "easeInCubic", anchor = "body", count = 1, radius = 0.4, width = 0.025, life = 0.5, mode = "compress"),
            SkillLayer("sound", "反弹压力", 1.05, 1.6, ease = "easeOutCubic", anchor = "source", count = 1, radius = 0.38, width = 0.04, life = 0.5, mode = "low"),
            SkillLayer("particles", "微量岩屑", 1.35, 2.25, ease = "linear", anchor = "target", count = 12, radius = 0.28, width = 0.03, life = 0.65, mode = "burst", shape = "rock"),
        )),
        SkillRecipe("surf", "冲浪", Element.WATER, "surf", 1.05, 2.25, 3.75, listOf(
            SkillLayer("pool", "汇水浪板", 0.0, 1.2, ease = "easeInOutSine", anchor = "source", count = 1, radius = 0.75, width = 0.03, life = 0.5),
            SkillLayer("wave", "承载卷浪", 0.55, 2.95, ease = "easeInOutCubic", anchor = "source", count = 1, radius = 0.8, width = 1.4, life = 0.5, mode = "surf"),
            SkillLayer("particles", "浪头白沫", 1.05, 3.1, ease = "linear", anchor = "source", count = 90, radius = 0.8, width = 0.03, life = 0.6, mode = "crest", shape = "foam"),
            SkillLayer("particles", "破浪大水花", 2.25, 3.75, ease = "linear", anchor = "target", count = 72, radius = 0.85, width = 0.03, life = 1.15, mode = "burst", shape = "drop"),
            SkillLayer("pool", "浪退水面", 2.45, 3.75, ease = "easeOutSine", anchor = "target", count = 1, radius = 0.9, width = 0.03, life = 0.5, mode = "return"),
        )),
        SkillRecipe("hydroCannon", "加农水炮", Element.WATER, "cannon", 3.9, 4.65, 6.15, listOf(
            SkillLayer("particles", "三秒光滴吸收", 0.0, 3.0, ease = "easeInCubic", anchor = "source", count = 96, radius = 0.8, width = 0.03, life = 0.8, mode = "gather", socket = "chest", shape = "drop", interval = 0.15, batch = 8, emitEnd = 3.0),
            SkillLayer("orb", "弹性高压水球", 3.0, 4.05, ease = "easeInOutElastic", anchor = "source", count = 1, radius = 0.25, width = 0.03, life = 0.5, socket = "mouth"),
            SkillLayer("jet", "白青高压柱", 3.9, 5.4, ease = "easeOutCubic", anchor = "source", count = 1, radius = 0.115, width = 0.03, life = 0.5, mode = "cannon"),
            SkillLayer("helix", "双层旋转外流", 3.9, 5.4, ease = "linear", anchor = "source", count = 2, radius = 0.18, width = 0.022, life = 0.5, turns = 4.0),
            SkillLayer("particles", "柱周发散细滴", 3.9, 5.65, ease = "linear", anchor = "source", count = 130, radius = 0.3, width = 0.03, life = 0.45, mode = "beam", shape = "drop"),
            SkillLayer("particles", "持续接触水扇", 4.65, 6.15, ease = "linear", anchor = "target", count = 110, radius = 0.95, width = 0.03, life = 0.8, mode = "spray", shape = "drop", emitEnd = 5.15),
            SkillLayer("particles", "水雾沉降", 5.1, 6.15, ease = "linear", anchor = "target", count = 64, radius = 0.85, width = 0.03, life = 1.05, mode = "mist"),
        )),
        SkillRecipe("ironWall", "铁壁", Element.ROCK, "guard", 1.0, 1.65, 3.0, listOf(
            SkillLayer("armor", "三片金属盾", 0.2, 2.55, ease = "easeOutCubic", anchor = "source", count = 3, radius = 0.48, width = 0.03, life = 0.5, mode = "metal"),
            SkillLayer("particles", "闭合接缝火星", 0.7, 1.25, ease = "linear", anchor = "body", count = 20, radius = 0.42, width = 0.03, life = 0.25, mode = "orbit"),
            SkillLayer("sound", "平面压力", 1.1, 1.9, ease = "easeOutQuad", anchor = "source", count = 1, radius = 0.5, width = 0.06, life = 0.5, mode = "wall"),
            SkillLayer("particles", "受压金屑", 1.65, 2.6, ease = "linear", anchor = "target", count = 20, radius = 0.4, width = 0.03, life = 0.7, mode = "burst", shape = "spark"),
        )),
        SkillRecipe("quickAttack", "疾冲", Element.NORMAL, "dash", 0.38, 0.82, 1.75, listOf(
            SkillLayer("particles", "起步空气", 0.0, 0.38, ease = "easeInQuad", anchor = "source", count = 12, radius = 0.25, width = 0.03, life = 0.2, mode = "gather"),
            SkillLayer("ribbon", "三条疾速尾迹", 0.38, 0.98, ease = "linear", anchor = "source", count = 3, radius = 0.16, width = 0.02, life = 0.5, mode = "body"),
            SkillLayer("particles", "后足尘点", 0.38, 1.2, ease = "linear", anchor = "body", count = 24, radius = 0.18, width = 0.03, life = 0.4, mode = "trail"),
            SkillLayer("flash", "单次接触星", 0.82, 1.08, ease = "easeOutCubic", anchor = "target", count = 5, radius = 0.28, width = 0.03, life = 0.5),
        )),
        SkillRecipe("wingToss", "振翼", Element.NORMAL, "wings", 0.7, 1.5, 2.65, listOf(
            SkillLayer("ribbon", "翼尖预备弧", 0.15, 0.75, ease = "linear", anchor = "source", count = 2, radius = 0.45, width = 0.018, life = 0.5, mode = "wings"),
            SkillLayer("wind", "双月牙风刃", 0.7, 1.55, ease = "easeOutQuad", anchor = "source", count = 2, radius = 0.45, width = 0.05, life = 0.5, mode = "travel"),
            SkillLayer("particles", "风刃随行丝", 0.7, 1.85, ease = "linear", anchor = "source", count = 32, radius = 0.3, width = 0.03, life = 0.4, mode = "trail", shape = "feather"),
            SkillLayer("wind", "接触风切", 1.5, 1.95, ease = "easeOutCubic", anchor = "target", count = 2, radius = 0.6, width = 0.025, life = 0.5, mode = "cut"),
        )),
        SkillRecipe("beak", "啄击", Element.NORMAL, "peck", 0.65, 1.25, 2.35, listOf(
            SkillLayer("orb", "喙尖锐光", 0.3, 1.32, ease = "easeOutQuad", anchor = "source", count = 1, radius = 0.035, width = 0.03, life = 0.5, socket = "mouth"),
            SkillLayer("ribbon", "点刺短线", 0.65, 1.38, ease = "linear", anchor = "source", count = 1, radius = 0.06, width = 0.014, life = 0.5, mode = "body"),
            SkillLayer("flash", "三次刺点", 0.89, 1.57, ease = "easeOutCubic", anchor = "target", count = 4, radius = 0.18, width = 0.03, life = 0.5, interval = 0.18, pulses = 3),
            SkillLayer("particles", "少量羽屑", 1.25, 2.2, ease = "linear", anchor = "target", count = 10, radius = 0.25, width = 0.03, life = 0.8, mode = "burst", shape = "feather"),
        )),
        SkillRecipe("dive", "俯冲", Element.ROCK, "dive", 1.15, 1.75, 3.0, listOf(
            SkillLayer("ribbon", "升空翼风", 0.15, 1.2, ease = "linear", anchor = "source", count = 2, radius = 0.25, width = 0.022, life = 0.5, mode = "body"),
            SkillLayer("ring", "目标落点", 0.65, 1.8, ease = "easeInOutSine", anchor = "target", count = 1, radius = 0.3, width = 0.018, life = 0.5, mode = "ground"),
            SkillLayer("ribbon", "俯冲长尾", 1.15, 1.82, ease = "linear", anchor = "source", count = 2, radius = 0.15, width = 0.04, life = 0.5, mode = "body"),
            SkillLayer("particles", "落点岩尘", 1.75, 3.0, ease = "linear", anchor = "target", count = 38, radius = 0.7, width = 0.03, life = 1.1, mode = "burst", shape = "rock"),
            SkillLayer("shock", "俯冲压地圈", 1.75, 2.45, ease = "easeOutCubic", anchor = "target", count = 1, radius = 0.65, width = 0.06, life = 0.5),
        )),
        SkillRecipe("screech", "尖啸", Element.NORMAL, "screech", 0.6, 1.4, 2.7, listOf(
            SkillLayer("particles", "喙前吸气细线", 0.0, 0.6, ease = "easeInQuad", anchor = "source", count = 18, radius = 0.22, width = 0.03, life = 0.25, mode = "gather", socket = "mouth"),
            SkillLayer("sound", "六片高频声壳", 0.6, 1.9, ease = "linear", anchor = "source", count = 6, radius = 0.42, width = 0.014, life = 0.5, mode = "screech", interval = 0.1),
            SkillLayer("helix", "细声束中轴", 0.6, 1.75, ease = "linear", anchor = "source", count = 1, radius = 0.06, width = 0.008, life = 0.5, turns = 8.0),
            SkillLayer("particles", "受声空气碎点", 1.4, 2.6, ease = "linear", anchor = "target", count = 22, radius = 0.35, width = 0.03, life = 0.7, mode = "mist"),
        )),
        SkillRecipe("crash", "撞击", Element.NORMAL, "rush", 0.65, 1.25, 2.4, listOf(
            SkillLayer("particles", "蓄力落尘", 0.0, 0.65, ease = "linear", anchor = "source", count = 14, radius = 0.26, width = 0.03, life = 0.3, mode = "ground"),
            SkillLayer("ribbon", "实体短尾", 0.65, 1.35, ease = "linear", anchor = "source", count = 2, radius = 0.2, width = 0.035, life = 0.5, mode = "body"),
            SkillLayer("flash", "宽肩接触闪", 1.25, 1.55, ease = "easeOutCubic", anchor = "target", count = 6, radius = 0.38, width = 0.03, life = 0.5),
            SkillLayer("particles", "撞击尘屑", 1.25, 2.35, ease = "linear", anchor = "target", count = 26, radius = 0.45, width = 0.03, life = 0.8, mode = "burst"),
        )),
        SkillRecipe("whirlpool", "漩涡", Element.WATER, "vortex", 1.05, 1.8, 3.8, listOf(
            SkillLayer("particles", "旋入水滴", 0.0, 1.05, ease = "linear", anchor = "source", count = 30, radius = 0.32, width = 0.03, life = 0.55, mode = "orbit", socket = "mouth", shape = "drop"),
            SkillLayer("ribbon", "引水弯线", 0.65, 1.4, ease = "linear", anchor = "source", count = 1, radius = 0.3, width = 0.04, life = 0.5, mode = "travel"),
            SkillLayer("vortex", "目标水漏斗", 1.05, 3.25, ease = "easeInOutCubic", anchor = "target", count = 1, radius = 0.65, width = 0.85, life = 0.5),
            SkillLayer("particles", "涡沿白沫", 1.1, 3.35, ease = "linear", anchor = "target", count = 88, radius = 0.65, width = 0.03, life = 0.65, mode = "vortex", shape = "foam"),
            SkillLayer("pool", "塌涡余纹", 2.75, 3.8, ease = "easeOutSine", anchor = "target", count = 1, radius = 0.7, width = 0.03, life = 0.5),
        )),
        SkillRecipe("claw", "爪击", Element.NORMAL, "claw", 0.7, 1.3, 2.5, listOf(
            SkillLayer("particles", "爪尖锐点", 0.2, 0.75, ease = "linear", anchor = "source", count = 9, radius = 0.08, width = 0.03, life = 0.25, mode = "orbit", socket = "paw"),
            SkillLayer("slash", "三道平行爪痕", 1.02, 1.68, ease = "easeInCubic", anchor = "target", count = 3, radius = 0.6, width = 0.035, life = 0.5),
            SkillLayer("ribbon", "挥爪空气丝", 0.9, 1.45, ease = "linear", anchor = "source", count = 2, radius = 0.25, width = 0.012, life = 0.5, mode = "claw"),
            SkillLayer("particles", "接触细屑", 1.3, 2.3, ease = "linear", anchor = "target", count = 18, radius = 0.4, width = 0.03, life = 0.75, mode = "burst"),
        )),
        SkillRecipe("rockSlide", "岩崩", Element.ROCK, "rocks", 1.05, 2.1, 3.7, listOf(
            SkillLayer("rocks", "五块斜落岩片", 0.25, 2.55, ease = "easeInQuad", anchor = "target", count = 5, radius = 0.2, width = 0.03, life = 0.5, mode = "slide"),
            SkillLayer("particles", "岩片成形细尘", 0.0, 1.05, ease = "easeInQuad", anchor = "target", count = 34, radius = 0.65, width = 0.03, life = 0.5, mode = "gather", shape = "dust"),
            SkillLayer("ribbon", "斜落尘带", 1.05, 2.2, ease = "linear", anchor = "target", count = 5, radius = 0.25, width = 0.035, life = 0.5, mode = "fall"),
            SkillLayer("particles", "连续碎石", 1.45, 3.6, ease = "linear", anchor = "target", count = 45, radius = 0.72, width = 0.03, life = 1.1, mode = "burst", shape = "rock", interval = 0.16, pulses = 5),
            SkillLayer("particles", "滑坡低尘", 1.45, 3.7, ease = "linear", anchor = "target", count = 50, radius = 0.9, width = 0.03, life = 1.1, mode = "ground"),
        )),
        SkillRecipe("pounce", "猛扑", Element.NORMAL, "pounce", 0.85, 1.55, 2.95, listOf(
            SkillLayer("ring", "蓄跳落脚圈", 0.2, 0.9, ease = "easeInQuad", anchor = "source", count = 1, radius = 0.28, width = 0.02, life = 0.5, mode = "compress"),
            SkillLayer("ribbon", "跃扑弧形气尾", 0.85, 1.65, ease = "linear", anchor = "source", count = 2, radius = 0.18, width = 0.025, life = 0.5, mode = "body"),
            SkillLayer("flash", "扑击接触星", 1.55, 1.9, ease = "easeOutQuad", anchor = "target", count = 5, radius = 0.4, width = 0.03, life = 0.5),
            SkillLayer("particles", "落脚尘扇", 1.55, 2.95, ease = "linear", anchor = "target", count = 36, radius = 0.65, width = 0.03, life = 1.1, mode = "burst"),
        )),
        SkillRecipe("rockfall", "落石", Element.ROCK, "rocks", 1.4, 2.2, 3.9, listOf(
            SkillLayer("particles", "巨岩聚尘", 0.0, 1.1, ease = "easeInCubic", anchor = "target", count = 50, radius = 0.75, width = 0.03, life = 0.65, mode = "gather"),
            SkillLayer("rocks", "单块巨岩", 0.35, 2.3, ease = "easeInQuad", anchor = "target", count = 1, radius = 0.38, width = 0.03, life = 0.5, mode = "fall"),
            SkillLayer("ribbon", "垂直落尘尾", 1.4, 2.23, ease = "linear", anchor = "target", count = 1, radius = 0.3, width = 0.08, life = 0.5, mode = "fall"),
            SkillLayer("particles", "巨岩八块碎裂", 2.2, 3.9, ease = "linear", anchor = "target", count = 40, radius = 0.9, width = 0.03, life = 1.4, mode = "burst", shape = "rock"),
            SkillLayer("shock", "重落地震尘圈", 2.2, 3.15, ease = "easeOutCubic", anchor = "target", count = 1, radius = 0.9, width = 0.09, life = 0.5),
        )),
        SkillRecipe("howl", "咆哮", Element.NORMAL, "howl", 0.9, 1.65, 3.0, listOf(
            SkillLayer("particles", "鬃毛边缘尘光", 0.25, 0.95, ease = "linear", anchor = "source", count = 22, radius = 0.25, width = 0.03, life = 0.35, mode = "orbit", socket = "head"),
            SkillLayer("sound", "三道低频咆哮", 0.9, 2.2, ease = "easeOutQuad", anchor = "source", count = 3, radius = 0.8, width = 0.065, life = 0.5, mode = "howl", interval = 0.22),
            SkillLayer("particles", "咆哮推尘", 1.0, 2.75, ease = "linear", anchor = "source", count = 44, radius = 0.7, width = 0.03, life = 0.75, mode = "travel", shape = "dust"),
            SkillLayer("shock", "目标脚边受压", 1.65, 2.35, ease = "easeOutCubic", anchor = "target", count = 1, radius = 0.55, width = 0.04, life = 0.5),
        )),
        SkillRecipe("ember", "火息", Element.FIRE, "breath", 0.85, 1.6, 3.0, listOf(
            SkillLayer("particles", "吸气暖火点", 0.0, 0.85, ease = "easeInCubic", anchor = "source", count = 28, radius = 0.35, width = 0.03, life = 0.45, mode = "gather", socket = "mouth"),
            SkillLayer("fire", "五瓣连续火息", 0.85, 2.3, ease = "easeOutCubic", anchor = "source", count = 5, radius = 0.32, width = 0.03, life = 0.5, mode = "breath"),
            SkillLayer("particles", "随热火星", 0.85, 2.8, ease = "linear", anchor = "source", count = 75, radius = 0.4, width = 0.03, life = 0.6, mode = "beam", shape = "ember"),
            SkillLayer("fire", "接触小火片", 1.6, 2.55, ease = "easeOutQuad", anchor = "target", count = 3, radius = 0.3, width = 0.03, life = 0.5, mode = "pillar"),
            SkillLayer("particles", "熄火薄烟", 2.05, 3.0, ease = "linear", anchor = "target", count = 26, radius = 0.4, width = 0.03, life = 0.85, mode = "mist"),
        )),
        SkillRecipe("dragonClaw", "龙爪", Element.FIRE, "claw", 1.0, 1.6, 3.0, listOf(
            SkillLayer("particles", "爪尖聚火", 0.0, 1.0, ease = "easeInCubic", anchor = "source", count = 30, radius = 0.25, width = 0.03, life = 0.5, mode = "gather", socket = "paw", shape = "ember"),
            SkillLayer("slash", "三道炽热龙爪", 1.22, 2.1, ease = "easeInCubic", anchor = "target", count = 3, radius = 0.7, width = 0.055, life = 0.5),
            SkillLayer("ribbon", "爪后橙焰带", 1.08, 1.8, ease = "linear", anchor = "source", count = 3, radius = 0.35, width = 0.035, life = 0.5, mode = "claw"),
            SkillLayer("particles", "切痕火星", 1.6, 3.0, ease = "linear", anchor = "target", count = 42, radius = 0.5, width = 0.03, life = 1.1, mode = "rise", shape = "ember"),
        )),
        SkillRecipe("tailWhip", "扫尾", Element.NORMAL, "tail", 0.85, 1.45, 2.8, listOf(
            SkillLayer("ribbon", "真实尾段扫风", 0.45, 1.9, ease = "easeInOutCubic", anchor = "source", count = 2, radius = 0.58, width = 0.045, life = 0.5, mode = "tail"),
            SkillLayer("wind", "扫尾接触弧", 1.2, 1.9, ease = "easeOutQuad", anchor = "target", count = 1, radius = 0.65, width = 0.035, life = 0.5, mode = "cut"),
            SkillLayer("particles", "扫起低尘", 1.3, 2.7, ease = "linear", anchor = "target", count = 30, radius = 0.65, width = 0.03, life = 0.85, mode = "ground"),
            SkillLayer("flash", "尾尖接触", 1.45, 1.7, ease = "easeOutCubic", anchor = "target", count = 4, radius = 0.23, width = 0.03, life = 0.5),
        )),
        SkillRecipe("blaze", "烈焰", Element.FIRE, "breath", 1.65, 2.5, 4.3, listOf(
            SkillLayer("particles", "深吸聚火", 0.0, 1.45, ease = "easeInCubic", anchor = "source", count = 65, radius = 0.62, width = 0.03, life = 0.7, mode = "gather", socket = "mouth", shape = "ember"),
            SkillLayer("orb", "口内压火芯", 0.55, 1.75, ease = "easeInOutCubic", anchor = "source", count = 1, radius = 0.17, width = 0.03, life = 0.5, socket = "mouth"),
            SkillLayer("fire", "七瓣宽烈焰", 1.65, 3.25, ease = "easeOutCubic", anchor = "source", count = 7, radius = 0.55, width = 0.03, life = 0.5, mode = "blaze"),
            SkillLayer("fire", "命中上卷火柱", 2.5, 3.85, ease = "easeOutCubic", anchor = "target", count = 5, radius = 0.55, width = 0.03, life = 0.5, mode = "pillar"),
            SkillLayer("particles", "烈焰浮火星", 1.65, 4.1, ease = "linear", anchor = "source", count = 110, radius = 0.65, width = 0.03, life = 0.85, mode = "beam", shape = "ember"),
            SkillLayer("particles", "余热烟灰", 3.3, 4.3, ease = "linear", anchor = "target", count = 46, radius = 0.7, width = 0.03, life = 1.0, mode = "mist"),
        )),
        SkillRecipe("spark", "电光", Element.ELECTRIC, "electric", 0.95, 1.5, 2.7, listOf(
            SkillLayer("particles", "体表静电聚点", 0.0, 0.95, ease = "easeInQuad", anchor = "source", count = 30, radius = 0.42, width = 0.03, life = 0.4, mode = "gather", socket = "head"),
            SkillLayer("bolt", "六折点主弧", 0.95, 1.9, ease = "easeOutCubic", anchor = "source", count = 1, radius = 0.13, width = 0.025, life = 0.5),
            SkillLayer("bolt", "两条间歇支弧", 1.08, 1.85, ease = "linear", anchor = "source", count = 2, radius = 0.3, width = 0.009, life = 0.5, mode = "branch"),
            SkillLayer("particles", "目标接触电点", 1.5, 2.65, ease = "linear", anchor = "target", count = 34, radius = 0.35, width = 0.03, life = 0.45, mode = "orbit", shape = "spark"),
        )),
        SkillRecipe("thunderFang", "雷牙", Element.ELECTRIC, "bite", 1.1, 1.65, 3.0, listOf(
            SkillLayer("particles", "牙位聚电", 0.0, 1.1, ease = "easeInCubic", anchor = "source", count = 30, radius = 0.3, width = 0.03, life = 0.5, mode = "gather", socket = "mouth", shape = "spark"),
            SkillLayer("jaws", "带电双牙列", 1.1, 2.1, ease = "easeInCubic", anchor = "target", count = 8, radius = 0.4, width = 0.03, life = 0.5, mode = "electric"),
            SkillLayer("bolt", "咬合桥接弧", 1.45, 2.15, ease = "linear", anchor = "target", count = 3, radius = 0.3, width = 0.018, life = 0.5, mode = "local"),
            SkillLayer("particles", "咬后电屑", 1.65, 3.0, ease = "linear", anchor = "target", count = 42, radius = 0.5, width = 0.03, life = 1.0, mode = "burst", shape = "spark"),
        )),
        SkillRecipe("overload", "超载", Element.ELECTRIC, "overload", 2.2, 2.85, 4.55, listOf(
            SkillLayer("particles", "密度渐增储电", 0.0, 2.2, ease = "easeInCubic", anchor = "source", count = 90, radius = 0.65, width = 0.03, life = 0.55, mode = "gather", socket = "chest", shape = "spark"),
            SkillLayer("bolt", "体表蓄电弧", 0.5, 2.35, ease = "linear", anchor = "body", count = 5, radius = 0.38, width = 0.014, life = 0.5, mode = "local"),
            SkillLayer("discharge", "膨胀放电壳", 2.2, 3.25, ease = "easeOutCubic", anchor = "source", count = 3, radius = 1.1, width = 0.025, life = 0.5),
            SkillLayer("bolt", "三条前向主弧", 2.2, 3.4, ease = "easeOutCubic", anchor = "source", count = 3, radius = 0.32, width = 0.04, life = 0.5),
            SkillLayer("particles", "过载接触散电", 2.85, 4.55, ease = "linear", anchor = "target", count = 95, radius = 0.85, width = 0.03, life = 1.3, mode = "burst", shape = "spark"),
        )),
        SkillRecipe("bugBite", "虫咬", Element.GRASS, "bite", 0.7, 1.25, 2.45, listOf(
            SkillLayer("jaws", "左右短颚", 0.55, 1.72, ease = "easeInCubic", anchor = "target", count = 2, radius = 0.24, width = 0.03, life = 0.5, mode = "bug"),
            SkillLayer("ribbon", "虫头短气线", 0.7, 1.4, ease = "linear", anchor = "source", count = 2, radius = 0.1, width = 0.012, life = 0.5, mode = "body"),
            SkillLayer("particles", "夹咬碎叶", 1.25, 2.35, ease = "linear", anchor = "target", count = 16, radius = 0.32, width = 0.03, life = 0.8, mode = "burst", shape = "leaf"),
            SkillLayer("flash", "颚尖接触", 1.25, 1.5, ease = "easeOutCubic", anchor = "target", count = 4, radius = 0.16, width = 0.03, life = 0.5),
        )),
        SkillRecipe("shellGuard", "硬壳", Element.ROCK, "guard", 0.95, 1.6, 2.9, listOf(
            SkillLayer("armor", "六块硬壳岩板", 0.2, 2.55, ease = "easeInOutCubic", anchor = "source", count = 6, radius = 0.43, width = 0.03, life = 0.5, mode = "rock"),
            SkillLayer("particles", "锁壳接缝屑", 0.6, 1.15, ease = "linear", anchor = "body", count = 18, radius = 0.38, width = 0.03, life = 0.3, mode = "orbit", shape = "rock"),
            SkillLayer("sound", "胸前岩压", 1.0, 1.9, ease = "easeOutQuad", anchor = "source", count = 1, radius = 0.48, width = 0.055, life = 0.5, mode = "low"),
            SkillLayer("particles", "岩压落屑", 1.6, 2.8, ease = "linear", anchor = "target", count = 22, radius = 0.42, width = 0.03, life = 0.9, mode = "burst", shape = "rock"),
        )),
        SkillRecipe("earthquake", "地震", Element.ROCK, "stomp", 1.25, 2.15, 4.15, listOf(
            SkillLayer("particles", "重踏前震尘", 0.0, 1.25, ease = "linear", anchor = "source", count = 26, radius = 0.45, width = 0.03, life = 0.5, mode = "ground"),
            SkillLayer("quake", "五道传播地裂", 1.25, 3.45, ease = "linear", anchor = "source", count = 5, radius = 0.6, width = 0.04, life = 0.5),
            SkillLayer("rocks", "逐段抬升岩片", 1.25, 3.5, ease = "easeInOutSine", anchor = "source", count = 14, radius = 0.11, width = 0.03, life = 0.5, mode = "quake"),
            SkillLayer("particles", "地裂行进尘", 1.25, 3.75, ease = "linear", anchor = "source", count = 88, radius = 0.65, width = 0.03, life = 0.9, mode = "groundTravel", shape = "dust"),
            SkillLayer("shock", "目标落地压圈", 2.15, 3.15, ease = "easeOutCubic", anchor = "target", count = 1, radius = 0.7, width = 0.08, life = 0.5),
            SkillLayer("particles", "震后沉尘", 2.5, 4.15, ease = "linear", anchor = "target", count = 40, radius = 0.8, width = 0.03, life = 1.2, mode = "mist"),
        )),
        SkillRecipe("chargeUp", "蓄气", Element.NORMAL, "inflate", 1.75, 2.4, 3.85, listOf(
            SkillLayer("particles", "间歇空气吸收", 0.0, 1.75, ease = "easeInCubic", anchor = "source", count = 48, radius = 0.6, width = 0.03, life = 0.6, mode = "gather", socket = "chest"),
            SkillLayer("ring", "胸前蓄压细环", 0.55, 1.85, ease = "easeInOutSine", anchor = "body", count = 1, radius = 0.45, width = 0.02, life = 0.5, mode = "compress"),
            SkillLayer("sound", "弹压气团", 1.75, 2.85, ease = "easeOutCubic", anchor = "source", count = 2, radius = 0.5, width = 0.035, life = 0.5, mode = "charge", interval = 0.12),
            SkillLayer("particles", "目标余气", 2.4, 3.85, ease = "linear", anchor = "target", count = 32, radius = 0.55, width = 0.03, life = 1.05, mode = "burst"),
        )),
    )
    private val byName = recipes.associateBy { it.name }
    fun recipe(move: Move): SkillRecipe = requireNotNull(byName[move.name]) { "缺少技能演出：${move.name}" }
    fun impactDelay(move: Move): Float = recipe(move).hit.toFloat()
    fun duration(move: Move): Float = recipe(move).end.toFloat()
    fun color(move: Move): String = when (move.element) {
        Element.GRASS -> "0x74DB66FF"
        Element.WATER -> "0x49C8EEFF"
        Element.FIRE -> "0xFF9E36FF"
        Element.ELECTRIC -> "0xF8DC53FF"
        Element.ROCK -> "0xBCA176FF"
        Element.NORMAL -> "0xD8D9D8FF"
    }
}
