package dev.hollowatlas.client.dev;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.world.LocationIndex;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DEVELOPMENT-ONLY automated client harness. Active only when the JVM is started with
 * {@code -Dhollowatlas.autoshot=<spec>}; it is inert in normal play.
 * Spec: {@code name:anchor:dx:dy:dz:yaw:pitch[:hidehud];...} — teleports the camera relative to an anchor
 * at noon in clear weather, waits for chunks to render and saves screenshots/<name>.png. Then quits.
 * Optional {@code -Dhollowatlas.autoshot.actions} hooks let other dev tests run client steps.
 */
public final class AutoShot {
    private record Shot(String name, String anchor, double dx, double dy, double dz, float yaw, float pitch, boolean hideHud, long time) {}

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final List<Consumer<MinecraftClient>> HOOKS = new ArrayList<>();
    private static int index = -1;
    private static int wait = 0;
    private static int warmup = 200;
    private static boolean primed = false;
    private static boolean pendingSave = false;

    private AutoShot() {}

    /** Other dev harnesses can run code at the moment shot N is taken (by name). */
    public static void hook(Consumer<MinecraftClient> c) { HOOKS.add(c); }

    public static void init() {
        String spec = System.getProperty("hollowatlas.autoshot");
        if (spec == null || spec.isBlank()) return;
        for (String part : spec.split(";")) {
            String[] f = part.trim().split(":");
            if (f.length < 7) continue;
            SHOTS.add(new Shot(f[0], f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3]), Double.parseDouble(f[4]),
                    Float.parseFloat(f[5]), Float.parseFloat(f[6]), f.length > 7 && f[7].equals("hidehud"),
                    f.length > 8 ? Long.parseLong(f[8]) : 6000L));
        }
        HollowAtlas.LOGGER.info("[autoshot] {} shots queued", SHOTS.size());
        ClientTickEvents.END_CLIENT_TICK.register(AutoShot::tick);
    }

    public static boolean active() { return !SHOTS.isEmpty(); }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        IntegratedServer server = client.getServer();
        if (server == null) return;
        if (warmup > 0) { warmup--; return; }
        if (wait > 0) { wait--; return; }
        if (pendingSave && index >= 0 && index < SHOTS.size()) {
            pendingSave = false;
            Shot s = SHOTS.get(index);
            client.options.hudHidden = s.hideHud();
            ScreenshotRecorder.saveScreenshot(client.runDirectory, s.name() + ".png", client.getFramebuffer(),
                    msg -> HollowAtlas.LOGGER.info("[autoshot] saved {}", s.name()));
            for (Consumer<MinecraftClient> h : HOOKS) h.accept(client);
        }
        if (!primed && index + 1 < SHOTS.size()) {
            // Phase 1: park out of sight (underground) near the next shot so the area and its residents load
            // without "popping in" in view, exactly as they would for a player walking up.
            Shot next = SHOTS.get(index + 1);
            server.execute(() -> {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayerList().get(0);
                sp.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                sp.getAbilities().flying = true;
                sp.sendAbilitiesUpdate();
                server.getOverworld().setTimeOfDay(next.time());
                server.getOverworld().setWeather(12000, 0, false, false);
                BlockPos base = LocationIndex.pos(next.anchor());
                sp.teleport(server.getOverworld(), base.getX() + 0.5, base.getY() - 14, base.getZ() + 0.5, 0, 90);
            });
            primed = true;
            wait = 90;
            return;
        }
        primed = false;
        index++;
        if (index >= SHOTS.size()) {
            HollowAtlas.LOGGER.info("[autoshot] done");
            client.scheduleStop();
            SHOTS.clear();
            return;
        }
        Shot s = SHOTS.get(index);
        client.options.hudHidden = s.hideHud();
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayerList().get(0);
            BlockPos base = LocationIndex.pos(s.anchor());
            server.getOverworld().setTimeOfDay(s.time());
            sp.teleport(server.getOverworld(), base.getX() + 0.5 + s.dx(), base.getY() + s.dy(), base.getZ() + 0.5 + s.dz(), s.yaw(), s.pitch());
        });
        wait = 110;
        pendingSave = true;
    }
}
