package com.example.fangkemeng.model

/** 图鉴：12 只精灵、招式池、配队与实例生成。程序化动物，统一攻击入口。 */
object DemoData {
    /** 动画入口保留统一字段，程序化动物按场景层内部状态机响应。 */
    const val ATTACK_CLIP = "procedural_attack"

    // ─── 招式池 ───

    private val sproutStrike = Move("嫩芽击", 35, ATTACK_CLIP, Element.GRASS, cost = 1, accuracy = 100)
    private val seedVolley = Move("种子连射", 44, ATTACK_CLIP, Element.GRASS, cost = 2, accuracy = 100)
    private val vineWhip = Move("藤鞭", 55, ATTACK_CLIP, Element.GRASS, cost = 3, accuracy = 95, status = StatusEffect.POISON, statusChance = 0.2f)
    private val seedBomb = Move("种子炸弹", 60, ATTACK_CLIP, Element.GRASS, cost = 3, accuracy = 90)
    private val hardRush = Move("硬化冲撞", 45, ATTACK_CLIP, Element.ROCK, cost = 2, accuracy = 95)
    private val woodHammer = Move("木槌", 80, ATTACK_CLIP, Element.GRASS, cost = 3, accuracy = 85)
    private val roar = Move("怒吼", 20, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)

    private val bubble = Move("水泡", 44, ATTACK_CLIP, Element.WATER, cost = 2, accuracy = 100)
    private val shellRush = Move("龟壳冲撞", 55, ATTACK_CLIP, Element.WATER, cost = 3, accuracy = 90)
    private val bite = Move("咬碎", 42, ATTACK_CLIP, Element.NORMAL, cost = 2, accuracy = 95)
    private val tide = Move("潮汐", 65, ATTACK_CLIP, Element.WATER, cost = 3, accuracy = 85, status = StatusEffect.POISON, statusChance = 0.2f)
    private val withdraw = Move("缩壳", 30, ATTACK_CLIP, Element.ROCK, cost = 1, accuracy = 100)
    private val surf = Move("冲浪", 75, ATTACK_CLIP, Element.WATER, cost = 3, accuracy = 90)
    private val hydroCannon = Move("加农水炮", 85, ATTACK_CLIP, Element.WATER, cost = 3, accuracy = 80)
    private val ironWall = Move("铁壁", 25, ATTACK_CLIP, Element.ROCK, cost = 1, accuracy = 100)

    private val quickAttack = Move("疾冲", 35, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)
    private val wingToss = Move("振翼", 44, ATTACK_CLIP, Element.NORMAL, cost = 2, accuracy = 100)
    private val beak = Move("啄击", 50, ATTACK_CLIP, Element.NORMAL, cost = 2, accuracy = 95)
    private val dive = Move("俯冲", 60, ATTACK_CLIP, Element.ROCK, cost = 3, accuracy = 85, status = StatusEffect.PARALYZE, statusChance = 0.15f)
    private val screech = Move("尖啸", 25, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)

    private val crash = Move("撞击", 35, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)
    private val whirlpool = Move("漩涡", 40, ATTACK_CLIP, Element.WATER, cost = 2, accuracy = 95)

    private val claw = Move("爪击", 42, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)
    private val rockSlide = Move("岩崩", 60, ATTACK_CLIP, Element.ROCK, cost = 3, accuracy = 85)
    private val pounce = Move("猛扑", 55, ATTACK_CLIP, Element.NORMAL, cost = 2, accuracy = 90)
    private val rockfall = Move("落石", 70, ATTACK_CLIP, Element.ROCK, cost = 3, accuracy = 80, status = StatusEffect.PARALYZE, statusChance = 0.15f)
    private val howl = Move("咆哮", 20, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)

    private val ember = Move("火息", 50, ATTACK_CLIP, Element.FIRE, cost = 2, accuracy = 95, status = StatusEffect.BURN, statusChance = 0.2f)
    private val dragonClaw = Move("龙爪", 60, ATTACK_CLIP, Element.FIRE, cost = 3, accuracy = 90)
    private val tailWhip = Move("扫尾", 38, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)
    private val blaze = Move("烈焰", 75, ATTACK_CLIP, Element.FIRE, cost = 3, accuracy = 80, status = StatusEffect.BURN, statusChance = 0.2f)

    private val spark = Move("电光", 45, ATTACK_CLIP, Element.ELECTRIC, cost = 2, accuracy = 100, status = StatusEffect.PARALYZE, statusChance = 0.25f)
    private val thunderFang = Move("雷牙", 60, ATTACK_CLIP, Element.ELECTRIC, cost = 3, accuracy = 85, status = StatusEffect.PARALYZE, statusChance = 0.25f)
    private val overload = Move("超载", 85, ATTACK_CLIP, Element.ELECTRIC, cost = 3, accuracy = 75, status = StatusEffect.PARALYZE, statusChance = 0.25f)
    private val bugBite = Move("虫咬", 40, ATTACK_CLIP, Element.GRASS, cost = 2, accuracy = 100, status = StatusEffect.POISON, statusChance = 0.2f)
    private val shellGuard = Move("硬壳", 30, ATTACK_CLIP, Element.ROCK, cost = 1, accuracy = 100)
    private val earthquake = Move("地震", 80, ATTACK_CLIP, Element.ROCK, cost = 3, accuracy = 80)
    private val chargeUp = Move("蓄气", 20, ATTACK_CLIP, Element.NORMAL, cost = 1, accuracy = 100)

    // ─── 物种 ───

    val seedCubSpecies = Species("方块种子兽", CreatureId.SEED_CUB, Element.GRASS)
    val vineCubSpecies = Species("藤甲兽", CreatureId.VINE_CUB, Element.GRASS)
    val waterTurtleSpecies = Species("方块水龟", CreatureId.WATER_TURTLE, Element.WATER)
    val tideTurtleSpecies = Species("潮甲龟", CreatureId.TIDE_TURTLE, Element.WATER)
    val birdSpecies = Species("警觉鸟", CreatureId.BIRD, Element.NORMAL)
    val fishSpecies = Species("浮游鱼", CreatureId.FISH, Element.WATER)
    val lionSpecies = Species("鬃毛狮", CreatureId.LION, Element.ROCK)
    val dragonSpecies = Species("幼火龙", CreatureId.DRAGON, Element.FIRE)
    val catSpecies = Species("顽皮猫", CreatureId.CAT, Element.ELECTRIC)
    val golemSpecies = Species("岩块兽", CreatureId.GOLEM, Element.ROCK)
    val sparkBugSpecies = Species("电光虫", CreatureId.SPARK_BUG, Element.ELECTRIC)
    val puffSpecies = Species("云绒兽", CreatureId.PUFF, Element.NORMAL)

    /** 图鉴顺序：解锁与商店都按此顺序。 */
    fun availableSpecies(): List<Species> = listOf(
        seedCubSpecies,
        vineCubSpecies,
        waterTurtleSpecies,
        tideTurtleSpecies,
        birdSpecies,
        fishSpecies,
        lionSpecies,
        dragonSpecies,
        catSpecies,
        golemSpecies,
        sparkBugSpecies,
        puffSpecies,
    )

    fun speciesOf(id: String): Species? =
        availableSpecies().firstOrNull { it.creatureId.name.equals(id, ignoreCase = true) }

    /** 每只精灵的 5 招可学池；出战装配前 3 招。 */
    fun movesFor(creatureId: CreatureId): List<Move> = when (creatureId) {
        CreatureId.SEED_CUB -> listOf(sproutStrike, seedVolley, vineWhip, seedBomb, hardRush)
        CreatureId.VINE_CUB -> listOf(vineWhip, seedBomb, woodHammer, hardRush, roar)
        CreatureId.WATER_TURTLE -> listOf(bubble, shellRush, bite, tide, withdraw)
        CreatureId.TIDE_TURTLE -> listOf(tide, surf, hydroCannon, bite, ironWall)
        CreatureId.BIRD -> listOf(quickAttack, wingToss, beak, dive, screech)
        CreatureId.FISH -> listOf(bubble, surf, crash, whirlpool, tide)
        CreatureId.LION -> listOf(claw, rockSlide, pounce, rockfall, howl)
        CreatureId.DRAGON -> listOf(ember, dragonClaw, tailWhip, blaze, wingToss)
        CreatureId.CAT -> listOf(claw, spark, tailWhip, thunderFang, quickAttack)
        CreatureId.GOLEM -> listOf(crash, rockSlide, rockfall, shellGuard, earthquake)
        CreatureId.SPARK_BUG -> listOf(spark, thunderFang, bugBite, quickAttack, overload)
        CreatureId.PUFF -> listOf(crash, pounce, wingToss, rockSlide, chargeUp)
    }

    /** 默认配招：招式池前 3 个。 */
    fun defaultLoadout(creatureId: CreatureId): List<Move> = movesFor(creatureId).take(3)

    /** 按名称匹配招式池（用于读取存档配招）。 */
    fun loadoutFor(creatureId: CreatureId, names: List<String>): List<Move> {
        val pool = movesFor(creatureId)
        val picked = names.mapNotNull { name -> pool.firstOrNull { it.name == name } }
        return if (names.size == 3 && names.distinct().size == 3 && picked.size == 3) picked else defaultLoadout(creatureId)
    }

    // ─── 实例生成 ───

    fun buildFangkemeng(
        species: Species,
        level: Int,
        moves: List<Move> = defaultLoadout(species.creatureId),
        energyBonus: Int = 0,
    ): Fangkemeng {
        val maxHp = CreatureGrowth.maxHp(level)
        val speed = CreatureGrowth.speed(level)
        return Fangkemeng(
            species = species,
            level = level,
            maxHp = maxHp,
            hp = maxHp,
            speed = speed,
            moves = moves,
            maxEnergy = Fangkemeng.MAX_ENERGY + energyBonus,
            energy = Fangkemeng.MAX_ENERGY + energyBonus,
        )
    }

    /** 初始解锁：草、水、普通各一只。 */
    val starters: List<CreatureId> = listOf(CreatureId.SEED_CUB, CreatureId.WATER_TURTLE, CreatureId.BIRD)

    val defaultParty: List<CreatureId> = starters

    fun newPlayerTeam(
        party: List<CreatureId> = defaultParty,
        levels: Map<CreatureId, Int> = emptyMap(),
        loadouts: Map<CreatureId, List<String>> = emptyMap(),
        relics: Set<String> = emptySet(),
    ): MutableList<Fangkemeng> = party.map { id ->
        val species = speciesOf(id.name) ?: seedCubSpecies
        buildFangkemeng(
            species = species,
            level = levels[id] ?: 10,
            moves = loadoutFor(id, loadouts[id] ?: emptyList()),
            energyBonus = if (Relic.ENERGY_TANK.id in relics) 2 else 0,
        )
    }.toMutableList()

    fun previewEnemySpecies(): Species = waterTurtleSpecies

    fun defaultItems(): MutableList<Item> = mutableListOf(
        Item(name = "伤药", kind = ItemKind.HP_POTION, amount = 35),
        Item(name = "精灵球", kind = ItemKind.POKEBALL, amount = 1),
    )
}
