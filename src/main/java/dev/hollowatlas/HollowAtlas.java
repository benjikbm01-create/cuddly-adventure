package dev.hollowatlas;

import dev.hollowatlas.ability.AbilityService;
import dev.hollowatlas.combat.CombatRegistry;
import dev.hollowatlas.combat.CombatService;
import dev.hollowatlas.combat.EncounterService;
import dev.hollowatlas.command.AtlasCommands;
import dev.hollowatlas.dialogue.DialogueService;
import dev.hollowatlas.inventory.CraftingService;
import dev.hollowatlas.inventory.HudSync;
import dev.hollowatlas.inventory.ModItems;
import dev.hollowatlas.inventory.RewardService;
import dev.hollowatlas.inventory.ShopService;
import dev.hollowatlas.investigation.CaseService;
import dev.hollowatlas.investigation.Interactions;
import dev.hollowatlas.net.Net;
import dev.hollowatlas.quest.QuestEngine;
import dev.hollowatlas.world.WorldChanges;
import dev.hollowatlas.content.ContentManager;
import dev.hollowatlas.npc.AmbientChatter;
import dev.hollowatlas.npc.NpcDirector;
import dev.hollowatlas.npc.NpcRegistry;
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
        ContentManager.init();
        Net.init();
        ModItems.init();
        NpcRegistry.init();
        CombatRegistry.init();
        NpcDirector.init();
        AmbientChatter.init();
        QuestEngine.init();
        DialogueService.init();
        CaseService.init();
        CombatService.init();
        EncounterService.init();
        AbilityService.init();
        ShopService.init();
        CraftingService.init();
        dev.hollowatlas.inventory.InventoryActions.init();
        WorldChanges.init();
        dev.hollowatlas.world.SiteService.init();
        Interactions.init();          // must register before protection so designated interactions win
        ProtectionService.register();
        dev.hollowatlas.command.DevCommands.init();
        dev.hollowatlas.devtest.DevTestService.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> AtlasCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(StateBackup::backupIfMigrating);
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> NpcDirector.onServerStopping());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            RegionBuilder.tick(server);
            if (RegionBuilder.isRunning() && server.getTicks() % 20 == 0 && dev.hollowatlas.state.WorldMode.showcase(server)) {
                Text bar = Text.literal("Building showcase map: " + RegionBuilder.percent() + "%  (you can fly around meanwhile)").formatted(Formatting.YELLOW);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) p.sendMessage(bar, true);
            }
            BoundaryService.tick(server);
            if (!RegionBuilder.isRunning() && GameState.get(server).flag(RegionBuilder.BUILT_FLAG)) {
                NpcDirector.tick(server);
                AmbientChatter.tick(server);
                QuestEngine.tick(server);
                EncounterService.tick(server);
                Interactions.tick(server);
                dev.hollowatlas.world.SiteService.tick(server);
            }
            CombatService.tick(server);
            AbilityService.tick(server);
            RewardService.tick(server);
            HudSync.tick(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer(), server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DialogueService.cancel(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ProtectionService.enforceMode(newPlayer));
    }

    private void onServerStarted(MinecraftServer server) {
        boolean newShowcase = Boolean.getBoolean("hollowatlas.autobuild") && "showcase".equals(System.getProperty("hollowatlas.mode"))
                || Boolean.getBoolean("hollowatlas.prepare") && "showcase".equals(System.getProperty("hollowatlas.mode"));
        if (newShowcase) GameState.get(server).setFlag(dev.hollowatlas.state.WorldMode.SHOWCASE_FLAG, true);
        LocationIndex.configure(dev.hollowatlas.state.WorldMode.showcase(server));
        if (Boolean.getBoolean("hollowatlas.prepare")) {
            // packaging: an empty showcase world that builds itself the first time it is opened
            LOGGER.info("Prepared an empty showcase world; stopping.");
            server.saveAll(false, true, true);
            server.stop(false);
            return;
        }
        LocationIndex.model();
        WorldRules.apply(server);
        if (GameState.get(server).flag(RegionBuilder.BUILT_FLAG)) {
            WorldChanges.apply(server);
            // one-time repair for worlds generated before panes/fences were connected
            if (!GameState.get(server).flag(RegionBuilder.CONNECT_FIX_FLAG)) RegionBuilder.startConnectFix(server.getOverworld(), LocationIndex.model());
        }
        GameState state = GameState.get(server);
        boolean stopAfter = Boolean.getBoolean("hollowatlas.autobuild.stop");
        Runnable afterBuild = () -> {
            renderMap(server);
            if (stopAfter) {
                LOGGER.info("Autobuild finished; stopping server.");
                server.stop(false);
            }
        };
        if (state.flag(RegionBuilder.BUILT_FLAG) && Boolean.getBoolean("hollowatlas.rendermap")
                && LocationIndex.modelsFor(server).stream().allMatch(m -> state.flag(RegionBuilder.builtFlag(m)))) {
            afterBuild.run();
            return;
        }
        if (!state.flag(RegionBuilder.BUILT_FLAG)) {
            if (Boolean.getBoolean("hollowatlas.autobuild") && "devtest".equals(System.getProperty("hollowatlas.mode"))) {
                state.setFlag(dev.hollowatlas.state.WorldMode.DEVTEST_FLAG, true);   // a brand-new developer testing world
            }
            if (Boolean.getBoolean("hollowatlas.autobuild")) {
                RegionBuilder.buildMissing(server, afterBuild);
            } else if (dev.hollowatlas.state.WorldMode.showcase(server)) {
                LOGGER.info("Showcase world opened for the first time: building the map in place.");
                RegionBuilder.buildMissing(server, () -> {
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
                        p.sendMessage(Text.literal("The showcase map is finished. Explore!").formatted(Formatting.GREEN), false);
                });
            } else {
                LOGGER.warn("This world does not contain the Hollow Atlas region. Use the bundled world, "
                        + "or run '/atlas build' as an operator on a superflat world.");
            }
        } else {
            BoundaryService.applyWorldBorder(server);
            // an existing save from an older version: generate any regions added since, in place
            boolean missing = LocationIndex.modelsFor(server).stream().anyMatch(m -> !state.flag(RegionBuilder.builtFlag(m)));
            if (missing) {
                LOGGER.info("Generating regions added since this world was created...");
                RegionBuilder.buildMissing(server, () -> {
                    for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
                        p.sendMessage(Text.translatable("message.hollowatlas.surveyed").formatted(Formatting.GREEN), false);
                    if (Boolean.getBoolean("hollowatlas.autobuild")) afterBuild.run();
                });
            } else if (Boolean.getBoolean("hollowatlas.autobuild")) {
                afterBuild.run();
            }
        }
    }

    public static void renderMap(MinecraftServer server) {
        try {
            for (dev.hollowatlas.world.RegionModel m : LocationIndex.modelsFor(server)) {
                if (!GameState.get(server).flag(RegionBuilder.builtFlag(m))) continue;
                MapRenderer.render(server.getOverworld(), m,
                        server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("hollowatlas").resolve("atlas_" + m.layout.id + ".png"), 1);
            }
        } catch (Exception e) {
            LOGGER.error("Map render failed", e);
        }
    }

    private void onJoin(ServerPlayerEntity player, MinecraftServer server) {
        ProtectionService.enforceMode(player);
        GameState state = GameState.get(server);
        if (dev.hollowatlas.state.WorldMode.showcase(server)) {
            boolean first = !state.players.containsKey(player.getUuid());
            state.profile(player.getUuid());
            state.markDirty();
            if (first) {
                BlockPos a = LocationIndex.has("sc_arrival") ? LocationIndex.pos("sc_arrival") : player.getBlockPos();
                player.teleport(server.getOverworld(), a.getX() + 0.5, a.getY() + 30, a.getZ() + 0.5, 180f, 20f);
                player.setSpawnPoint(server.getOverworld().getRegistryKey(), a, 180f, true, false);
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
            }
            player.sendMessage(Text.literal("Hollow Atlas showcase map: terrain and buildings only. You're in creative mode: fly with double-tap Space.").formatted(Formatting.AQUA), false);
            if (!state.flag(RegionBuilder.BUILT_FLAG))
                player.sendMessage(Text.literal("The map is being built around you (a few minutes). Progress shows above your hotbar.").formatted(Formatting.YELLOW), false);
            HudSync.send(player);
            return;
        }
        if (!state.flag(RegionBuilder.BUILT_FLAG)) {
            player.sendMessage(Text.translatable("message.hollowatlas.no_region").formatted(Formatting.RED), false);
            return;
        }
        boolean firstVisit = !state.players.containsKey(player.getUuid());
        PlayerProfile profile = state.profile(player.getUuid());
        if (firstVisit && dev.hollowatlas.state.WorldMode.devtest(server)) {
            dev.hollowatlas.devtest.DevTestService.onFirstJoin(player);
        } else if (firstVisit) {
            BlockPos arrival = LocationIndex.pos("arrival");
            player.teleport(server.getOverworld(), arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, -90f, 0f);
            player.setSpawnPoint(server.getOverworld().getRegistryKey(), arrival, -90f, true, false);
            profile.checkpoint = "arrival";
            profile.coins = 12;
            state.markDirty();
            // a traveler arrives on the west road in the rain
            server.getOverworld().setWeather(0, 3600, true, false);
            RewardService.give(player, "hollowatlas:traveler_shortsword", 1);
            RewardService.give(player, "minecraft:bread", 3);
            QuestEngine.start(player, "m01");
        }
        QuestEngine.sync(player);
        HudSync.send(player);
    }
}
