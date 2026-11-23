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
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("drone"));

    public static final DeferredHolder<EntityType<?>, EntityType<RcCarEntity>> RC_CAR =
            ENTITIES.register("rc_car", () -> EntityType.Builder.<RcCarEntity>of(RcCarEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.35f)
                    .eyeHeight(0.3f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("rc_car"));

    public static final DeferredHolder<EntityType<?>, EntityType<RcTankEntity>> RC_TANK =
            ENTITIES.register("rc_tank", () -> EntityType.Builder.<RcTankEntity>of(RcTankEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.45f)
                    .eyeHeight(0.35f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("rc_tank"));

    public static final DeferredHolder<EntityType<?>, EntityType<RcPlaneEntity>> RC_PLANE =
            ENTITIES.register("rc_plane", () -> EntityType.Builder.<RcPlaneEntity>of(RcPlaneEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.45f)
                    .eyeHeight(0.35f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("rc_plane"));

    public static final DeferredHolder<EntityType<?>, EntityType<RcBoatEntity>> RC_BOAT =
            ENTITIES.register("rc_boat", () -> EntityType.Builder.<RcBoatEntity>of(RcBoatEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.4f)
                    .eyeHeight(0.3f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("rc_boat"));

    public static final DeferredHolder<EntityType<?>, EntityType<RcSubmarineEntity>> RC_SUBMARINE =
            ENTITIES.register("rc_submarine", () -> EntityType.Builder.<RcSubmarineEntity>of(RcSubmarineEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.4f)
                    .eyeHeight(0.3f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("rc_submarine"));

    public static final DeferredHolder<EntityType<?>, EntityType<RcPilotDummyEntity>> RC_PILOT_DUMMY =
            ENTITIES.register("rc_pilot_dummy", () -> EntityType.Builder.<RcPilotDummyEntity>of(RcPilotDummyEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("rc_pilot_dummy"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }

    public static void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(RC_PILOT_DUMMY.get(), RcPilotDummyEntity.createAttributes().build());
    }
}
