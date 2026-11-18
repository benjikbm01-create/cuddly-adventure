package dev.hollowatlas.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.combat.CombatService;
import dev.hollowatlas.combat.EnemyEntity;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.inventory.HudSync;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.investigation.Interactions;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ability registry + executor. Numbers live in {@code data/hollowatlas/abilities/*.json}; behaviour is
 * implemented per id here. Abilities spend either STAMINA (trained techniques) or FOCUS (rare
 * instruments), have cooldowns, obey collisions/protection (no teleporting, no block edits) and are
 * unlocked through play. There are no ultimates.
 */
public final class AbilityService {
    public static final float MAX_FOCUS = 100f;
    public static final Map<String, AbilityDef> DEFS = ContentManager.register("abilities", AbilityDef.class);

    public static final class AbilityDef implements ContentManager.Validatable {
        public String id;
        public String name;
        public String description;
        public String category = "movement";   // movement | investigation | combat
        public String pool = "stamina";         // stamina | focus
        public float cost = 20;
        public int cooldown = 60;               // ticks
        public String token_item;               // hotbar token item id
        public JsonElement unlock;              // documentation of the unlock condition (enforced by content)
        public List<Branch> branches = new ArrayList<>();
        @Override public String id() { return id; }
        @Override public void validate(String file, List<String> problems) {
            if (!List.of("stamina", "focus").contains(pool)) problems.add(file + ": bad pool " + pool);
            if (cooldown < 10) problems.add(file + ": cooldown too short (no spam abilities)");
        }
    }

    /** Future upgrade path hook (v0.1 ships definitions only). */
    public static final class Branch { public String id, name, description; public int cost; }

    private static final Map<UUID, Long> ECHO_UNTIL = new HashMap<>();

    private AbilityService() {}

    public static void init() {
        Net.on("ability", (player, body) -> {
            PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
            String id;
            if (body.has("slot")) {
                int slot = body.get("slot").getAsInt();
                if (slot < 0 || slot > 1) return;
                id = p.settings.getOrDefault("ability_slot_" + slot, "");
            } else id = body.get("id").getAsString();
            use(player, id);
        });
        Net.on("ability_slot", (player, body) -> {
            int slot = body.get("slot").getAsInt();
            String id = body.get("id").getAsString();
            PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
            if (slot < 0 || slot > 1 || (!id.isEmpty() && !p.abilities.contains(id))) return;
            p.settings.put("ability_slot_" + slot, id);
            GameState.get(player.getServer()).markDirty();
            HudSync.mark(player);
        });
    }

    public static void unlock(ServerPlayerEntity player, String id) {
        AbilityDef def = DEFS.get(id);
        if (def == null) { HollowAtlas.LOGGER.warn("unlock: unknown ability {}", id); return; }
        GameState gs = GameState.get(player.getServer());
        PlayerProfile p = gs.profile(player.getUuid());
        if (!p.abilities.add(id)) return;
        for (int s = 0; s < 2; s++) {
            if (p.settings.getOrDefault("ability_slot_" + s, "").isEmpty()) { p.settings.put("ability_slot_" + s, id); break; }
        }
        gs.markDirty();
        if (def.token_item != null) RewardService.give(player, def.token_item, 1);
        player.sendMessage(Text.translatable("message.hollowatlas.ability_learned", def.name).formatted(Formatting.LIGHT_PURPLE), false);
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.6f);
        CombatService.sendHint(player, "hint.hollowatlas.ability." + id);
        HudSync.mark(player);
    }

    public static void use(ServerPlayerEntity player, String id) {
        AbilityDef def = DEFS.get(id);
        if (def == null) return;
        GameState gs = GameState.get(player.getServer());
        PlayerProfile p = gs.profile(player.getUuid());
        if (!p.abilities.contains(id)) {
            player.sendMessage(Text.translatable("message.hollowatlas.ability_unknown").formatted(Formatting.GRAY), true);
            return;
        }
        long now = player.getWorld().getTime();
        long ready = p.cooldowns.getOrDefault(id, 0L);
        if (now < ready) {
            player.sendMessage(Text.translatable("message.hollowatlas.ability_cooldown", def.name, (ready - now + 19) / 20).formatted(Formatting.GRAY), true);
            return;
        }
        if (!canExecute(player, id)) return;
        if (def.pool.equals("focus")) {
            if (p.focus < def.cost) {
                player.sendMessage(Text.translatable("message.hollowatlas.no_focus").formatted(Formatting.RED), true);
                return;
            }
            p.focus -= def.cost;
        } else if (!CombatService.spend(player, def.cost)) {
            return;
        }
        p.cooldowns.put(id, now + def.cooldown);
        gs.markDirty();
        execute(player, id);
        HudSync.mark(player);
    }

    private static boolean canExecute(ServerPlayerEntity player, String id) {
        if (id.equals("windstep") && !player.isOnGround() && !player.isTouchingWater()) {
            player.sendMessage(Text.translatable("message.hollowatlas.windstep_ground").formatted(Formatting.GRAY), true);
            return false;
        }
        return true;
    }

    private static void execute(ServerPlayerEntity player, String id) {
        ServerWorld w = player.getServerWorld();
        switch (id) {
            case "windstep" -> {
                // trained burst of footwork: velocity only, so walls, doors and the world border still apply
                Vec3d fwd = Vec3d.fromPolar(0, player.getYaw());
                player.setVelocity(fwd.x * 1.9, 0.32, fwd.z * 1.9);
                player.velocityModified = true;
                player.fallDistance = 0;
                CombatService.state(player);
                w.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
                w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 0.7f, 1.3f);
            }
            case "echo_sight" -> {
                ECHO_UNTIL.put(player.getUuid(), w.getTime() + 200);
                w.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1f, 0.8f);
                for (EnemyEntity e : w.getEntitiesByClass(EnemyEntity.class, new Box(player.getBlockPos()).expand(24), x -> true)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
                }
                int found = traces(player, w, true);
                player.sendMessage(Text.translatable(found > 0 ? "message.hollowatlas.echo_found" : "message.hollowatlas.echo_none", found)
                        .formatted(Formatting.LIGHT_PURPLE), true);
            }
            default -> { }
        }
    }

    /** Echo Sight: shows shimmering traces over nearby clues/caches the player hasn't resolved yet. */
    private static int traces(ServerPlayerEntity player, ServerWorld w, boolean count) {
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        int n = 0;
        for (Map.Entry<BlockPos, Interactions.Def> e : Interactions.all().entrySet()) {
            Interactions.Def d = e.getValue();
            if (!List.of("inspect", "cache", "puzzle").contains(d.type) || !Interactions.allowed(player.getServer(), d)) continue;
            if (p.flags.contains("int:" + d.id) && !"puzzle".equals(d.type)) continue;
            BlockPos pos = e.getKey();
            if (pos.getSquaredDistance(player.getBlockPos()) > 24 * 24) continue;
            n++;
            w.spawnParticles(player, ParticleTypes.END_ROD, true, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.15, 0.6, 0.15, 0.01);
        }
        return n;
    }

    public static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        GameState gs = GameState.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerProfile p = gs.profile(player.getUuid());
            if (p.focus < MAX_FOCUS) p.focus = Math.min(MAX_FOCUS, p.focus + 0.1f);   // 2 per second
            Long until = ECHO_UNTIL.get(player.getUuid());
            if (until != null) {
                if (now > until) ECHO_UNTIL.remove(player.getUuid());
                else if (now % 10 == 0) traces(player, player.getServerWorld(), false);
            }
        }
    }

    public static JsonArray hud(ServerPlayerEntity player, PlayerProfile p) {
        JsonArray arr = new JsonArray();
        long now = player.getWorld().getTime();
        for (String id : p.abilities) {
            AbilityDef def = DEFS.get(id);
            if (def == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("name", def.name);
            o.addProperty("pool", def.pool);
            o.addProperty("cost", def.cost);
            o.addProperty("cd_total", def.cooldown);
            o.addProperty("cd_left", Math.max(0, p.cooldowns.getOrDefault(id, 0L) - now));
            int slot = -1;
            for (int s = 0; s < 2; s++) if (id.equals(p.settings.get("ability_slot_" + s))) slot = s;
            o.addProperty("slot", slot);
            o.addProperty("token", def.token_item == null ? "" : def.token_item);
            arr.add(o);
        }
        return arr;
    }
}
