package dev.hollowatlas.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.state.PlayerProfile.QuestProgress;
import dev.hollowatlas.world.LocationIndex;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Server-authoritative quest progression. Quests only advance through gameplay events raised by
 * server code (dialogue actions, inspections, kills, gathering, puzzles) or by polling real state
 * (positions, inventory, flags). There is deliberately no command that completes a quest in normal
 * play; debug tools are operator-only and limited to the sandbox quest.
 */
public final class QuestEngine {
    public static final Map<String, QuestDef> QUESTS = ContentManager.register("quests", QuestDef.class);

    private QuestEngine() {}

    public static void init() {
        Net.on("journal_request", (player, body) -> sync(player));
        Net.on("track", (player, body) -> {
            String id = body.has("quest") ? body.get("quest").getAsString() : "";
            PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
            if (id.isEmpty() || isActive(p, id)) {
                p.trackedQuest = id;
                GameState.get(player.getServer()).markDirty();
                sync(player);
            }
        });
        Net.on("abandon", (player, body) -> {
            String id = body.get("quest").getAsString();
            QuestDef def = QUESTS.get(id);
            PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
            if (def != null && def.abandonable && isActive(p, id)) {
                p.quests.remove(id);
                if (id.equals(p.trackedQuest)) p.trackedQuest = "";
                GameState.get(player.getServer()).markDirty();
                sync(player);
            }
        });
    }

    public static boolean isActive(PlayerProfile p, String id) {
        QuestProgress q = p.quest(id);
        return q != null && q.status == QuestProgress.Status.ACTIVE;
    }

    public static boolean isDone(PlayerProfile p, String id) {
        QuestProgress q = p.quest(id);
        return q != null && q.status == QuestProgress.Status.COMPLETED;
    }

    private static long day(ServerPlayerEntity player) { return player.getWorld().getTimeOfDay() / 24000L; }

    public static boolean start(ServerPlayerEntity player, String id) {
        QuestDef def = QUESTS.get(id);
        if (def == null) { HollowAtlas.LOGGER.warn("start_quest: unknown quest {}", id); return false; }
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        if (p.quest(id) != null) return false;               // already started or finished: idempotent
        if (!Logic.test(player, def.prereq, new Logic.Ctx(def.giver, id))) return false;
        QuestProgress q = new QuestProgress();
        q.stage = def.stages.get(0).id;
        q.startedDay = day(player);
        p.quests.put(id, q);
        if (p.trackedQuest.isEmpty() || !isActive(p, p.trackedQuest)) p.trackedQuest = id;
        state.markDirty();
        player.sendMessage(Text.translatable("message.hollowatlas.quest_started",
                Text.translatableWithFallback("quest.hollowatlas." + id + ".title", def.title)).formatted(Formatting.GOLD), false);
        player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.4f);
        Logic.run(player, def.on_start, new Logic.Ctx(def.giver, id));
        poll(player);
        sync(player);
        return true;
    }

    /** Raise a gameplay event, e.g. ("talk","nella_room"), ("inspect","pump_valve"), ("kill","hollowatlas:brigand"). */
    public static void event(ServerPlayerEntity player, String type, String target) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        boolean changed = false;
        for (Map.Entry<String, QuestProgress> e : p.quests.entrySet()) {
            QuestProgress q = e.getValue();
            if (q.status != QuestProgress.Status.ACTIVE) continue;
            QuestDef def = QUESTS.get(e.getKey());
            if (def == null) continue;
            QuestDef.Stage stage = def.stage(q.stage);
            if (stage == null) continue;
            for (QuestDef.Objective o : stage.objectives) {
                if (!o.type.equals(type) && !("event".equals(o.type) && type.equals("event"))) continue;
                if (!matches(o.target, target)) continue;
                String key = q.stage + "/" + o.id;
                // dedupe: the same unique target can only count once per objective
                String seenKey = key + "#" + target;
                if (q.counters.containsKey(seenKey)) continue;
                int c = q.counters.getOrDefault(key, 0);
                if (c >= o.count) continue;
                q.counters.put(key, c + 1);
                if (!"kill".equals(type) && !"gather".equals(type)) q.counters.put(seenKey, 1);
                changed = true;
            }
        }
        if (changed) {
            state.markDirty();
            advanceAll(player);
            sync(player);
        }
    }

    private static boolean matches(String pattern, String target) {
        if (pattern == null || pattern.equals("*")) return true;
        if (pattern.endsWith("*")) return target.startsWith(pattern.substring(0, pattern.length() - 1));
        return pattern.equals(target);
    }

    /** Re-check state-based objectives (reach, gather, flag, hypothesis) and advance stages. */
    public static void poll(ServerPlayerEntity player) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        boolean changed = false;
        for (Map.Entry<String, QuestProgress> e : p.quests.entrySet()) {
            QuestProgress q = e.getValue();
            if (q.status != QuestProgress.Status.ACTIVE) continue;
            QuestDef def = QUESTS.get(e.getKey());
            if (def == null) continue;
            QuestDef.Stage stage = def.stage(q.stage);
            if (stage == null) continue;
            for (QuestDef.Objective o : stage.objectives) {
                String key = q.stage + "/" + o.id;
                int before = q.counters.getOrDefault(key, 0);
                int now = before;
                switch (o.type) {
                    case "reach" -> {
                        if (o.target != null && LocationIndex.has(o.target)) {
                            BlockPos a = LocationIndex.pos(o.target);
                            if (player.squaredDistanceTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5) <= o.radius * o.radius) now = o.count;
                        }
                    }
                    case "gather" -> now = Math.min(o.count, RewardService.count(player, Logic.item(o.target)));
                    case "flag" -> {
                        boolean ok = o.when != null ? Logic.test(player, o.when, new Logic.Ctx(def.giver, def.id))
                                : (p.has(o.target) || state.flag(o.target));
                        now = ok ? o.count : 0;
                    }
                    case "hypothesis" -> now = p.hypotheses.containsKey(o.target) ? o.count : 0;
                    default -> { }
                }
                if (now != before && (now > before || o.type.equals("gather") || o.type.equals("flag"))) {
                    q.counters.put(key, now);
                    changed = true;
                }
            }
        }
        if (changed) {
            state.markDirty();
            advanceAll(player);
            sync(player);
        }
    }

    private static boolean stageDone(QuestProgress q, QuestDef.Stage stage) {
        for (QuestDef.Objective o : stage.objectives) {
            if (o.optional) continue;
            if (q.counters.getOrDefault(q.stage + "/" + o.id, 0) < o.count) return false;
        }
        return true;
    }

    private static void advanceAll(ServerPlayerEntity player) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        for (int guard = 0; guard < 16; guard++) {
            boolean moved = false;
            for (Map.Entry<String, QuestProgress> e : new java.util.ArrayList<>(p.quests.entrySet())) {
                QuestProgress q = e.getValue();
                if (q.status != QuestProgress.Status.ACTIVE) continue;
                QuestDef def = QUESTS.get(e.getKey());
                if (def == null) continue;
                QuestDef.Stage stage = def.stage(q.stage);
                if (stage == null || !stageDone(q, stage)) continue;
                Logic.Ctx ctx = new Logic.Ctx(def.giver, def.id);
                if (gatherConsumes(stage)) {
                    for (QuestDef.Objective o : stage.objectives) {
                        if ("gather".equals(o.type) && !o.optional) RewardService.take(player, Logic.item(o.target), o.count);
                    }
                }
                q.log.add(q.stage);
                String next = resolveNext(player, stage.next, ctx);
                Logic.run(player, stage.on_complete, ctx);
                if (next == null || next.equals("complete")) complete(player, def, q);
                else if (def.stage(next) != null) {
                    q.stage = next;
                    player.sendMessage(Text.translatable("message.hollowatlas.quest_updated",
                            Text.translatableWithFallback("quest.hollowatlas." + def.id + ".title", def.title)).formatted(Formatting.YELLOW), true);
                } else {
                    HollowAtlas.LOGGER.error("Quest {} references missing stage {}", def.id, next);
                    complete(player, def, q);
                }
                state.markDirty();
                moved = true;
            }
            if (!moved) break;
            // state-based objectives of the new stage may already be satisfied
            pollQuiet(player);
        }
    }

    private static boolean gatherConsumes(QuestDef.Stage stage) {
        return stage.on_complete != null && stage.on_complete.toString().contains("\"consume_gathered\"");
    }

    private static void pollQuiet(ServerPlayerEntity player) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        for (Map.Entry<String, QuestProgress> e : p.quests.entrySet()) {
            QuestProgress q = e.getValue();
            if (q.status != QuestProgress.Status.ACTIVE) continue;
            QuestDef def = QUESTS.get(e.getKey());
            QuestDef.Stage stage = def == null ? null : def.stage(q.stage);
            if (stage == null) continue;
            for (QuestDef.Objective o : stage.objectives) {
                String key = q.stage + "/" + o.id;
                if ("flag".equals(o.type)) {
                    boolean ok = o.when != null ? Logic.test(player, o.when, new Logic.Ctx(def.giver, def.id)) : (p.has(o.target) || state.flag(o.target));
                    if (ok) q.counters.put(key, o.count);
                } else if ("gather".equals(o.type)) {
                    q.counters.put(key, Math.min(o.count, RewardService.count(player, Logic.item(o.target))));
                }
            }
        }
    }

    private static String resolveNext(ServerPlayerEntity player, JsonElement next, Logic.Ctx ctx) {
        if (next == null || next.isJsonNull()) return null;
        if (next.isJsonPrimitive()) return next.getAsString();
        for (JsonElement b : next.getAsJsonArray()) {
            JsonObject o = b.getAsJsonObject();
            if (Logic.test(player, o.get("when"), ctx)) return o.get("stage").getAsString();
        }
        return null;
    }

    private static void complete(ServerPlayerEntity player, QuestDef def, QuestProgress q) {
        if (q.status == QuestProgress.Status.COMPLETED) return;   // never pay rewards twice
        q.status = QuestProgress.Status.COMPLETED;
        q.completedDay = day(player);
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        if (def.id.equals(p.trackedQuest)) {
            p.trackedQuest = p.quests.entrySet().stream().filter(e -> e.getValue().status == QuestProgress.Status.ACTIVE)
                    .map(Map.Entry::getKey).findFirst().orElse("");
        }
        player.sendMessage(Text.translatable("message.hollowatlas.quest_completed",
                Text.translatableWithFallback("quest.hollowatlas." + def.id + ".title", def.title)).formatted(Formatting.GREEN), false);
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.0f);
        Logic.run(player, def.rewards, new Logic.Ctx(def.giver, def.id));
        p.flags.add("quest_done:" + def.id);
    }

    /** Periodic poll for position-based objectives. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 7) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) poll(p);
    }

    // --------------------------------------------------------------- journal

    /** Push the player's discovered quests, tracked objective and case board to the client. */
    public static void sync(ServerPlayerEntity player) {
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        boolean guidance = !"off".equals(p.settings.getOrDefault("guidance", "on"));
        JsonObject root = new JsonObject();
        JsonArray quests = new JsonArray();
        for (Map.Entry<String, QuestProgress> e : p.quests.entrySet()) {
            QuestDef def = QUESTS.get(e.getKey());
            if (def == null) continue;
            QuestProgress q = e.getValue();
            JsonObject jq = new JsonObject();
            jq.addProperty("id", def.id);
            jq.addProperty("title", def.title);
            jq.addProperty("category", def.category);
            jq.addProperty("summary", def.summary == null ? "" : def.summary);
            jq.addProperty("status", q.status.name());
            jq.addProperty("tracked", def.id.equals(p.trackedQuest));
            jq.addProperty("abandonable", def.abandonable);
            JsonArray log = new JsonArray();
            for (String sid : q.log) {
                QuestDef.Stage st = def.stage(sid);
                if (st != null && st.text != null) log.add(st.text);
            }
            jq.add("log", log);
            if (q.status == QuestProgress.Status.ACTIVE) {
                QuestDef.Stage st = def.stage(q.stage);
                if (st != null) {
                    jq.addProperty("stage_text", st.text == null ? "" : st.text);
                    if (guidance && st.hint != null) jq.addProperty("hint", st.hint);
                    JsonArray objs = new JsonArray();
                    for (QuestDef.Objective o : st.objectives) {
                        JsonObject jo = new JsonObject();
                        jo.addProperty("text", o.text == null ? o.id : o.text);
                        jo.addProperty("done", q.counters.getOrDefault(q.stage + "/" + o.id, 0));
                        jo.addProperty("count", o.count);
                        jo.addProperty("optional", o.optional);
                        if (guidance && o.marker != null && LocationIndex.has(o.marker)) {
                            BlockPos m = LocationIndex.pos(o.marker);
                            jo.addProperty("mx", m.getX()); jo.addProperty("my", m.getY()); jo.addProperty("mz", m.getZ());
                        }
                        objs.add(jo);
                    }
                    jq.add("objectives", objs);
                }
            } else if (def.completion_text != null) {
                jq.addProperty("stage_text", def.completion_text);
            }
            quests.add(jq);
        }
        root.add("quests", quests);
        root.addProperty("tracked", p.trackedQuest);
        root.addProperty("coins", p.coins);
        root.addProperty("guidance", guidance);
        root.add("cases", dev.hollowatlas.investigation.CaseService.journal(player));
        Net.send(player, "journal", root);
    }
}
