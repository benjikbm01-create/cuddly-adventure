package dev.hollowatlas.combat;

import com.google.gson.JsonObject;
import dev.hollowatlas.inventory.HudSync;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.world.LocationIndex;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Action-combat layer on top of vanilla melee. Vanilla left-click stays the light attack; this adds
 * stamina, a telegraphed heavy strike, a dodge with invulnerability frames, guarding, a timed parry
 * that opens a counter window, guard breaks and difficulty-scaled incoming damage.
 * All rules run on the server; the client only sends intents.
 */
public final class CombatService {
    public static final float MAX_STAMINA = 100f;
    private static final float LIGHT_COST = 5, HEAVY_COST = 28, DODGE_COST = 22, BLOCK_COST_PER_DMG = 6;
    private static final net.minecraft.util.Identifier GUARD_SLOW = dev.hollowatlas.HollowAtlas.id("guard_slow");

    public static final class State {
        long lastStaminaUse;
        long dodgeIframesUntil, dodgeReadyAt;
        boolean guarding;
        long guardStart;
        long exhaustedUntil;
        boolean exhausted;
        long counterUntil;
        long heavyReadyAt;
        long heavyStrikeAt = -1;
        long staggerUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final ThreadLocal<Boolean> REENTRY = ThreadLocal.withInitial(() -> false);

    private CombatService() {}

    public static State state(PlayerEntity p) { return STATES.computeIfAbsent(p.getUuid(), u -> new State()); }

    public static boolean isExhausted(PlayerEntity p) { return state(p).exhausted; }

    public static boolean isGuarding(PlayerEntity p) { return state(p).guarding; }

    public static double difficultyMultiplier(MinecraftServer server) {
        return switch (GameState.get(server).difficulty) {
            case "story" -> 0.5;
            case "hard" -> 1.35;
            case "expert" -> 1.7;
            default -> 1.0;
        };
    }

    public static int parryWindow(MinecraftServer server) {
        return switch (GameState.get(server).difficulty) {
            case "story" -> 9;
            case "hard" -> 5;
            case "expert" -> 4;
            default -> 6;
        };
    }

    /** Spend stamina; returns false (and does nothing) if not enough or exhausted. */
    public static boolean spend(ServerPlayerEntity player, float amount) {
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        State s = state(player);
        if (s.exhausted || p.stamina < amount) {
            player.sendMessage(Text.translatable("message.hollowatlas.no_stamina").formatted(Formatting.RED), true);
            return false;
        }
        p.stamina -= amount;
        s.lastStaminaUse = player.getWorld().getTime();
        if (p.stamina <= 0.5f) exhaust(player, s, p);
        HudSync.mark(player);
        return true;
    }

    private static void exhaust(ServerPlayerEntity player, State s, PlayerProfile p) {
        p.stamina = 0;
        s.exhausted = true;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1, false, false, true));
        player.sendMessage(Text.translatable("message.hollowatlas.exhausted").formatted(Formatting.RED), true);
    }

    public static void init() {
        Net.on("heavy", (player, body) -> heavy(player));
        Net.on("dodge", (player, body) -> dodge(player, body.get("x").getAsFloat(), body.get("z").getAsFloat()));
        Net.on("guard", (player, body) -> guard(player, body.get("on").getAsBoolean()));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            // light attack: small stamina cost; never blocked, exhaustion only weakens it
            PlayerProfile p = GameState.get(sp.getServer()).profile(sp.getUuid());
            State s = state(sp);
            if (!s.exhausted) {
                p.stamina = Math.max(0, p.stamina - LIGHT_COST);
                s.lastStaminaUse = world.getTime();
                if (p.stamina <= 0.5f) exhaust(sp, s, p);
            }
            if (s.guarding) setGuard(sp, false);
            HudSync.mark(sp);
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatService::allowDamage);

        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (alive) return;
            STATES.remove(newP.getUuid());
            GameState gs = GameState.get(newP.getServer());
            PlayerProfile p = gs.profile(newP.getUuid());
            p.stamina = MAX_STAMINA;
            p.focus = dev.hollowatlas.ability.AbilityService.MAX_FOCUS;
            int cost = Math.min(25, p.coins / 10);
            p.coins -= cost;
            gs.markDirty();
            if (LocationIndex.has(p.checkpoint)) {
                BlockPos cp = LocationIndex.pos(p.checkpoint);
                newP.teleport(newP.getServerWorld(), cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, newP.getYaw(), 0);
            }
            newP.sendMessage(Text.translatable("message.hollowatlas.recovered", cost).formatted(Formatting.GRAY), false);
            EncounterService.onPlayerDeath(newP);
            HudSync.mark(newP);
        });
    }

    // ------------------------------------------------------------- intents

    private static void heavy(ServerPlayerEntity player) {
        State s = state(player);
        long now = player.getWorld().getTime();
        if (now < s.heavyReadyAt || s.heavyStrikeAt > 0 || now < s.staggerUntil) return;
        if (!spend(player, HEAVY_COST)) return;
        if (s.guarding) setGuard(player, false);
        s.heavyStrikeAt = now + 8;              // short wind-up the player commits to
        s.heavyReadyAt = now + 22;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 2, false, false, false));
        player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.PLAYERS, 0.4f, 1.6f);
    }

    private static void releaseHeavy(ServerPlayerEntity player, State s) {
        s.heavyStrikeAt = -1;
        player.swingHand(Hand.MAIN_HAND, true);
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1f).multiply(1, 0, 1).normalize();
        Vec3d center = player.getPos().add(look.multiply(1.8)).add(0, 1, 0);
        double base = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        boolean hitAny = false;
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(1.6, 1.2, 1.6),
                e -> e != player && e.isAlive() && !(e instanceof PlayerEntity) && !(e instanceof dev.hollowatlas.npc.NpcEntity))) {
            float dmg = (float) (base * 1.9);
            if (target instanceof EnemyEntity enemy) {
                if (enemy.isBlocking()) { enemy.guardBreak(); dmg *= 0.6f; }
            }
            target.damage(world.getDamageSources().playerAttack(player), dmg);
            target.takeKnockback(0.9, -look.x, -look.z);
            hitAny = true;
        }
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        world.playSound(null, player.getBlockPos(), hitAny ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE,
                SoundCategory.PLAYERS, 0.9f, 0.8f);
    }

    private static void dodge(ServerPlayerEntity player, float ix, float iz) {
        State s = state(player);
        long now = player.getWorld().getTime();
        if (now < s.dodgeReadyAt || now < s.staggerUntil || !player.isOnGround()) return;
        if (!spend(player, DODGE_COST)) return;
        if (s.guarding) setGuard(player, false);
        // input is in player-local space: x = strafe (right +), z = forward (+)
        Vec3d fwd = Vec3d.fromPolar(0, player.getYaw());
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
        Vec3d dir = fwd.multiply(iz).add(right.multiply(ix));
        if (dir.lengthSquared() < 0.01) dir = fwd.multiply(-1);    // default: hop back
        dir = dir.normalize();
        player.setVelocity(dir.x * 1.05, 0.22, dir.z * 1.05);
        player.velocityModified = true;
        s.dodgeIframesUntil = now + 7;
        s.dodgeReadyAt = now + 14;
        player.getServerWorld().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.05, 0.3, 0.01);
        player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.PLAYERS, 0.5f, 1.8f);
    }

    private static void guard(ServerPlayerEntity player, boolean on) {
        State s = state(player);
        if (on && (s.exhausted || player.getWorld().getTime() < s.staggerUntil)) return;
        setGuard(player, on);
    }

    private static void setGuard(ServerPlayerEntity player, boolean on) {
        State s = state(player);
        if (s.guarding == on) return;
        s.guarding = on;
        var attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr != null) {
            attr.removeModifier(GUARD_SLOW);
            if (on) attr.addTemporaryModifier(new EntityAttributeModifier(GUARD_SLOW, -0.45, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (on) s.guardStart = player.getWorld().getTime();
        HudSync.mark(player);
    }

    // -------------------------------------------------------------- damage

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (REENTRY.get()) return true;
        MinecraftServer server = entity.getServer();
        if (server == null) return true;
        // outgoing counter bonus
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && !(entity instanceof PlayerEntity)) {
            State as = state(attacker);
            if (attacker.getWorld().getTime() < as.counterUntil) {
                as.counterUntil = 0;
                attacker.getServerWorld().spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1, entity.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
                return reapply(entity, source, amount * 1.6f);
            }
            return true;
        }
        if (!(entity instanceof ServerPlayerEntity player)) return true;
        State s = state(player);
        long now = player.getWorld().getTime();
        if (now < s.dodgeIframesUntil && source.getAttacker() != null) {
            player.getServerWorld().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            return false; // clean dodge
        }
        double mult = difficultyMultiplier(server);
        Entity attacker = source.getAttacker();
        if (s.guarding && attacker != null && inFront(player, attacker)) {
            if (now - s.guardStart <= parryWindow(server)) {
                // PARRY: negate, stagger the attacker, open a counter window
                s.counterUntil = now + 30;
                if (attacker instanceof EnemyEntity enemy) enemy.stagger(40);
                else if (attacker instanceof MobEntity mob) mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3));
                if (attacker instanceof LivingEntity la) la.takeKnockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1f, 1.6f);
                player.getServerWorld().spawnParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.2, player.getZ(), 14, 0.4, 0.4, 0.4, 0.3);
                player.sendMessage(Text.translatable("message.hollowatlas.parry").formatted(Formatting.AQUA), true);
                return false;
            }
            PlayerProfile p = GameState.get(server).profile(player.getUuid());
            float cost = (float) (amount * BLOCK_COST_PER_DMG * mult);
            p.stamina -= cost;
            s.lastStaminaUse = now;
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.8f, 0.9f);
            if (p.stamina <= 0) {
                // GUARD BREAK: guard collapses, take a portion, short stagger
                exhaust(player, s, p);
                setGuard(player, false);
                s.staggerUntil = now + 30;
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 3, false, false, true));
                player.sendMessage(Text.translatable("message.hollowatlas.guard_break").formatted(Formatting.RED), true);
                HudSync.mark(player);
                return reapply(player, source, (float) (amount * 0.6 * mult));
            }
            HudSync.mark(player);
            return reapply(player, source, (float) (amount * 0.25 * mult));
        }
        if (mult != 1.0 && attacker != null) return reapply(player, source, (float) (amount * mult));
        return true;
    }

    private static boolean reapply(LivingEntity entity, DamageSource source, float amount) {
        if (amount <= 0.01f) return false;
        REENTRY.set(true);
        try {
            entity.damage(source, amount);
        } finally {
            REENTRY.set(false);
        }
        return false;
    }

    private static boolean inFront(PlayerEntity player, Entity attacker) {
        Vec3d look = player.getRotationVec(1f).multiply(1, 0, 1).normalize();
        Vec3d to = attacker.getPos().subtract(player.getPos()).multiply(1, 0, 1);
        if (to.lengthSquared() < 1e-4) return true;
        return look.dotProduct(to.normalize()) > 0.2;   // ~156 degree arc
    }

    // ---------------------------------------------------------------- tick

    public static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        GameState gs = GameState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            State s = state(player);
            PlayerProfile p = gs.profile(player.getUuid());
            if (s.heavyStrikeAt > 0 && now >= s.heavyStrikeAt) releaseHeavy(player, s);
            if (!s.guarding && now - s.lastStaminaUse > 15 && p.stamina < MAX_STAMINA) {
                p.stamina = Math.min(MAX_STAMINA, p.stamina + (s.exhausted ? 0.9f : 1.3f));
                if (s.exhausted && p.stamina >= 40) {
                    s.exhausted = false;
                }
            } else if (s.guarding && p.stamina < MAX_STAMINA && now - s.lastStaminaUse > 30) {
                p.stamina = Math.min(MAX_STAMINA, p.stamina + 0.25f);
            }
        }
    }

    public static void sendHint(ServerPlayerEntity player, String key) {
        JsonObject o = new JsonObject();
        o.addProperty("key", key);
        Net.send(player, "hint", o);
    }
}
