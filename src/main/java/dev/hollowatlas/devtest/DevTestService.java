package dev.hollowatlas.devtest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.hollowatlas.ability.AbilityService;
import dev.hollowatlas.combat.CombatRegistry;
import dev.hollowatlas.combat.CombatService;
import dev.hollowatlas.combat.EnemyEntity;
import dev.hollowatlas.combat.EncounterService;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.inventory.HudSync;
import dev.hollowatlas.inventory.ModItems;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.investigation.CaseService;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.quest.QuestDef;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.state.WorldMode;
import dev.hollowatlas.world.LocationIndex;
import dev.hollowatlas.world.WorldChanges;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Developer Testing Mode actions. Every entry point refuses to run unless the world is a developer
 * testing world ({@link WorldMode#devtest}); in story worlds none of this exists from the player's
 * point of view. Actions call the REAL game systems (rewards, abilities, quests, encounters, world
 * changes, combat) so testing here exercises the same code the story uses.
 * Nothing here references story characters, quests or places.
 */
public final class DevTestService {
    /** Test-world anchors are prefixed so they can't collide with (or reveal) story places. */
    public static final String ANCHOR_PREFIX = "dt_";
    public static final List<String> ACTIONS = List.of(
            "spawn_brigand", "spawn_slinger", "spawn_pair", "clear_enemies",
            "give_weapons", "give_armor", "give_food", "give_materials", "give_token", "fill_pack", "clear_pack",
            "unlock_windstep", "unlock_echo_sight", "reset_cooldowns", "refill",
            "coins", "reset_shops",
            "time_morning", "time_noon", "time_evening", "time_night", "next_day", "weather_clear", "weather_rain",
            "toggle_gap", "reset_nodes",
            "start_quest", "reset_quests",
            "tp", "checkpoint_here", "die", "save_now", "difficulty", "info");

    private DevTestService() {}

    public static void init() {
        Net.on("dev", (player, body) -> run(player, body.get("action").getAsString(),
                body.has("arg") ? body.get("arg").getAsString() : ""));
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> dispatcher.register(
                CommandManager.literal("devtest")
                        .requires(src -> WorldMode.devtest(src.getServer()))
                        .then(CommandManager.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> CommandSource.suggestMatching(ACTIONS, b))
                                .executes(ctx -> {
                                    run(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "action"), "");
                                    return 1;
                                })
                                .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                                        .suggests((c, b) -> CommandSource.suggestMatching(suggestArgs(), b))
                                        .executes(ctx -> {
                                            run(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "action"),
                                                    StringArgumentType.getString(ctx, "arg"));
                                            return 1;
                                        })))));
    }

    private static List<String> suggestArgs() {
        List<String> out = new ArrayList<>(testAnchors());
        out.addAll(testQuests().stream().map(q -> q.id).toList());
        out.addAll(List.of("story", "normal", "hard", "expert", "50", "500"));
        return out;
    }

    public static List<String> testAnchors() {
        return LocationIndex.names().stream().filter(n -> n.startsWith(ANCHOR_PREFIX)).sorted().toList();
    }

    public static List<QuestDef> testQuests() {
        return QuestEngine.QUESTS.values().stream().filter(ContentManager::isDevtest).toList();
    }

    private static void say(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal("[Dev] " + msg).formatted(Formatting.LIGHT_PURPLE), false);
    }

    // ------------------------------------------------------------- first join

    public static void onFirstJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        GameState gs = GameState.get(server);
        PlayerProfile p = gs.profile(player.getUuid());
        BlockPos arrival = LocationIndex.pos("dt_arrival");
        player.teleport(server.getOverworld(), arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 180f, 0f);
        player.setSpawnPoint(server.getOverworld().getRegistryKey(), arrival, 180f, true, false);
        p.checkpoint = "dt_arrival";
        p.coins = 100;
        gs.markDirty();
        RewardService.give(player, "hollowatlas:traveler_shortsword", 1);
        RewardService.give(player, "minecraft:bread", 8);
        player.sendMessage(Text.translatable("message.hollowatlas.devtest_welcome").formatted(Formatting.LIGHT_PURPLE), false);
    }

    // ------------------------------------------------------------- actions

    public static void run(ServerPlayerEntity player, String action, String arg) {
        MinecraftServer server = player.getServer();
        if (!WorldMode.devtest(server)) return;           // never available in story worlds
        ServerWorld w = player.getServerWorld();
        GameState gs = GameState.get(server);
        PlayerProfile p = gs.profile(player.getUuid());
        switch (action) {
            case "spawn_brigand" -> spawn(player, "brigand", 0);
            case "spawn_slinger" -> spawn(player, "slinger", 0);
            case "spawn_pair" -> { spawn(player, "brigand", -2); spawn(player, "slinger", 3); }
            case "clear_enemies" -> {
                int n = 0;
                for (EnemyEntity e : w.getEntitiesByClass(EnemyEntity.class, player.getBoundingBox().expand(96), x -> true)) { e.discard(); n++; }
                say(player, "Removed " + n + " enemies.");
            }
            case "give_weapons" -> {
                for (String id : List.of("hollowatlas:traveler_shortsword", "hollowatlas:ash_quarterstaff", "hollowatlas:holt_hatchet", "minecraft:shield"))
                    RewardService.give(player, id, 1);
            }
            case "give_armor" -> {
                for (String id : List.of("minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots"))
                    RewardService.give(player, id, 1);
            }
            case "give_food" -> {
                RewardService.give(player, "hollowatlas:hearth_stew", 3);
                RewardService.give(player, "hollowatlas:trail_tonic", 3);
                RewardService.give(player, "hollowatlas:honey_bun", 4);
                RewardService.give(player, "minecraft:mutton", 3);
            }
            case "give_materials" -> {
                for (String id : List.of("hollowatlas:yarrow", "hollowatlas:feverfew", "minecraft:carrot", "minecraft:potato", "minecraft:sweet_berries"))
                    RewardService.give(player, id, 6);
                RewardService.give(player, "hollowatlas:old_coin", 3);
            }
            case "give_token" -> RewardService.give(player, "hollowatlas:test_token", 1);
            case "fill_pack" -> {
                var inv = player.getInventory();
                int n = 0;
                for (int i = 0; i < inv.main.size(); i++) if (inv.main.get(i).isEmpty()) { inv.main.set(i, new ItemStack(net.minecraft.item.Items.COBBLESTONE, 64)); n++; }
                say(player, "Filled " + n + " empty slots with cobblestone. Try earning a reward now.");
            }
            case "clear_pack" -> {
                var inv = player.getInventory();
                for (int i = 0; i < inv.main.size(); i++) if (!ModItems.isKeyItem(inv.main.get(i))) inv.main.set(i, ItemStack.EMPTY);
                for (int i = 0; i < inv.armor.size(); i++) inv.armor.set(i, ItemStack.EMPTY);
                say(player, "Cleared your pack (key items kept).");
            }
            case "unlock_windstep" -> AbilityService.unlock(player, "windstep");
            case "unlock_echo_sight" -> AbilityService.unlock(player, "echo_sight");
            case "reset_cooldowns" -> { p.cooldowns.clear(); gs.markDirty(); say(player, "Ability cooldowns reset."); }
            case "refill" -> {
                p.stamina = CombatService.MAX_STAMINA;
                p.focus = AbilityService.MAX_FOCUS;
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                gs.markDirty();
            }
            case "coins" -> RewardService.coins(player, parseInt(arg, 50));
            case "reset_shops" -> { gs.shopStock.clear(); gs.shopRestockDay.clear(); gs.markDirty(); say(player, "Shop stock reset."); }
            case "time_morning" -> w.setTimeOfDay(w.getTimeOfDay() / 24000 * 24000 + 1000);
            case "time_noon" -> w.setTimeOfDay(w.getTimeOfDay() / 24000 * 24000 + 6000);
            case "time_evening" -> w.setTimeOfDay(w.getTimeOfDay() / 24000 * 24000 + 12500);
            case "time_night" -> w.setTimeOfDay(w.getTimeOfDay() / 24000 * 24000 + 18000);
            case "next_day" -> w.setTimeOfDay((w.getTimeOfDay() / 24000 + 1) * 24000 + 1000);
            case "weather_clear" -> w.setWeather(12000, 0, false, false);
            case "weather_rain" -> w.setWeather(0, 6000, true, false);
            case "toggle_gap" -> {
                boolean open = !gs.flag("hollowatlas:dt_gap_bridged");
                gs.setFlag("hollowatlas:dt_gap_bridged", open);
                WorldChanges.apply(server);
                say(player, "Test world event: footbridge " + (open ? "built" : "removed") + ".");
            }
            case "reset_nodes" -> { gs.nodeCooldowns.keySet().removeIf(k -> k.startsWith(ANCHOR_PREFIX)); gs.markDirty(); say(player, "Gathering spots reset."); }
            case "start_quest" -> {
                QuestDef q = QuestEngine.QUESTS.get(arg);
                if (q == null || !ContentManager.isDevtest(q)) { say(player, "Unknown test quest."); return; }
                if (!QuestEngine.start(player, arg)) say(player, "Already started or finished. Reset test quests first.");
            }
            case "reset_quests" -> resetQuests(player, gs, p);
            case "tp" -> {
                if (!arg.startsWith(ANCHOR_PREFIX) || !LocationIndex.has(arg)) { say(player, "Unknown test location."); return; }
                BlockPos b = LocationIndex.pos(arg);
                player.teleport(w, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, player.getYaw(), player.getPitch());
            }
            case "checkpoint_here" -> {
                BlockPos b = player.getBlockPos();
                p.checkpoint = "@" + b.getX() + "," + b.getY() + "," + b.getZ();
                player.setSpawnPoint(w.getRegistryKey(), b, player.getYaw(), true, false);
                gs.markDirty();
                say(player, "Checkpoint set here. Use 'Test death' to try it.");
            }
            case "die" -> player.damage(w.getDamageSources().genericKill(), Float.MAX_VALUE);
            case "save_now" -> { server.saveAll(false, true, true); say(player, "World saved. Quit and reload to check persistence."); }
            case "difficulty" -> {
                if (!List.of("story", "normal", "hard", "expert").contains(arg)) { say(player, "Use story, normal, hard or expert."); return; }
                gs.difficulty = arg;
                gs.markDirty();
                say(player, "Difficulty: " + arg);
            }
            case "info" -> sendInfo(player);
            default -> { say(player, "Unknown action."); return; }
        }
        HudSync.mark(player);
        QuestEngine.sync(player);
        sendInfo(player);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static void spawn(ServerPlayerEntity player, String kind, int side) {
        ServerWorld w = player.getServerWorld();
        Vec3d fwd = Vec3d.fromPolar(0, player.getYaw());
        Vec3d at = player.getPos().add(fwd.multiply(5)).add(-fwd.z * side, 0, fwd.x * side);
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
        if (Math.abs(y - player.getY()) > 4) y = (int) player.getY();
        EnemyEntity e = CombatRegistry.ENEMY.create(w);
        if (e == null) return;
        e.refreshPositionAndAngles(at.x, y, at.z, player.getYaw() + 180, 0);
        e.initialize(w, w.getLocalDifficulty(e.getBlockPos()), SpawnReason.COMMAND, null);
        e.setKind(kind);
        w.spawnEntity(e);
    }

    /** Resets ONLY test-world progress: test quests, their flags, test evidence/theories, test world flags and nodes. */
    private static void resetQuests(ServerPlayerEntity player, GameState gs, PlayerProfile p) {
        int n = 0;
        for (QuestDef q : testQuests()) {
            if (p.quests.remove(q.id) != null) n++;
            p.flags.remove("quest_done:" + q.id);
        }
        if (!p.trackedQuest.isEmpty() && !p.quests.containsKey(p.trackedQuest)) p.trackedQuest = "";
        p.flags.removeIf(f -> f.startsWith("dt_") || f.startsWith("int:dt_") || f.startsWith("seen:dt_") || f.startsWith("chose:dt_"));
        for (CaseService.CaseDef c : CaseService.CASES.values()) {
            if (!ContentManager.isDevtest(c)) continue;
            p.hypotheses.remove(c.id);
            for (CaseService.Evidence e : c.evidence) p.evidence.remove(e.id);
        }
        gs.worldFlags.removeIf(f -> f.startsWith("hollowatlas:dt_") || f.startsWith("hollowatlas:encounter_cleared:dt_"));
        gs.nodeCooldowns.keySet().removeIf(k -> k.startsWith(ANCHOR_PREFIX) || k.startsWith("encounter:dt_"));
        for (var e : gs.npcs.entrySet()) if (e.getKey().startsWith(ANCHOR_PREFIX)) e.getValue().variant = "";
        gs.markDirty();
        WorldChanges.apply(player.getServer());
        say(player, "Reset " + n + " test quest(s) and test-world state. Your story saves are separate and untouched.");
    }

    public static void sendInfo(ServerPlayerEntity player) {
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        JsonObject o = new JsonObject();
        JsonArray qs = new JsonArray();
        for (QuestDef q : testQuests()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", q.id);
            j.addProperty("title", q.title);
            var prog = p.quest(q.id);
            j.addProperty("status", prog == null ? "NOT STARTED" : prog.status == PlayerProfile.QuestProgress.Status.ACTIVE ? "stage: " + prog.stage : prog.status.name());
            qs.add(j);
        }
        o.add("quests", qs);
        JsonArray anchors = new JsonArray();
        testAnchors().forEach(anchors::add);
        o.add("anchors", anchors);
        o.addProperty("difficulty", GameState.get(player.getServer()).difficulty);
        o.addProperty("enemies", EncounterService.activeCount());
        Net.send(player, "dev_info", o);
    }

    /** Resolve "@x,y,z" checkpoints set by the dev menu; null if not of that form. */
    public static BlockPos parseCheckpoint(String cp) {
        if (cp == null || !cp.startsWith("@")) return null;
        String[] f = cp.substring(1).split(",");
        if (f.length != 3) return null;
        try { return new BlockPos(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])); } catch (Exception e) { return null; }
    }

    public static Map<String, String> labels() {
        return Map.of();
    }
}
