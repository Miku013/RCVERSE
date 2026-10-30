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

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

