package dev.hollowatlas.state;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Save format: everything written comes back identical; old/unknown data migrates safely. */
class SaveRoundTripTest {

    private static GameState sample() {
        GameState s = new GameState();
        s.worldFlags.add("hollowatlas:dt_gap_bridged");
        s.worldFlags.add(WorldMode.DEVTEST_FLAG);
        s.difficulty = "hard";
        s.nodeCooldowns.put("dt_herb_1", 12345L);
        s.shopStock.computeIfAbsent("dt_shop", k -> new java.util.HashMap<>()).put("minecraft:bread", 3);
        s.shopRestockDay.put("dt_shop", 7L);
        NpcRecord r = s.npc("dt_juno");
        r.x = 1.5; r.y = 66; r.z = -3.5; r.placed = true; r.task = "work"; r.anchor = "dt_garden"; r.variant = "moved";
        PlayerProfile p = s.profile(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        p.coins = 42;
        p.flags.add("dt_flag");
        p.evidence.add("dt_ev_cart");
        p.hypotheses.put("dt_case", "taken");
        p.discovered.add("dt_square");
        p.abilities.add("windstep");
        p.cooldowns.put("windstep", 999L);
        p.relationships.put("dt_bertram", 5);
        p.checkpoint = "@1,66,2";
        p.pendingRewards.put("minecraft:apple", 3);
        p.settings.put("guidance", "off");
        p.trackedQuest = "dtq_branch";
        PlayerProfile.QuestProgress q = new PlayerProfile.QuestProgress();
        q.stage = "a_cross";
        q.counters.put("choose/c", 1);
        q.choices.put("path", "a");
        q.log.add("choose");
        q.startedDay = 2;
        p.quests.put("dtq_branch", q);
        PlayerProfile.QuestProgress done = new PlayerProfile.QuestProgress();
        done.status = PlayerProfile.QuestProgress.Status.COMPLETED;
        done.completedDay = 3;
        p.quests.put("dtq_errand", done);
        return s;
    }

    @Test
    void gameStateRoundTripsExactly() {
        GameState a = sample();
        NbtCompound nbt = a.writeNbt(new NbtCompound(), null);
        GameState b = GameState.fromNbt(nbt, null);
        assertEquals(nbt, b.writeNbt(new NbtCompound(), null), "save -> load -> save must be identical");
        PlayerProfile p = b.players.values().iterator().next();
        assertEquals(42, p.coins);
        assertEquals("a", p.quest("dtq_branch").choices.get("path"));
        assertEquals(PlayerProfile.QuestProgress.Status.COMPLETED, p.quest("dtq_errand").status);
        assertEquals("moved", b.npc("dt_juno").variant);
        assertTrue(b.flag(WorldMode.DEVTEST_FLAG));
        assertEquals("hard", b.difficulty);
    }

    @Test
    void unversionedSaveMigratesToCurrentVersion() {
        NbtCompound legacy = new NbtCompound();       // version 0: no "version" key
        legacy.put("flags", GameState.strings(java.util.List.of("hollowatlas:region_built")));
        GameState s = GameState.fromNbt(legacy, null);
        assertTrue(s.flag("hollowatlas:region_built"));
        assertEquals(GameState.VERSION, s.writeNbt(new NbtCompound(), null).getInt("version"));
    }

    @Test
    void corruptValuesAreClampedNotCrashing() {
        NbtCompound nbt = sample().writeNbt(new NbtCompound(), null);
        NbtCompound players = nbt.getCompound("players");
        players.put("not-a-uuid", new NbtCompound());               // corrupt key
        NbtCompound p = players.getCompound("00000000-0000-0000-0000-000000000001");
        p.putInt("coins", -500);                                    // impossible value
        p.getCompound("quests").getCompound("dtq_branch").putString("status", "BOGUS");
        GameState s = GameState.fromNbt(nbt, null);
        PlayerProfile prof = s.players.get(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertEquals(0, prof.coins, "negative coins clamp to 0");
        assertEquals(PlayerProfile.QuestProgress.Status.ACTIVE, prof.quest("dtq_branch").status, "unknown status falls back safely");
        assertEquals(1, s.players.size(), "corrupt profile key skipped");
    }
}
