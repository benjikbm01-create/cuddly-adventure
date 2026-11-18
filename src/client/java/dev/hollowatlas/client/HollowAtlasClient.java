package dev.hollowatlas.client;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.client.dev.AutoShot;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint: screens, HUD, key bindings.
 */
public final class HollowAtlasClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HollowAtlas.LOGGER.info("The Hollow Atlas initializing (client)");
        AutoShot.init();
    }
}
