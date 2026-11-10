package com.microdrone.mod.block.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MicroDroneMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneChargerBlockEntity>> DRONE_CHARGER_BE =
            BLOCK_ENTITIES.register("drone_charger",
                    () -> BlockEntityType.Builder.of(DroneChargerBlockEntity::new, ModBlocks.DRONE_CHARGER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestPivotBlockEntity>> TEST_PIVOT_BE =
            BLOCK_ENTITIES.register("test_pivot",
                    () -> BlockEntityType.Builder.of(TestPivotBlockEntity::new, ModBlocks.TEST_PIVOT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity>> MINIATURE_PLACEMENT_BE =
            BLOCK_ENTITIES.register("miniature_placement",
                    () -> BlockEntityType.Builder.of(com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity::new, ModBlocks.MINIATURE_PLACEMENT.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RaceDisplayBlockEntity>> RACE_DISPLAY_BE =
            BLOCK_ENTITIES.register("race_display",
                    () -> BlockEntityType.Builder.of(RaceDisplayBlockEntity::new, ModBlocks.RACE_DISPLAY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TracksideCameraBlockEntity>> TRACKSIDE_CAMERA_BE =
            BLOCK_ENTITIES.register("trackside_camera",
                    () -> BlockEntityType.Builder.of(TracksideCameraBlockEntity::new, ModBlocks.TRACKSIDE_CAMERA.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VerticalLoopBlockEntity>> VERTICAL_LOOP_BE =
            BLOCK_ENTITIES.register("vertical_loop",
                    () -> BlockEntityType.Builder.of(VerticalLoopBlockEntity::new, ModBlocks.VERTICAL_LOOP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModularSlopeBlockEntity>> MODULAR_SLOPE_BE =
            BLOCK_ENTITIES.register("modular_slope",
                    () -> BlockEntityType.Builder.of(ModularSlopeBlockEntity::new, ModBlocks.MODULAR_SLOPE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.microdrone.mod.block.road.RoadStartBlockEntity>> ROAD_START_BE =
            BLOCK_ENTITIES.register("road_start",
                    () -> BlockEntityType.Builder.of(com.microdrone.mod.block.road.RoadStartBlockEntity::new, ModBlocks.ROAD_START.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.microdrone.mod.block.road.RoadEndBlockEntity>> ROAD_END_BE =
            BLOCK_ENTITIES.register("road_end",
                    () -> BlockEntityType.Builder.of(com.microdrone.mod.block.road.RoadEndBlockEntity::new, ModBlocks.ROAD_END.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

