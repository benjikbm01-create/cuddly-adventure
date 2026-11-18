package dev.hollowatlas.client;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.client.dev.AutoShot;
import dev.hollowatlas.client.render.EnemyRenderer;
import dev.hollowatlas.client.render.NpcRenderer;
import dev.hollowatlas.client.ui.HudRenderer;
import dev.hollowatlas.combat.CombatRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
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
        ClientConfig.load();
        ClientState.init();
        Keys.init();
        HudRenderer.init();
        EntityRendererRegistry.register(NpcRegistry.RESIDENT, NpcRenderer::new);
        EntityRendererRegistry.register(CombatRegistry.ENEMY, EnemyRenderer::new);
        EntityRendererRegistry.register(CombatRegistry.SLING_STONE, FlyingItemEntityRenderer::new);
        AutoShot.init();
        dev.hollowatlas.client.dev.AutoTest.init();
    }
}
