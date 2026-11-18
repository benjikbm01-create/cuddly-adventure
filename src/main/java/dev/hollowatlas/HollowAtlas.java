package dev.hollowatlas;

import dev.hollowatlas.command.AtlasCommands;
import dev.hollowatlas.state.GameState;
import dev.hollowatlas.state.PlayerProfile;
import dev.hollowatlas.state.StateBackup;
import dev.hollowatlas.world.BoundaryService;
import dev.hollowatlas.world.LocationIndex;
import dev.hollowatlas.world.MapRenderer;
import dev.hollowatlas.world.ProtectionService;
import dev.hollowatlas.world.RegionBuilder;
import dev.hollowatlas.world.WorldRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (server + client) entrypoint for The Hollow Atlas.
 */
public final class HollowAtlas implements ModInitializer {
    public static final String MOD_ID = "hollowatlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("The Hollow Atlas initializing (common)");
        ProtectionService.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> AtlasCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(StateBackup::backupIfMigrating);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RegionBuilder.tick(server);
            BoundaryService.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer(), server));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ProtectionService.enforceMode(newPlayer));
    }

    private void onServerStarted(MinecraftServer server) {
        LocationIndex.model();
        WorldRules.apply(server);
        GameState state = GameState.get(server);
        boolean stopAfter = Boolean.getBoolean("hollowatlas.autobuild.stop");
        Runnable afterBuild = () -> {
            renderMap(server);
            if (stopAfter) {
                LOGGER.info("Autobuild finished; stopping server.");
                server.stop(false);
            }
        };
        if (state.flag(RegionBuilder.BUILT_FLAG) && Boolean.getBoolean("hollowatlas.rendermap")) {
            afterBuild.run();
        }
        if (!state.flag(RegionBuilder.BUILT_FLAG)) {
            if (Boolean.getBoolean("hollowatlas.autobuild")) {
                RegionBuilder.start(server.getOverworld(), LocationIndex.model(), afterBuild);
            } else {
                LOGGER.warn("This world does not contain the Hollow Atlas region. Use the bundled world, "
                        + "or run '/atlas build' as an operator on a superflat world.");
            }
        }
    }

    public static void renderMap(MinecraftServer server) {
        try {
            MapRenderer.render(server.getOverworld(),
                    server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("hollowatlas").resolve("region_map.png"), 1);
        } catch (Exception e) {
            LOGGER.error("Map render failed", e);
        }
    }

    private void onJoin(ServerPlayerEntity player, MinecraftServer server) {
        ProtectionService.enforceMode(player);
        GameState state = GameState.get(server);
        if (!state.flag(RegionBuilder.BUILT_FLAG)) {
            player.sendMessage(Text.translatable("message.hollowatlas.no_region").formatted(Formatting.RED), false);
            return;
        }
        boolean firstVisit = !state.players.containsKey(player.getUuid());
        PlayerProfile profile = state.profile(player.getUuid());
        if (firstVisit) {
            BlockPos arrival = LocationIndex.pos("arrival");
            player.teleport(server.getOverworld(), arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, -90f, 0f);
            player.setSpawnPoint(server.getOverworld().getRegistryKey(), arrival, -90f, true, false);
            profile.checkpoint = "arrival";
            state.markDirty();
        }
    }
}
