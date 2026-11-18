package dev.hollowatlas.client;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.client.dev.AutoShot;
import dev.hollowatlas.client.render.NpcRenderer;
import dev.hollowatlas.npc.NpcRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client-only entrypoint: screens, HUD, key bindings.
 */
public final class HollowAtlasClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HollowAtlas.LOGGER.info("The Hollow Atlas initializing (client)");
        EntityRendererRegistry.register(NpcRegistry.RESIDENT, NpcRenderer::new);
        AutoShot.init();
    }
}
