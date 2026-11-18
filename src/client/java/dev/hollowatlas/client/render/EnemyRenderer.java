package dev.hollowatlas.client.render;

import dev.hollowatlas.HollowAtlas;
import dev.hollowatlas.combat.EnemyEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/** Brigands use the player model; telegraphs are shown by raising the arms (wind-up/aim) or crossing them (guard). */
public class EnemyRenderer extends BipedEntityRenderer<EnemyEntity, PlayerEntityModel<EnemyEntity>> {
    public EnemyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx), 0.5f);
    }

    @Override
    public Identifier getTexture(EnemyEntity e) {
        return HollowAtlas.id("textures/entity/npc/" + e.kind() + ".png");
    }

    static class Model extends PlayerEntityModel<EnemyEntity> {
        Model(EntityRendererFactory.Context ctx) { super(ctx.getPart(EntityModelLayers.PLAYER), false); }

        @Override
        public void setAngles(EnemyEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
            switch (e.poseState()) {
                case EnemyEntity.POSE_WINDUP -> {
                    rightArm.pitch = -2.7f; leftArm.pitch = -2.5f;
                    rightArm.roll = 0.1f; leftArm.roll = -0.1f;
                }
                case EnemyEntity.POSE_AIM -> { rightArm.pitch = -1.6f; rightArm.yaw = -0.3f; }
                case EnemyEntity.POSE_BLOCK -> {
                    rightArm.pitch = -1.2f; leftArm.pitch = -1.2f;
                    rightArm.yaw = -0.6f; leftArm.yaw = 0.6f;
                }
                case EnemyEntity.POSE_STAGGER -> { head.pitch = 0.5f; body.pitch = 0.25f; rightArm.pitch = 0.4f; leftArm.pitch = 0.4f; }
                default -> { }
            }
            rightSleeve.copyTransform(rightArm);
            leftSleeve.copyTransform(leftArm);
            hat.copyTransform(head);
            jacket.copyTransform(body);
        }
    }
}
