package dev.hollowatlas.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.ability.AbilityService;
import dev.hollowatlas.investigation.CaseService;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.inventory.ShopService;
import dev.hollowatlas.npc.NpcDirector;
import dev.hollowatlas.npc.ScheduleService;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.world.LocationIndex;
import dev.hollowatlas.world.WorldChanges;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Data-driven predicates and effects shared by dialogue, quests, interactions and puzzles.
 * All evaluation happens on the server against the canonical {@link GameState}.
 */
public final class Logic {
    private Logic() {}

    /** Context carried through evaluation (e.g. which resident is speaking). */
    public record Ctx(String npc, String quest) {
        public static final Ctx NONE = new Ctx(null, null);
    }

    // ------------------------------------------------------------ conditions

    public static boolean test(ServerPlayerEntity player, JsonElement cond, Ctx ctx) {
        if (cond == null || cond.isJsonNull()) return true;
        if (cond.isJsonArray()) {
            for (JsonElement e : cond.getAsJsonArray()) if (!test(player, e, ctx)) return false;
            return true;
        }
        if (!cond.isJsonObject()) return true;
        JsonObject c = cond.getAsJsonObject();
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        for (String key : c.keySet()) {
            JsonElement v = c.get(key);
            boolean ok = switch (key) {
                case "flag" -> p.has(v.getAsString());
                case "flags" -> all(v, p::has);
                case "not_flag" -> !p.has(v.getAsString());
                case "not_flags" -> none(v, p::has);
                case "world_flag" -> state.flag(v.getAsString());
                case "not_world_flag" -> !state.flag(v.getAsString());
                case "quest_active" -> QuestEngine.isActive(p, v.getAsString());
                case "quest_done" -> QuestEngine.isDone(p, v.getAsString());
                case "quest_not_started" -> p.quest(v.getAsString()) == null;
                case "quest_stage" -> {
                    JsonObject o = v.getAsJsonObject();
                    PlayerProfile.QuestProgress qp = p.quest(o.get("quest").getAsString());
                    yield qp != null && qp.status == PlayerProfile.QuestProgress.Status.ACTIVE && qp.stage.equals(o.get("stage").getAsString());
                }
                case "evidence" -> all(v, p.evidence::contains);
                case "not_evidence" -> none(v, p.evidence::contains);
                case "has_item" -> {
                    JsonObject o = v.getAsJsonObject();
                    yield RewardService.count(player, item(o.get("item").getAsString())) >= (o.has("count") ? o.get("count").getAsInt() : 1);
                }
                case "coins_min" -> p.coins >= v.getAsInt();
                case "relationship" -> {
                    JsonObject o = v.getAsJsonObject();
                    String npc = o.has("npc") ? o.get("npc").getAsString() : ctx.npc();
                    yield p.relationship(npc) >= o.get("min").getAsInt();
                }
                case "time" -> {
                    JsonObject o = v.getAsJsonObject();
                    double h = ScheduleService.hour(player.getWorld());
                    double from = o.get("from").getAsDouble(), to = o.get("to").getAsDouble();
                    yield from <= to ? h >= from && h < to : h >= from || h < to;
                }
                case "weather" -> v.getAsString().equals("rain") == player.getWorld().isRaining();
                case "hypothesis" -> {
                    JsonObject o = v.getAsJsonObject();
                    yield o.get("id").getAsString().equals(p.hypotheses.get(o.get("case").getAsString()));
                }
                case "choice" -> {
                    JsonObject o = v.getAsJsonObject();
                    PlayerProfile.QuestProgress qp = p.quest(o.get("quest").getAsString());
                    yield qp != null && o.get("value").getAsString().equals(qp.choices.get(o.get("key").getAsString()));
                }
                case "ability" -> p.abilities.contains(v.getAsString());
                case "discovered" -> p.discovered.contains(v.getAsString());
                case "any" -> {
                    boolean any = false;
                    for (JsonElement e : v.getAsJsonArray()) if (test(player, e, ctx)) { any = true; break; }
                    yield any;
                }
                case "not" -> !test(player, v, ctx);
                case "comment" -> true;
                default -> {
                    HollowAtlas.LOGGER.warn("Unknown condition key '{}'", key);
                    yield false;
                }
            };
            if (!ok) return false;
        }
        return true;
    }

    private static boolean all(JsonElement arr, java.util.function.Predicate<String> f) {
        for (JsonElement e : arr.getAsJsonArray()) if (!f.test(e.getAsString())) return false;
        return true;
    }

    private static boolean none(JsonElement arr, java.util.function.Predicate<String> f) {
        if (arr.isJsonPrimitive()) return !f.test(arr.getAsString());
        for (JsonElement e : arr.getAsJsonArray()) if (f.test(e.getAsString())) return false;
        return true;
    }

    public static JsonObject flagCond(String flag) {
        JsonObject o = new JsonObject();
        o.addProperty(flag.startsWith("hollowatlas:") ? "world_flag" : "flag", flag);
        return o;
    }

    public static Item item(String id) {
        return Registries.ITEM.get(Identifier.of(id));
    }

    // --------------------------------------------------------------- actions

    public static void run(ServerPlayerEntity player, JsonElement actions, Ctx ctx) {
        if (actions == null || actions.isJsonNull()) return;
        if (actions.isJsonArray()) {
            for (JsonElement e : actions.getAsJsonArray()) run(player, e, ctx);
            return;
        }
        JsonObject a = actions.getAsJsonObject();
        if (a.has("when") && !test(player, a.get("when"), ctx)) return;
        String type = a.get("type").getAsString();
        GameState state = GameState.get(player.getServer());
        PlayerProfile p = state.profile(player.getUuid());
        switch (type) {
            case "set_flag" -> { p.flags.add(str(a, "flag")); state.markDirty(); QuestEngine.poll(player); }
            case "clear_flag" -> { p.flags.remove(str(a, "flag")); state.markDirty(); }
            case "world_flag" -> {
                state.setFlag(str(a, "flag"), !a.has("value") || a.get("value").getAsBoolean());
                WorldChanges.apply(player.getServer());
                QuestEngine.poll(player);
            }
            case "start_quest" -> QuestEngine.start(player, str(a, "quest"));
            case "quest_event" -> QuestEngine.event(player, str(a, "event"), str(a, "target"));
            case "give_item" -> RewardService.give(player, str(a, "item"), a.has("count") ? a.get("count").getAsInt() : 1);
            case "take_item" -> RewardService.take(player, item(str(a, "item")), a.has("count") ? a.get("count").getAsInt() : 1);
            case "coins" -> RewardService.coins(player, a.get("amount").getAsInt());
            case "evidence" -> CaseService.addEvidence(player, str(a, "id"));
            case "relationship" -> {
                String npc = a.has("npc") ? str(a, "npc") : ctx.npc();
                p.relationships.merge(npc, a.get("delta").getAsInt(), Integer::sum);
                state.markDirty();
            }
            case "open_shop" -> ShopService.open(player, str(a, "shop"), ctx.npc());
            case "set_variant" -> {
                state.npc(str(a, "npc")).variant = str(a, "variant");
                state.markDirty();
            }
            case "choice" -> {
                PlayerProfile.QuestProgress qp = p.quest(str(a, "quest"));
                if (qp != null) { qp.choices.put(str(a, "key"), str(a, "value")); state.markDirty(); }
            }
            case "unlock_ability" -> AbilityService.unlock(player, str(a, "ability"));
            case "checkpoint" -> {
                String anchor = str(a, "anchor");
                if (LocationIndex.has(anchor)) {
                    p.checkpoint = anchor;
                    BlockPos pos = LocationIndex.pos(anchor);
                    player.setSpawnPoint(player.getServerWorld().getRegistryKey(), pos, player.getYaw(), true, false);
                    state.markDirty();
                }
            }
            case "discover" -> {
                if (p.discovered.add(str(a, "site"))) {
                    state.markDirty();
                    player.sendMessage(Text.translatable("message.hollowatlas.discovered",
                            Text.translatable("site.hollowatlas." + str(a, "site"))).formatted(Formatting.AQUA), false);
                    player.playSoundToPlayer(SoundEvents.UI_TOAST_IN, SoundCategory.PLAYERS, 0.8f, 1.2f);
                    QuestEngine.poll(player);
                }
            }
            case "message" -> player.sendMessage(Text.translatableWithFallback(
                    a.has("key") ? str(a, "key") : "", str(a, "text")).formatted(Formatting.GRAY, Formatting.ITALIC), false);
            case "track" -> { p.trackedQuest = str(a, "quest"); state.markDirty(); QuestEngine.sync(player); }
            case "npc_say" -> {
                var e = NpcDirector.live(a.has("npc") ? str(a, "npc") : ctx.npc());
                if (e != null) NpcDirector.say(player.getServerWorld(), e, str(a, "text"), 16);
            }
            case "sound" -> player.playSoundToPlayer(Registries.SOUND_EVENT.get(Identifier.of(str(a, "sound"))),
                    SoundCategory.PLAYERS, 1f, 1f);
            case "weather_clear" -> player.getServerWorld().setWeather(6000, 0, false, false);
            case "consume_gathered" -> { } // handled by QuestEngine when the stage completes
            case "open_dialogue" -> dev.hollowatlas.dialogue.DialogueService.openTree(player, str(a, "tree"), str(a, "node"));
            case "difficulty" -> { state.difficulty = str(a, "value"); state.markDirty(); }
            default -> HollowAtlas.LOGGER.warn("Unknown action type '{}'", type);
        }
    }

    private static String str(JsonObject o, String key) { return o.get(key).getAsString(); }

    /** Validate action/condition shapes at load time (unknown keys, missing fields). */
    public static void validateActions(String where, JsonElement actions, List<String> problems) {
        if (actions == null || actions.isJsonNull()) return;
        JsonArray arr = actions.isJsonArray() ? actions.getAsJsonArray() : null;
        if (arr == null) { JsonArray one = new JsonArray(); one.add(actions); arr = one; }
        for (JsonElement e : arr) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("type")) problems.add(where + ": action without type: " + e);
        }
    }
}
