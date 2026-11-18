package dev.hollowatlas.npc;

import dev.hollowatlas.world.LocationIndex;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Per-entity behaviour executor. The {@link NpcDirector} decides WHAT a resident should be doing
 * (schedule entry + target anchor); the brain decides HOW: walking there with rate-limited
 * path planning, recovering when stuck, and performing a visibly distinct activity on arrival.
 */
public final class NpcBrain {
    private enum Phase { TRAVEL, ARRIVED }

    private final NpcEntity npc;
    private final NpcDefinition def;
    private NpcDefinition.ScheduleEntry entry;
    private String anchor;
    private BlockPos target;
    private BlockPos subTarget;   // wander/tend points around the anchor
    private Phase phase = Phase.TRAVEL;
    private int timer;
    private int routeIndex;
    private double bestDist = Double.MAX_VALUE;
    private int noProgress;
    private int stuckEvents;
    private int pathCooldown;
    private int pauseTicks;

    public NpcBrain(NpcEntity npc, NpcDefinition def) {
        this.npc = npc;
        this.def = def;
    }

    public NpcDefinition def() { return def; }
    public NpcDefinition.ScheduleEntry entry() { return entry; }
    public String anchor() { return anchor; }
    public String phase() { return phase.name(); }
    public int stuckEvents() { return stuckEvents; }

    /** Pause routine briefly (e.g. while talking with the player). */
    public void pause(int ticks) {
        pauseTicks = Math.max(pauseTicks, ticks);
        npc.getNavigation().stop();
    }

    public void setEntry(NpcDefinition.ScheduleEntry e, double hour) {
        if (e == entry) return;
        entry = e;
        routeIndex = 0;
        anchor = e.route != null && !e.route.isEmpty() ? e.route.get(0) : e.anchor;
        retarget();
        if (npc.isSleeping() && !"sleep".equals(e.activity)) npc.wakeUp();
        equip();
    }

    /** Entrance / stair-landing waypoints so residents take the real route into buildings and upstairs. */
    private final java.util.ArrayDeque<BlockPos> waypoints = new java.util.ArrayDeque<>();

    private void retarget() {
        target = anchor != null && LocationIndex.has(anchor) ? LocationIndex.pos(anchor) : npc.getBlockPos();
        subTarget = null;
        waypoints.clear();
        if (anchor != null && anchor.contains(".")) {
            String b = anchor.substring(0, anchor.indexOf('.'));
            String insideKey = b + ".inside", landingKey = b + ".landing";
            if (LocationIndex.has(insideKey)) {
                BlockPos inside = LocationIndex.pos(insideKey);
                boolean far = npc.getBlockPos().getSquaredDistance(target) > 5 * 5;
                boolean upstairs = LocationIndex.has(landingKey) && target.getY() >= inside.getY() + 4;
                boolean alreadyUp = upstairs && Math.abs(npc.getBlockY() - target.getY()) <= 1;
                if (far && !alreadyUp && npc.getBlockPos().getSquaredDistance(inside) > 3 * 3) waypoints.add(inside);
                if (upstairs && !alreadyUp) waypoints.add(LocationIndex.pos(landingKey));
            }
        }
        phase = Phase.TRAVEL;
        bestDist = Double.MAX_VALUE;
        noProgress = 0;
    }

    private void equip() {
        ItemStack held = ItemStack.EMPTY;
        if (entry != null && ("work".equals(entry.activity) || "patrol".equals(entry.activity)) && def.held != null && !def.held.isEmpty()) {
            held = new ItemStack(Registries.ITEM.get(Identifier.of(def.held)));
        }
        if ("eat".equals(entry == null ? "" : entry.activity)) held = new ItemStack(Items.BREAD);
        npc.setStackInHand(Hand.MAIN_HAND, held);
    }

    private ServerWorld world() { return (ServerWorld) npc.getWorld(); }

    public void tick() {
        if (entry == null || target == null) return;
        if (pauseTicks > 0) { pauseTicks--; return; }
        timer++;
        if (npc.isSleeping()) {
            if (!"sleep".equals(entry.activity)) npc.wakeUp();
            else return;
        }
        BlockPos goal = subTarget != null ? subTarget : !waypoints.isEmpty() && phase == Phase.TRAVEL ? waypoints.peek() : target;
        double dist = Math.sqrt(npc.getBlockPos().getSquaredDistance(goal));
        if (phase == Phase.TRAVEL) travel(goal, dist);
        else perform(dist);
    }

    private void travel(BlockPos goal, double dist) {
        double arrive = "sleep".equals(entry.activity) ? 1.6 : 1.8;
        if (!waypoints.isEmpty() && goal.equals(waypoints.peek()) && dist <= 2.2) {
            waypoints.poll();               // reached the entrance/landing: continue to the real target
            npc.getNavigation().stop();
            bestDist = Double.MAX_VALUE;
            noProgress = 0;
            pathCooldown = 0;
            return;
        }
        if (dist <= arrive) {
            npc.getNavigation().stop();
            if (subTarget != null) { subTarget = null; timer = 0; phase = Phase.ARRIVED; onSubArrive(); return; }
            phase = Phase.ARRIVED;
            timer = 0;
            onArrive();
            return;
        }
        if (pathCooldown > 0) pathCooldown--;
        if (npc.getNavigation().isIdle() && pathCooldown == 0 && NpcDirector.tryPathToken()) {
            npc.getNavigation().startMovingTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, def.speed);
            pathCooldown = 20;
        }
        if (timer % 20 == 0) {
            if (dist < bestDist - 0.5) { bestDist = dist; noProgress = 0; }
            else if (++noProgress >= 6) { recover(goal); }
        }
    }

    /** Safe positional recovery: re-plan first; if still blocked and nobody is watching, relocate. */
    private void recover(BlockPos goal) {
        noProgress = 0;
        bestDist = Double.MAX_VALUE;
        stuckEvents++;
        npc.getNavigation().stop();
        pathCooldown = 0;
        if (stuckEvents % 2 == 0 && !watched(npc.getPos()) && !watched(Vec3d.ofBottomCenter(goal))) {
            npc.refreshPositionAndAngles(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, npc.getYaw(), 0);
            NpcDirector.countRecovery();
        }
    }

    private boolean watched(Vec3d pos) {
        for (PlayerEntity p : world().getPlayers()) {
            if (p.squaredDistanceTo(pos) < 24 * 24) {
                if (NpcDirector.hasLineOfSight(world(), p.getEyePos(), pos.add(0, 1, 0))) return true;
            }
        }
        return false;
    }

    private void onArrive() {
        switch (entry.activity) {
            case "sleep" -> {
                BlockState st = world().getBlockState(target);
                if (st.getBlock() instanceof BedBlock) npc.sleep(target);
            }
            case "patrol" -> {
                if (entry.route != null && entry.route.size() > 1) {
                    routeIndex = (routeIndex + 1) % entry.route.size();
                    anchor = entry.route.get(routeIndex);
                    pauseTicks = 40 + npc.getRandom().nextInt(60); // look around at each waypoint
                    retarget();
                }
            }
            default -> { }
        }
    }

    private void onSubArrive() {
        if ("work".equals(entry.activity) && "tend".equals(entry.style)) {
            npc.swingHand(Hand.MAIN_HAND);
            world().spawnParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + 0.3, npc.getZ(), 4, 0.6, 0.2, 0.6, 0);
        }
    }

    private void perform(double dist) {
        if (dist > Math.max(4.5, entry.radius + 2.5)) { phase = Phase.TRAVEL; bestDist = Double.MAX_VALUE; return; }
        Random r = npc.getRandom();
        switch (entry.activity) {
            case "work" -> work(r);
            case "eat" -> {
                if (timer % 70 == 0) {
                    npc.swingHand(Hand.MAIN_HAND);
                    npc.playSound(SoundEvents.ENTITY_GENERIC_EAT, 0.5f, 1.0f);
                }
            }
            case "wander", "social" -> {
                if (entry.radius > 0 && timer > 160 + r.nextInt(200)) {
                    pickSubTarget(entry.radius);
                }
            }
            default -> { }
        }
    }

    private void work(Random r) {
        String style = entry.style == null ? "" : entry.style;
        ServerWorld w = world();
        switch (style) {
            case "hammer" -> {
                if (timer % 24 == 0) {
                    npc.swingHand(Hand.MAIN_HAND);
                    npc.playSound(SoundEvents.BLOCK_ANVIL_USE, 0.35f, 0.9f + r.nextFloat() * 0.2f);
                    w.spawnParticles(ParticleTypes.LAVA, npc.getX(), npc.getY() + 1.0, npc.getZ(), 1, 0.3, 0.1, 0.3, 0);
                }
            }
            case "tend" -> {
                if (timer > 120 + r.nextInt(120)) pickSubTarget(Math.max(4, entry.radius));
                else if (timer % 30 == 0) npc.swingHand(Hand.MAIN_HAND);
            }
            case "serve", "pack", "sweep" -> {
                if (timer % 45 == 0) npc.swingHand(Hand.MAIN_HAND);
                if (entry.radius > 0 && timer > 200 + r.nextInt(160)) pickSubTarget(entry.radius);
            }
            case "bake" -> {
                if (timer % 60 == 0) {
                    npc.swingHand(Hand.MAIN_HAND);
                    w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, npc.getX(), npc.getY() + 2.0, npc.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
                }
            }
            case "read" -> {
                if (timer % 100 == 0) npc.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.4f, 1.0f);
            }
            case "fish" -> {
                if (timer % 140 == 0) {
                    npc.swingHand(Hand.MAIN_HAND);
                    npc.playSound(SoundEvents.ENTITY_FISHING_BOBBER_THROW, 0.4f, 1.0f);
                }
            }
            case "vend" -> {
                if (timer % 400 == 0) NpcDirector.bark(npc, "work");
            }
            case "guard" -> {
                if (timer % 200 == 0) npc.setYaw(npc.getYaw() + 90);
            }
            default -> { }
        }
    }

    private void pickSubTarget(int radius) {
        timer = 0;
        Random r = npc.getRandom();
        for (int tries = 0; tries < 6; tries++) {
            BlockPos p = target.add(r.nextInt(radius * 2 + 1) - radius, 0, r.nextInt(radius * 2 + 1) - radius);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos q = p.up(dy);
                if (world().getBlockState(q).isAir() && world().getBlockState(q.up()).isAir()
                        && world().getBlockState(q.down()).isSolidBlock(world(), q.down())) {
                    subTarget = q;
                    phase = Phase.TRAVEL;
                    bestDist = Double.MAX_VALUE;
                    return;
                }
            }
        }
    }
}
