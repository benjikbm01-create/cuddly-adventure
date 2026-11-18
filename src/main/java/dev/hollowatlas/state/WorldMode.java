package dev.hollowatlas.state;

import dev.hollowatlas.content.ContentManager;
import net.minecraft.server.MinecraftServer;

/**
 * Which kind of world this is. A STORY world (the real game) loads only story content; a DEVTEST
 * world (the separate developer testing world) loads only noncanonical test content and enables the
 * developer menu. The mode is a world flag written when the world is generated, so it travels with
 * the save and can't be toggled from inside a story world.
 */
public final class WorldMode {
    public static final String DEVTEST_FLAG = "hollowatlas:mode_devtest";
    public static final String DEVTEST = "devtest", STORY = "story";

    private WorldMode() {}

    public static boolean devtest(MinecraftServer server) {
        return server != null && GameState.get(server).flag(DEVTEST_FLAG);
    }

    public static String of(MinecraftServer server) { return devtest(server) ? DEVTEST : STORY; }

    /** Content object is usable in this world (test content only in test worlds, story content only in story worlds). */
    public static boolean allowed(MinecraftServer server, Object content) {
        return ContentManager.isDevtest(content) == devtest(server);
    }

    public static boolean allowedMode(MinecraftServer server, String mode) {
        return (DEVTEST.equals(mode)) == devtest(server);
    }
}
