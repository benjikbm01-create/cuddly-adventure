package dev.hollowatlas.combat;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Sling stone thrown by slingers. Breaks on impact; never damages blocks. */
public class SlingStoneEntity extends ThrownItemEntity {
    public SlingStoneEntity(EntityType<? extends SlingStoneEntity> type, World world) { super(type, world); }

    public SlingStoneEntity(World world, LivingEntity owner) { super(CombatRegistry.SLING_STONE, owner, world); }

    @Override
    protected Item getDefaultItem() { return Items.COBBLESTONE; }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (hit.getEntity() == getOwner()) return;
        hit.getEntity().damage(getDamageSources().thrown(this, getOwner()), 4f);
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!getWorld().isClient) {
            ((ServerWorld) getWorld()).spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, getStack()), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
            discard();
        }
    }
}
