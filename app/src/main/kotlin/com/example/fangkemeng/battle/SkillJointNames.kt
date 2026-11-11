package com.example.fangkemeng.battle

import com.example.fangkemeng.model.CreatureId

/** Original model node order, preserved from the approved creature source. */
internal val skillJointNames = mapOf(
    CreatureId.SEED_CUB to listOf("body","face","bulb","earL","earR","legFL","legFR","legBL","legBR"),
    CreatureId.WATER_TURTLE to listOf("shell","belly","head","armL","armR","legL","legR","tail"),
    CreatureId.BIRD to listOf("body","head","wingL","wingR","tail","legL","legR"),
    CreatureId.FISH to listOf("body","tailBase","tailTip","finL","finR","topFin"),
    CreatureId.LION to listOf("body","head","tail","frontLegL","frontLegR","backLegL","backLegR"),
    CreatureId.DRAGON to listOf("body","neck","jaw","wingL","wingR","tailBase","tailTip","foreLegL","foreLegR"),
    CreatureId.CAT to listOf("body","head","tailBase","tailTip","pawL","pawR","hindLegL","hindLegR"),
    CreatureId.VINE_CUB to listOf("body","face","crown","legL","legR","legBL","legBR"),
    CreatureId.TIDE_TURTLE to listOf("shell","belly","head","finL","finR"),
    CreatureId.GOLEM to listOf("legs","torso","head","armL","armR"),
    CreatureId.SPARK_BUG to listOf("body","head","wingL","wingR","antennaL","antennaR"),
    CreatureId.PUFF to listOf("body","head","earL","earR","tail"),
)
