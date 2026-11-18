package dev.hollowatlas.npc;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.NpcRecord;
import dev.hollowatlas.world.LocationIndex;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tiered resident simulation.
 * <ul>
 *   <li>Tier A (player within 48 blocks): live entity, full brain (navigation + work animations).</li>
 *   <li>Tier B (48-128 blocks): live entity keeps following its schedule (cheap, same brain).</li>
 *   <li>Tier C (no player nearby / chunk unloaded): no entity; position advances abstractly to the
 *       scheduled anchor so residents are somewhere sensible when the area loads again.</li>
 * </ul>
 * Schedules are re-resolved once per second; new path searches are rate-limited globally.
 */
public final class NpcDirector {
    public static final Map<String, NpcDefinition> DEFS = ContentManager.register("npcs", NpcDefinition.class);

    private static final double SPAWN_RANGE = 96, DESPAWN_RANGE = 128;
    private static final int PATH_TOKENS_PER_TICK = 3;
    private static final Map<String, NpcEntity> LIVE = new HashMap<>();
    private static final Map<String, Long> BARK_COOLDOWN = new HashMap<>();
    private static int pathTokens;
    // profiling counters (per second)
    private static int pathsThisSecond, pathsLastSecond, recoveries, spawns, despawns;

    private NpcDirector() {}

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof NpcEntity npc && LIVE.get(npc.npcId()) != npc) {
                npc.discard(); // stray duplicate (should not happen: residents are never saved to chunks)
            }
        });
        ContentManager.validator((cm, problems) -> {
            for (NpcDefinition d : DEFS.values()) {
                for (NpcDefinition.ScheduleEntry e : d.schedule) checkAnchors(d, e, problems);
                for (NpcDefinition.ScheduleEntry e : d.rain) checkAnchors(d, e, problems);
                d.variants.values().forEach(list -> list.forEach(e -> checkAnchors(d, e, problems)));
                if (d.fallback_anchor != null && !LocationIndex.has(d.fallback_anchor))
                    problems.add("npc " + d.id + ": unknown fallback anchor " + d.fallback_anchor);
            }
        });
    }

    private static void checkAnchors(NpcDefinition d, NpcDefinition.ScheduleEntry e, List<String> problems) {
        if (e.anchor != null && !LocationIndex.has(e.anchor)) problems.add("npc " + d.id + ": unknown anchor " + e.anchor);
        for (String r : e.route) if (!LocationIndex.has(r)) problems.add("npc " + d.id + ": unknown route anchor " + r);
        if ((e.anchor == null || e.anchor.isEmpty()) && e.route.isEmpty()) problems.add("npc " + d.id + ": schedule entry without anchor");
    }

    public static boolean tryPathToken() {
        if (pathTokens <= 0) return false;
        pathTokens--;
        pathsThisSecond++;
        return true;
    }

    public static void countRecovery() { recoveries++; }

    public static NpcEntity live(String id) {
        NpcEntity e = LIVE.get(id);
        return e != null && !e.isRemoved() ? e : null;
    }

    public static Map<String, NpcEntity> liveView() { return java.util.Collections.unmodifiableMap(LIVE); }

    public static String profile() {
        long live = LIVE.values().stream().filter(e -> !e.isRemoved()).count();
        return String.format("residents: %d defined, %d live, %d abstract | path searches/s: %d | recoveries: %d | spawns: %d despawns: %d",
                DEFS.size(), live, DEFS.size() - live, pathsLastSecond, recoveries, spawns, despawns);
    }

    public static void onServerStopping() {
        LIVE.values().forEach(Entity::discard);
        LIVE.clear();
    }

    public static void tick(MinecraftServer server) {
        pathTokens = PATH_TOKENS_PER_TICK;
        if (server.getTicks() % 20 != 0) return;
        pathsLastSecond = pathsThisSecond;
        pathsThisSecond = 0;
        ServerWorld world = server.getOverworld();
        GameState state = GameState.get(server);
        double hour = ScheduleService.hour(world);
        boolean raining = world.isRaining();
        for (NpcDefinition def : DEFS.values()) {
            try {
                update(world, state, def, hour, raining);
            } catch (Exception e) {
                HollowAtlas.LOGGER.error("Resident {} update failed", def.id, e);
            }
        }
    }

    private static void update(ServerWorld world, GameState state, NpcDefinition def, double hour, boolean raining) {
        NpcRecord rec = state.npc(def.id);
        NpcDefinition.ScheduleEntry entry = ScheduleService.resolve(def, hour, raining, rec.variant);
        if (entry == null) return;
        String anchor = ScheduleService.anchorFor(entry, hour);
        if (!rec.placed || rec.anchor.isEmpty()) {
            abstractAdvance(rec, anchor);
            rec.placed = true;
            state.markDirty();
        }
        NpcEntity e = LIVE.get(def.id);
        if (e != null && e.isRemoved()) { LIVE.remove(def.id); e = null; }
        Vec3d at = e != null ? e.getPos() : new Vec3d(rec.x, rec.y, rec.z);
        double nearest = nearestPlayer(world, at);

        if (e == null) {
            // Tier C: progress abstractly toward the scheduled place.
            if (!entry.activity.equals(rec.task) || !anchor.equals(rec.anchor)) {
                abstractAdvance(rec, anchor);
                rec.task = entry.activity;
                state.markDirty();
            }
            BlockPos bp = BlockPos.ofFloored(rec.x, rec.y, rec.z);
            if (nearest < SPAWN_RANGE && world.isChunkLoaded(ChunkPos.toLong(bp.getX() >> 4, bp.getZ() >> 4))
                    && !visibleToPlayers(world, new Vec3d(rec.x, rec.y, rec.z), 20)) {
                spawn(world, def, rec, entry, hour);
            }
            return;
        }
        if (nearest > DESPAWN_RANGE) {
            rec.x = e.getX(); rec.y = e.getY(); rec.z = e.getZ();
            LIVE.remove(def.id);
            e.discard();
            despawns++;
            state.markDirty();
            return;
        }
        NpcBrain brain = e.npcBrain();
        if (brain != null) brain.setEntry(entry, hour);
        rec.x = e.getX(); rec.y = e.getY(); rec.z = e.getZ();
        rec.task = entry.activity;
        rec.anchor = brain != null && brain.anchor() != null ? brain.anchor() : anchor;
        state.markDirty();
        // keep residents inside the authored region; recover from falls into water/void
        if (!LocationIndex.inRegion(e.getX(), e.getZ()) || e.getY() < world.getBottomY() + 5) {
            BlockPos home = LocationIndex.pos(def.fallback_anchor != null ? def.fallback_anchor : anchor);
            e.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
            recoveries++;
        }
        ambientBark(world, e, def, hour, raining);
    }

    private static void abstractAdvance(NpcRecord rec, String anchor) {
        if (anchor == null || !LocationIndex.has(anchor)) return;
        BlockPos p = LocationIndex.pos(anchor);
        rec.x = p.getX() + 0.5; rec.y = p.getY(); rec.z = p.getZ() + 0.5;
        rec.anchor = anchor;
    }

    private static void spawn(ServerWorld world, NpcDefinition def, NpcRecord rec, NpcDefinition.ScheduleEntry entry, double hour) {
        NpcEntity e = NpcRegistry.RESIDENT.create(world);
        if (e == null) return;
        e.bind(def);
        e.refreshPositionAndAngles(rec.x, rec.y, rec.z, world.random.nextFloat() * 360f, 0);
        LIVE.put(def.id, e);
        world.spawnEntity(e);
        e.npcBrain().setEntry(entry, hour);
        spawns++;
    }

    private static double nearestPlayer(ServerWorld world, Vec3d pos) {
        double best = Double.MAX_VALUE;
        for (PlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            best = Math.min(best, p.getPos().distanceTo(pos));
        }
        return best;
    }

    private static boolean visibleToPlayers(ServerWorld world, Vec3d pos, double range) {
        for (PlayerEntity p : world.getPlayers()) {
            if (p.getPos().distanceTo(pos) < range && hasLineOfSight(world, p.getEyePos(), pos.add(0, 1.2, 0))) return true;
        }
        return false;
    }

    public static boolean hasLineOfSight(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE,
                net.minecraft.block.ShapeContext.absent())).getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------- speech

    /** Context-sensitive one-liners when a player comes close. Rate limited per resident/player. */
    private static void ambientBark(ServerWorld world, NpcEntity e, NpcDefinition def, double hour, boolean raining) {
        if (e.isSleeping()) return;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.squaredDistanceTo(e) > 4.5 * 4.5) continue;
            String key = def.id + "|" + p.getUuid();
            long now = world.getTime();
            Long last = BARK_COOLDOWN.get(key);
            if (last != null && now - last < 20 * 150) continue;
            if (!hasLineOfSight(world, p.getEyePos(), e.getEyePos())) continue;
            BARK_COOLDOWN.put(key, now);
            String ctx = raining && def.barks.containsKey("rain") ? "rain"
                    : hour < 11 && hour >= 5 ? "morning" : hour >= 19 || hour < 5 ? "night" : "day";
            if (!def.barks.containsKey(ctx)) ctx = "greet";
            say(world, e, pick(def.barks.get(ctx), world), 12);
        }
    }

    public static void bark(NpcEntity e, String context) {
        NpcDefinition def = DEFS.get(e.npcId());
        if (def == null) return;
        ServerWorld w = (ServerWorld) e.getWorld();
        say(w, e, pick(def.barks.get(context), w), 14);
    }

    private static String pick(List<String> lines, ServerWorld world) {
        if (lines == null || lines.isEmpty()) return null;
        return lines.get(world.random.nextInt(lines.size()));
    }

    /** Speech is delivered as chat to nearby players only, styled as overheard talk. */
    public static void say(ServerWorld world, NpcEntity e, String line, double range) {
        if (line == null) return;
        Text msg = Text.literal(e.getName().getString()).formatted(Formatting.GOLD)
                .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(line).formatted(Formatting.GRAY));
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(e) <= range * range) p.sendMessage(msg, false);
        }
    }

    public static UUID uuidOf(String id) {
        NpcEntity e = live(id);
        return e == null ? null : e.getUuid();
    }
}
