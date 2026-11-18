package dev.hollowatlas.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

/**
 * Adventure-map game rules. Applied when the region is built and re-applied on every server start,
 * so a world copied between machines stays protected.
 */
public final class WorldRules {
    private WorldRules() {}

    public static void apply(MinecraftServer server) {
        GameRules r = server.getGameRules();
        r.get(GameRules.KEEP_INVENTORY).set(true, server);          // death keeps items (checkpoint system)
        r.get(GameRules.DO_MOB_SPAWNING).set(false, server);        // curated encounters only
        r.get(GameRules.DO_MOB_GRIEFING).set(false, server);        // no creeper/enderman block damage
        r.get(GameRules.DO_FIRE_TICK).set(false, server);           // no fire spread
        r.get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS).set(false, server);
        r.get(GameRules.DO_INSOMNIA).set(false, server);            // sleep is optional
        r.get(GameRules.DO_TRADER_SPAWNING).set(false, server);
        r.get(GameRules.DO_PATROL_SPAWNING).set(false, server);
        r.get(GameRules.DO_WARDEN_SPAWNING).set(false, server);
        r.get(GameRules.DISABLE_RAIDS).set(true, server);
        r.get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false, server);
        r.get(GameRules.SPAWN_RADIUS).set(0, server);
    }
}
