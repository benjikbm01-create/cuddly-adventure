package dev.hollowatlas.npc;

import dev.hollowatlas.HollowAtlas;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class NpcRegistry {
    public static final EntityType<NpcEntity> RESIDENT = Registry.register(Registries.ENTITY_TYPE, HollowAtlas.id("resident"),
            EntityType.Builder.<NpcEntity>create(NpcEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.95f)
                    .eyeHeight(1.62f)
                    .maxTrackingRange(10)
                    .disableSaving()
                    .build("resident"));

    private NpcRegistry() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(RESIDENT, NpcEntity.createAttributes());
    }
}
