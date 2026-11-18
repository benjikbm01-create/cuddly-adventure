package dev.hollowatlas.state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent per-player RPG state. Owned by {@link GameState}; mutate only on the server thread. */
public final class PlayerProfile {
    public int coins = 0;
    public final Set<String> flags = new LinkedHashSet<>();
    public final Map<String, QuestProgress> quests = new LinkedHashMap<>();
    public String trackedQuest = "";
    public final Set<String> evidence = new LinkedHashSet<>();
    /** case id -> chosen hypothesis id (a guess, never treated as fact). */
    public final Map<String, String> hypotheses = new HashMap<>();
    public final Set<String> discovered = new LinkedHashSet<>();
    public final Set<String> abilities = new LinkedHashSet<>();
    /** ability id -> world time when usable again. */
    public final Map<String, Long> cooldowns = new HashMap<>();
    public final Map<String, Integer> relationships = new HashMap<>();
    public String checkpoint = "arrival";
    /** Rewards that could not be delivered (full inventory): item id -> count. */
    public final Map<String, Integer> pendingRewards = new LinkedHashMap<>();
    public final Map<String, String> settings = new HashMap<>();
    /** Transient-ish combat pools; persisted so a relog can't refill them instantly. */
    public float stamina = 100f;
    public float focus = 100f;

    public boolean has(String flag) { return flags.contains(flag); }

    public QuestProgress quest(String id) { return quests.get(id); }

    public int relationship(String npc) { return relationships.getOrDefault(npc, 0); }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("coins", coins);
        n.put("flags", GameState.strings(flags));
        NbtCompound q = new NbtCompound();
        quests.forEach((id, p) -> q.put(id, p.toNbt()));
        n.put("quests", q);
        n.putString("tracked", trackedQuest);
        n.put("evidence", GameState.strings(evidence));
        NbtCompound h = new NbtCompound();
        hypotheses.forEach(h::putString);
        n.put("hypotheses", h);
        n.put("discovered", GameState.strings(discovered));
        n.put("abilities", GameState.strings(abilities));
        NbtCompound cd = new NbtCompound();
        cooldowns.forEach(cd::putLong);
        n.put("cooldowns", cd);
        NbtCompound rel = new NbtCompound();
        relationships.forEach(rel::putInt);
        n.put("relationships", rel);
        n.putString("checkpoint", checkpoint);
        NbtCompound pr = new NbtCompound();
        pendingRewards.forEach(pr::putInt);
        n.put("pending_rewards", pr);
        NbtCompound st = new NbtCompound();
        settings.forEach(st::putString);
        n.put("settings", st);
        n.putFloat("stamina", stamina);
        n.putFloat("focus", focus);
        return n;
    }

    public static PlayerProfile fromNbt(NbtCompound n) {
        PlayerProfile p = new PlayerProfile();
        p.coins = Math.max(0, n.getInt("coins"));
        GameState.readStrings(n.getList("flags", NbtElement.STRING_TYPE), p.flags);
        NbtCompound q = n.getCompound("quests");
        for (String id : q.getKeys()) p.quests.put(id, QuestProgress.fromNbt(q.getCompound(id)));
        p.trackedQuest = n.getString("tracked");
        GameState.readStrings(n.getList("evidence", NbtElement.STRING_TYPE), p.evidence);
        NbtCompound h = n.getCompound("hypotheses");
        for (String k : h.getKeys()) p.hypotheses.put(k, h.getString(k));
        GameState.readStrings(n.getList("discovered", NbtElement.STRING_TYPE), p.discovered);
        GameState.readStrings(n.getList("abilities", NbtElement.STRING_TYPE), p.abilities);
        NbtCompound cd = n.getCompound("cooldowns");
        for (String k : cd.getKeys()) p.cooldowns.put(k, cd.getLong(k));
        NbtCompound rel = n.getCompound("relationships");
        for (String k : rel.getKeys()) p.relationships.put(k, rel.getInt(k));
        if (n.contains("checkpoint")) p.checkpoint = n.getString("checkpoint");
        NbtCompound pr = n.getCompound("pending_rewards");
        for (String k : pr.getKeys()) p.pendingRewards.put(k, Math.max(0, pr.getInt(k)));
        NbtCompound st = n.getCompound("settings");
        for (String k : st.getKeys()) p.settings.put(k, st.getString(k));
        if (n.contains("stamina")) p.stamina = n.getFloat("stamina");
        if (n.contains("focus")) p.focus = n.getFloat("focus");
        return p;
    }

    /** Progress of one quest for one player. */
    public static final class QuestProgress {
        public enum Status { ACTIVE, COMPLETED, FAILED }

        public String stage = "";
        public Status status = Status.ACTIVE;
        /** objective id -> count */
        public final Map<String, Integer> counters = new HashMap<>();
        public final Map<String, String> choices = new HashMap<>();
        public final List<String> log = new ArrayList<>();
        public long startedDay;
        public long completedDay = -1;

        public NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putString("stage", stage);
            n.putString("status", status.name());
            NbtCompound c = new NbtCompound();
            counters.forEach(c::putInt);
            n.put("counters", c);
            NbtCompound ch = new NbtCompound();
            choices.forEach(ch::putString);
            n.put("choices", ch);
            n.put("log", GameState.strings(log));
            n.putLong("started", startedDay);
            n.putLong("completed", completedDay);
            return n;
        }

        public static QuestProgress fromNbt(NbtCompound n) {
            QuestProgress p = new QuestProgress();
            p.stage = n.getString("stage");
            try { p.status = Status.valueOf(n.getString("status")); } catch (IllegalArgumentException e) { p.status = Status.ACTIVE; }
            NbtCompound c = n.getCompound("counters");
            for (String k : c.getKeys()) p.counters.put(k, c.getInt(k));
            NbtCompound ch = n.getCompound("choices");
            for (String k : ch.getKeys()) p.choices.put(k, ch.getString(k));
            GameState.readStrings(n.getList("log", NbtElement.STRING_TYPE), p.log);
            p.startedDay = n.getLong("started");
            p.completedDay = n.contains("completed") ? n.getLong("completed") : -1;
            return p;
        }
    }
}
