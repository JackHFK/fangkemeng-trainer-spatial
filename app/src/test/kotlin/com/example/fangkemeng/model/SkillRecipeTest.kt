package com.example.fangkemeng.model

import com.example.fangkemeng.battle.SkillVfx
import org.junit.Assert.*
import org.junit.Test

class SkillRecipeTest {
    @Test fun recipes_coverEveryCreatureMoveWithUniquePerformance() {
        val moves = DemoData.availableSpecies().flatMap { DemoData.movesFor(it.creatureId) }
        assertEquals(60, moves.size)
        assertEquals(38, moves.map { it.name }.toSet().size)
        assertEquals(moves.map { it.name }.toSet(), SkillVfx.recipes.map { it.name }.toSet())
        assertEquals(38, SkillVfx.recipes.map { listOf(it.shape, it.path, it.count, it.impact) }.toSet().size)
        moves.forEach { move ->
            val recipe = SkillVfx.recipe(move)
            assertTrue(recipe.count in 1..12)
            assertTrue(recipe.radius > 0f)
            assertTrue(SkillVfx.impactDelay(move) > 0f)
            assertTrue(SkillVfx.duration(move) > SkillVfx.impactDelay(move) + recipe.impactSeconds)
            assertTrue(SkillVfx.color(move).matches(Regex("0x[0-9A-F]{8}")))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun recipe_rejectsUnknownSkillInsteadOfSilentlyReusingGenericEffect() {
        SkillVfx.recipe(Move("Unknown", 1, "clip"))
    }
}
