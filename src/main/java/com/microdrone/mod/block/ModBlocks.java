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

    // コース小物配置土台ブロック（薄型ベースプレート）
    public static final DeferredBlock<com.microdrone.mod.block.miniature.MiniaturePlacementBlock> MINIATURE_PLACEMENT = BLOCKS.register("miniature_placement",
            () -> new com.microdrone.mod.block.miniature.MiniaturePlacementBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5f)
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(com.microdrone.mod.block.miniature.MiniaturePlacementBlock.LIT) && state.getValue(com.microdrone.mod.block.miniature.MiniaturePlacementBlock.LIT) ? 14 : 0)));

    // レース電光掲示板（マルチブロック対応フルブロック）
    public static final DeferredBlock<RaceDisplayBlock> RACE_DISPLAY = BLOCKS.register("race_display",
            () -> new RaceDisplayBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 3.0f)
                    .noOcclusion()));

    // レーシングスロープ（ハーフブロック式ジャンプ台）
    public static final DeferredBlock<RacingSlopeBlock> RACING_SLOPE = BLOCKS.register("racing_slope",
            () -> new RacingSlopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 2.0f)
                    .noOcclusion()));

    // ブーストジャンプ台（加速＆自発光）
    public static final DeferredBlock<RacingSlopeBlock> BOOST_RAMP = BLOCKS.register("boost_ramp",
            () -> new RacingSlopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.5f, 2.0f)
                    .lightLevel(state -> 12)
                    .noOcclusion(), true));

    // モジュラースロープ坂（つなげるほど長く緩やかになる坂道）
    public static final DeferredBlock<ModularSlopeBlock> MODULAR_SLOPE = BLOCKS.register("modular_slope",
            () -> new ModularSlopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 2.0f)
                    .lightLevel(state -> 5)
                    .noOcclusion()));

    // 峠スロープ（どこまでも階段状に伸ばせる連続峠道）
    public static final DeferredBlock<TougeSlopeBlock> TOUGE_SLOPE = BLOCKS.register("touge_slope",
            () -> new TougeSlopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 2.0f)
                    .lightLevel(state -> 5)
                    .noOcclusion()));

    // 360°縦ループ（Loop-the-Loop）コース
    public static final DeferredBlock<VerticalLoopBlock> VERTICAL_LOOP = BLOCKS.register("vertical_loop",
            () -> new VerticalLoopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 3.0f)
                    .lightLevel(state -> 4)
                    .noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}

