package dev.hollowatlas.combat;

import com.google.gson.JsonElement;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.logic.Logic;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.world.LocationIndex;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Curated encounters at authored anchors (no random wilderness spawning). An encounter spawns when a
 * player approaches and its conditions hold, resets if the player dies or leaves, and records a
 * cleared flag (once-only, or reopening the next day).
 */
public final class EncounterService {
    public static final Map<String, Def> DEFS = ContentManager.register("encounters", Def.class);

    public static final class Def implements ContentManager.Validatable {
        public String id;
        public String anchor;
        public double trigger_radius = 22;
        public double leash = 40;
        public JsonElement when;
        public boolean daily = false;        // false: once only
        public List<Spawn> spawns = new ArrayList<>();
        public JsonElement on_clear;
        public String start_text;
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            if (anchor == null || !LocationIndex.has(anchor)) problems.add(file + ": unknown anchor " + anchor);
            for (Spawn s : spawns) if (!List.of("brigand", "slinger").contains(s.kind)) problems.add(file + ": unknown kind " + s.kind);
        }
    }

    public static final class Spawn { public String kind; public int dx, dz; }

    private static final Map<String, List<EnemyEntity>> ACTIVE = new HashMap<>();

    private EncounterService() {}

    public static String clearedFlag(Def d) { return "hollowatlas:encounter_cleared:" + d.id; }

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof EnemyEntity e)) return;
            if (source.getAttacker() instanceof ServerPlayerEntity p) QuestEngine.event(p, "kill", "hollowatlas:" + e.kind());
            String id = e.encounterId();
            List<EnemyEntity> list = ACTIVE.get(id);
            if (list == null) return;
            list.remove(e);
            if (list.isEmpty()) {
                ACTIVE.remove(id);
                Def d = DEFS.get(id);
                if (d == null) return;
                GameState gs = GameState.get(entity.getServer());
                gs.setFlag(clearedFlag(d), true);
                if (d.daily) gs.nodeCooldowns.put("encounter:" + d.id, entity.getWorld().getTime() + 24000L);
                if (source.getAttacker() instanceof ServerPlayerEntity p) {
                    p.sendMessage(Text.translatable("message.hollowatlas.encounter_cleared").formatted(Formatting.GREEN), true);
                    Logic.run(p, d.on_clear, Logic.Ctx.NONE);
                    QuestEngine.event(p, "event", "encounter:" + d.id);
                }
            }
        });
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        // limited encounter reset: the fight is restored to full the next time the player approaches
        for (List<EnemyEntity> list : ACTIVE.values()) list.forEach(EnemyEntity::discard);
        ACTIVE.clear();
    }

    public static int activeCount() { return ACTIVE.values().stream().mapToInt(List::size).sum(); }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 13) return;
        ServerWorld world = server.getOverworld();
        GameState gs = GameState.get(server);
        long now = world.getTime();
        for (Def d : DEFS.values()) {
            BlockPos at = LocationIndex.pos(d.anchor);
            List<EnemyEntity> live = ACTIVE.get(d.id);
            if (live != null) {
                live.removeIf(e -> e.isRemoved() || !e.isAlive());
                boolean anyNear = world.getPlayers().stream().anyMatch(p -> !p.isSpectator() && p.squaredDistanceTo(at.toCenterPos()) < 64 * 64);
                if (!anyNear) { live.forEach(EnemyEntity::discard); ACTIVE.remove(d.id); continue; }
                for (EnemyEntity e : live) {
                    if (e.squaredDistanceTo(at.toCenterPos()) > d.leash * d.leash) {
                        e.setTarget(null);
                        e.getNavigation().startMovingTo(at.getX(), at.getY(), at.getZ(), 1.2);
                    }
                }
                continue;
            }
            if (gs.flag(clearedFlag(d))) {
                if (!d.daily) continue;
                Long reopen = gs.nodeCooldowns.get("encounter:" + d.id);
                if (reopen != null && reopen > now) continue;
                gs.setFlag(clearedFlag(d), false);
            }
            ServerPlayerEntity trigger = null;
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isSpectator() || p.isCreative()) continue;
                if (p.squaredDistanceTo(at.toCenterPos()) < d.trigger_radius * d.trigger_radius && Logic.test(p, d.when, Logic.Ctx.NONE)) trigger = p;
            }
            if (trigger == null) continue;
            List<EnemyEntity> spawned = new ArrayList<>();
            for (Spawn s : d.spawns) {
                EnemyEntity e = CombatRegistry.ENEMY.create(world);
                if (e == null) continue;
                BlockPos sp = at.add(s.dx, 0, s.dz);
                int y = world.getBlockState(sp).isAir() && !world.getBlockState(sp.down()).isAir() ? sp.getY()
                        : world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
                if (Math.abs(y - at.getY()) > 6) y = at.getY();
                e.refreshPositionAndAngles(sp.getX() + 0.5, y, sp.getZ() + 0.5, world.random.nextFloat() * 360, 0);
                e.initialize(world, world.getLocalDifficulty(sp), SpawnReason.EVENT, null);
                e.setKind(s.kind);
                e.encounterId = d.id;
                e.setPersistent();
                world.spawnEntity(e);
                spawned.add(e);
            }
            ACTIVE.put(d.id, spawned);
            if (d.start_text != null) trigger.sendMessage(Text.literal(d.start_text).formatted(Formatting.RED, Formatting.ITALIC), false);
        }
    }
}
