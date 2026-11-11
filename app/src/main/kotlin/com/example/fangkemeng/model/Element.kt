package com.example.fangkemeng.model

/** 六属性：决定克制关系、招式配色与粒子类型。 */
enum class Element(val displayName: String) {
    GRASS("草"),
    WATER("水"),
    FIRE("火"),
    ELECTRIC("电"),
    ROCK("岩"),
    NORMAL("普通"),
}

/** 属性克制表：行=攻击方，列=防御方；未列出的组合为 1.0。 */
object TypeChart {
    private val strong: Map<Pair<Element, Element>, Float> = mapOf(
        (Element.GRASS to Element.WATER) to 2f,
        (Element.GRASS to Element.ROCK) to 2f,
        (Element.WATER to Element.FIRE) to 2f,
        (Element.WATER to Element.ROCK) to 2f,
        (Element.FIRE to Element.GRASS) to 2f,
        (Element.FIRE to Element.ROCK) to 2f,
        (Element.ELECTRIC to Element.WATER) to 2f,
        (Element.ROCK to Element.FIRE) to 2f,
        (Element.ROCK to Element.ELECTRIC) to 2f,
    )

    private val weak: Map<Pair<Element, Element>, Float> = mapOf(
        (Element.GRASS to Element.GRASS) to 0.5f,
        (Element.GRASS to Element.FIRE) to 0.5f,
        (Element.WATER to Element.WATER) to 0.5f,
        (Element.WATER to Element.GRASS) to 0.5f,
        (Element.FIRE to Element.WATER) to 0.5f,
        (Element.FIRE to Element.FIRE) to 0.5f,
        (Element.FIRE to Element.ROCK) to 0.5f,
        (Element.ELECTRIC to Element.GRASS) to 0.5f,
        (Element.ELECTRIC to Element.ELECTRIC) to 0.5f,
        (Element.ROCK to Element.ROCK) to 0.5f,
        (Element.NORMAL to Element.ROCK) to 0.5f,
    )

    fun multiplier(attack: Element, defend: Element): Float =
        strong[attack to defend] ?: weak[attack to defend] ?: 1f
}

/** 状态异常：战斗结束清除，不入存档。 */
enum class StatusEffect(val displayName: String) {
    BURN("灼烧"),
    POISON("中毒"),
    PARALYZE("麻痹"),
}
