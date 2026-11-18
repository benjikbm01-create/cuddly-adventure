package dev.hollowatlas.combat;

import dev.hollowatlas.HollowAtlas;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class CombatRegistry {
    public static final EntityType<EnemyEntity> ENEMY = Registry.register(Registries.ENTITY_TYPE, HollowAtlas.id("brigand"),
            EntityType.Builder.<EnemyEntity>create(EnemyEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f).eyeHeight(1.62f).maxTrackingRange(10).disableSaving().build("brigand"));

    public static final EntityType<SlingStoneEntity> SLING_STONE = Registry.register(Registries.ENTITY_TYPE, HollowAtlas.id("sling_stone"),
            EntityType.Builder.<SlingStoneEntity>create(SlingStoneEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f).maxTrackingRange(4).trackingTickInterval(10).disableSaving().build("sling_stone"));

    private CombatRegistry() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(ENEMY, EnemyEntity.createAttributes());
    }
}
