package com.example.fangkemeng.model

import org.junit.Assert.*
import org.junit.Test

class PreparationDraftTest {
    @Test fun toggleMove_fullSelectionRequiresExplicitDeselectBeforeReplace() {
        val p = PlayerProfile()
        val id = p.party.first()
        val draft = PreparationDraft(p)
        val original = draft.moves(id).toList()
        val replacement = DemoData.movesFor(id).last().name
        assertFalse(draft.toggleMove(id, replacement))
        assertEquals(original, draft.moves(id))
        assertTrue(draft.toggleMove(id, original[1]))
        assertEquals(listOf(original[0], original[2]), draft.moves(id))
        assertNotNull(draft.issue)
        assertFalse(p.commitPreparation(draft.party, draft.loadouts))
        assertTrue(p.loadouts.isEmpty())
        assertTrue(draft.toggleMove(id, replacement))
        assertEquals(listOf(original[0], original[2], replacement), draft.moves(id))
        assertNull(draft.issue)
        assertTrue(p.commitPreparation(draft.party, draft.loadouts))
        assertEquals(draft.moves(id), p.loadouts[id])
    }

    @Test fun hiddenIncompletePartnerBlocksAtomicCommitAndCancelLeavesOriginal() {
        val p = PlayerProfile()
        val originalParty = p.party.toList()
        val first = p.party.first()
        p.loadouts[first] = DemoData.movesFor(first).takeLast(3).map { it.name }
        val originalLoadouts = p.loadouts.mapValues { it.value.toList() }
        val draft = PreparationDraft(p)
        assertTrue(draft.toggleMove(first, draft.moves(first).first()))
        val second = p.party[1]
        assertEquals(3, draft.moves(second).size)
        assertNotNull(draft.issue)
        assertFalse(p.commitPreparation(draft.party, draft.loadouts))
        assertEquals(originalParty, p.party)
        assertEquals(originalLoadouts, p.loadouts)
        val reopened = PreparationDraft(p)
        assertEquals(originalLoadouts[first], reopened.moves(first))
        assertNull(reopened.issue)
    }

    @Test fun togglePartner_obeysExplicitRemovalAndOneToThreeBounds() {
        val p = PlayerProfile()
        val extra = DemoData.availableSpecies().last().creatureId
        p.unlocked += extra
        val draft = PreparationDraft(p)
        assertFalse(draft.togglePartner(extra))
        assertTrue(draft.togglePartner(draft.party[1]))
        assertTrue(draft.togglePartner(extra))
        assertEquals(extra, draft.party.last())
        assertEquals(3, draft.moves(extra).size)
        assertTrue(draft.togglePartner(draft.party[0]))
        assertTrue(draft.togglePartner(draft.party[0]))
        assertFalse(draft.togglePartner(draft.party.single()))
        assertEquals(1, draft.party.size)
        assertEquals(DemoData.defaultParty, p.party)
    }

    @Test fun commitPreparation_rejectsMalformedMovesAndInvalidTeamsWithoutPartialWrite() {
        val p = PlayerProfile()
        val id = p.party.first()
        val valid = DemoData.defaultLoadout(id).map { it.name }
        for (names in listOf(valid.take(2), valid + valid[0], listOf(valid[0], valid[0], valid[1]), listOf(valid[0], valid[1], "unknown"))) {
            assertFalse(p.commitPreparation(p.party.toList(), mapOf(id to names)))
            assertTrue(p.loadouts.isEmpty())
        }
        for (team in listOf(emptyList(), listOf(id, id), p.party + DemoData.availableSpecies().last().creatureId)) {
            assertFalse(p.commitPreparation(team, mapOf(id to valid)))
            assertEquals(DemoData.defaultParty, p.party)
        }
        val locked = DemoData.availableSpecies().last().creatureId
        assertFalse(p.commitPreparation(listOf(locked), mapOf(locked to DemoData.defaultLoadout(locked).map { it.name })))
        assertTrue(p.loadouts.isEmpty())
    }

    @Test fun readingLegacyInvalidLoadoutUsesDefaultButExplicitDraftCannotSaveIt() {
        val p = PlayerProfile()
        val id = p.party.first()
        val duplicate = List(3) { DemoData.movesFor(id).first().name }
        p.loadouts[id] = duplicate
        assertEquals(DemoData.defaultLoadout(id), p.loadoutOf(id))
        assertFalse(p.commitPreparation(p.party.toList(), mapOf(id to duplicate)))
        assertEquals(duplicate, p.loadouts[id])
        val draft = PreparationDraft(p)
        assertEquals(DemoData.defaultLoadout(id).map { it.name }, draft.moves(id))
        assertFalse(draft.toggleMove(id, "unknown"))
    }
}
