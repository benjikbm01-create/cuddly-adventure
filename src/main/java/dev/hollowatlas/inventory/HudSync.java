package dev.hollowatlas.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hollowatlas.ability.AbilityService;
import dev.hollowatlas.combat.CombatService;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pushes authoritative HUD numbers (coins, stamina, focus, ability cooldowns, combat state) to clients.
 * The client never computes these values itself, so the HUD cannot drift from server state.
 */
public final class HudSync {
    private static final Set<UUID> DIRTY = new HashSet<>();

    private HudSync() {}

    public static void mark(ServerPlayerEntity p) { DIRTY.add(p.getUuid()); }

    public static void tick(MinecraftServer server) {
        boolean periodic = server.getTicks() % 4 == 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!periodic && !DIRTY.contains(p.getUuid())) continue;
            send(p);
        }
        DIRTY.clear();
    }

    public static void send(ServerPlayerEntity player) {
        PlayerProfile p = GameState.get(player.getServer()).profile(player.getUuid());
        JsonObject o = new JsonObject();
        o.addProperty("coins", p.coins);
        o.addProperty("stamina", p.stamina);
        o.addProperty("stamina_max", CombatService.MAX_STAMINA);
        o.addProperty("focus", p.focus);
        o.addProperty("focus_max", AbilityService.MAX_FOCUS);
        o.addProperty("exhausted", CombatService.isExhausted(player));
        o.addProperty("guarding", CombatService.isGuarding(player));
        o.addProperty("difficulty", GameState.get(player.getServer()).difficulty);
        JsonArray ab = AbilityService.hud(player, p);
        o.add("abilities", ab);
        Net.send(player, "hud", o);
    }
}
