package com.microdrone.mod.block;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MicroDroneMod.MODID);

    // 専用充電ステーションブロック
    public static final DeferredBlock<DroneChargerBlock> DRONE_CHARGER = BLOCKS.register("drone_charger",
            () -> new DroneChargerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.0f, 3.0f)
                    .noOcclusion()));

    // 確認用ピボットテストブロック
    public static final DeferredBlock<TestPivotBlock> TEST_PIVOT = BLOCKS.register("test_pivot",
            () -> new TestPivotBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(1.5f)
                    .noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}

