package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MicroDroneMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE =
            ENTITIES.register("drone", () -> EntityType.Builder.<DroneEntity>of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.25f)
                    .eyeHeight(0.18f)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build("drone"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
