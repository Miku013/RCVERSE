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

    // レース用スタートブロック（スタートシグナル）
    public static final DeferredBlock<StartBlock> START_BLOCK = BLOCKS.register("start_block",
            () -> new StartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 2.0f)
                    .noOcclusion()
                    .lightLevel(state -> state.hasProperty(StartBlock.LIT) && state.getValue(StartBlock.LIT) ? 15 : 0)));

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

    // 360°縦ループ（Loop-the-Loop）コース
    public static final DeferredBlock<VerticalLoopBlock> VERTICAL_LOOP = BLOCKS.register("vertical_loop",
            () -> new VerticalLoopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 3.0f)
                    .lightLevel(state -> 4)
                    .noOcclusion()));

    // 道路・ヘアピンカーブ生成 始点ブロック
    public static final DeferredBlock<com.microdrone.mod.block.road.RoadStartBlock> ROAD_START = BLOCKS.register("road_start",
            () -> new com.microdrone.mod.block.road.RoadStartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.5f, 2.0f)
                    .lightLevel(state -> 3)
                    .noOcclusion()));

    // 道路・ヘアピンカーブ生成 終点ブロック
    public static final DeferredBlock<com.microdrone.mod.block.road.RoadEndBlock> ROAD_END = BLOCKS.register("road_end",
            () -> new com.microdrone.mod.block.road.RoadEndBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.5f, 2.0f)
                    .lightLevel(state -> 3)
                    .noOcclusion()));

    // 垂直打ち上げジャンプパッド（3段階パワー調整可能）
    public static final DeferredBlock<JumpPadBlock> JUMP_PAD = BLOCKS.register("jump_pad",
            () -> new JumpPadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.5f, 2.0f)
                    .lightLevel(state -> state.hasProperty(JumpPadBlock.POWER) ? (state.getValue(JumpPadBlock.POWER) == 1 ? 5 : (state.getValue(JumpPadBlock.POWER) == 2 ? 8 : 12)) : 8)
                    .noOcclusion()));

    // デカール作業台 (3Dリバリーエディター)
    public static final DeferredBlock<DecalTableBlock> DECAL_TABLE = BLOCKS.register("decal_table",
            () -> new DecalTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .noOcclusion()));

    // RC工作台 (RCマシン組み立て ＆ 展示台)
    public static final DeferredBlock<RcWorkbenchBlock> RC_WORKBENCH = BLOCKS.register("rc_workbench",
            () -> new RcWorkbenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .noOcclusion()));

    // RC展示ラック (4段マルチRC展示ラック)
    public static final DeferredBlock<RcRackBlock> RC_RACK = BLOCKS.register("rc_rack",
            () -> new RcRackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 3.0f)
                    .noOcclusion()));

    // RC大型ショウケース (幅3マス×高さ2マス 10スロット3D展示ケース)
    public static final DeferredBlock<RcShowcaseBlock> RC_SHOWCASE = BLOCKS.register("rc_showcase",
            () -> new RcShowcaseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f, 3.0f)
                    .noOcclusion()));

    // RCピット作業台 (テストブロック: 3Dビューポート & 自由回転カスタムステーション)
    public static final DeferredBlock<RcPitStationBlock> RC_PIT_STATION = BLOCKS.register("rc_pit_station",
            () -> new RcPitStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 3.5f)
                    .noOcclusion()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}

