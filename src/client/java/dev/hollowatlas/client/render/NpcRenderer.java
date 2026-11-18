package dev.hollowatlas.client.render;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.npc.NpcEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Residents use the player model with their own generated skin. */
public class NpcRenderer extends BipedEntityRenderer<NpcEntity, PlayerEntityModel<NpcEntity>> {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    public NpcRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(NpcEntity entity) {
        return CACHE.computeIfAbsent(entity.skin(), s -> HollowAtlas.id("textures/entity/npc/" + s + ".png"));
    }
}
