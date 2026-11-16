package com.microdrone.mod.item;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, MicroDroneMod.MODID);

    public static final DeferredHolder<Item, DroneSpawnItem> DRONE_SPAWN_ITEM =
            ITEMS.register("drone_item", () -> new DroneSpawnItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, RcCarItem> RC_CAR_ITEM =
            ITEMS.register("rc_car_item", () -> new RcCarItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, RcTankItem> RC_TANK_ITEM =
            ITEMS.register("rc_tank_item", () -> new RcTankItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, RcPlaneItem> RC_PLANE_ITEM =
            ITEMS.register("rc_plane_item", () -> new RcPlaneItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, RcBoatItem> RC_BOAT_ITEM =
            ITEMS.register("rc_boat_item", () -> new RcBoatItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, RcSubmarineItem> RC_SUBMARINE_ITEM =
            ITEMS.register("rc_submarine_item", () -> new RcSubmarineItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, DroneControllerItem> DRONE_CONTROLLER =
            ITEMS.register("drone_controller", () -> new DroneControllerItem(new Item.Properties().stacksTo(1)));

    // 充電ステーション (BlockItem)
    public static final DeferredHolder<Item, BlockItem> DRONE_CHARGER_ITEM =
            ITEMS.register("drone_charger", () -> new BlockItem(ModBlocks.DRONE_CHARGER.get(), new Item.Properties()));

    // 確認用ピボットテストブロック (BlockItem)
    public static final DeferredHolder<Item, BlockItem> TEST_PIVOT_ITEM =
            ITEMS.register("test_pivot", () -> new BlockItem(ModBlocks.TEST_PIVOT.get(), new Item.Properties()));

    // レース電光掲示板 (BlockItem)
    public static final DeferredHolder<Item, BlockItem> RACE_DISPLAY_ITEM =
            ITEMS.register("race_display", () -> new BlockItem(ModBlocks.RACE_DISPLAY.get(), new Item.Properties()));

    // スタートブロック (BlockItem)
    public static final DeferredHolder<Item, BlockItem> START_BLOCK_ITEM =
            ITEMS.register("start_block", () -> new BlockItem(ModBlocks.START_BLOCK.get(), new Item.Properties()));

    // レーシングスロープ (BlockItem)
    public static final DeferredHolder<Item, BlockItem> RACING_SLOPE_ITEM =
            ITEMS.register("racing_slope", () -> new BlockItem(ModBlocks.RACING_SLOPE.get(), new Item.Properties()));

    // ブーストジャンプ台 (BlockItem)
    public static final DeferredHolder<Item, BlockItem> BOOST_RAMP_ITEM =
            ITEMS.register("boost_ramp", () -> new BlockItem(ModBlocks.BOOST_RAMP.get(), new Item.Properties()));

    // モジュラースロープ坂 (BlockItem)
    public static final DeferredHolder<Item, BlockItem> MODULAR_SLOPE_ITEM =
            ITEMS.register("modular_slope", () -> new BlockItem(ModBlocks.MODULAR_SLOPE.get(), new Item.Properties()));

    // 道路・ヘアピンカーブ生成 始点マーカー (BlockItem)
    public static final DeferredHolder<Item, BlockItem> ROAD_START_ITEM =
            ITEMS.register("road_start", () -> new BlockItem(ModBlocks.ROAD_START.get(), new Item.Properties()));

    // 道路・ヘアピンカーブ生成 終点マーカー (BlockItem)
    public static final DeferredHolder<Item, BlockItem> ROAD_END_ITEM =
            ITEMS.register("road_end", () -> new BlockItem(ModBlocks.ROAD_END.get(), new Item.Properties()));

    // 360°縦ループコース (BlockItem)
    public static final DeferredHolder<Item, BlockItem> VERTICAL_LOOP_ITEM =
            ITEMS.register("vertical_loop", () -> new BlockItem(ModBlocks.VERTICAL_LOOP.get(), new Item.Properties()));

    // 垂直打ち上げジャンプパッド (BlockItem)
    public static final DeferredHolder<Item, BlockItem> JUMP_PAD_ITEM =
            ITEMS.register("jump_pad", () -> new BlockItem(ModBlocks.JUMP_PAD.get(), new Item.Properties()));

    // デカール作業台 (BlockItem)
    public static final DeferredHolder<Item, BlockItem> DECAL_TABLE_ITEM =
            ITEMS.register("decal_table", () -> new BlockItem(ModBlocks.DECAL_TABLE.get(), new Item.Properties()));

    // RC工作台 (BlockItem)
    public static final DeferredHolder<Item, BlockItem> RC_WORKBENCH_ITEM =
            ITEMS.register("rc_workbench", () -> new BlockItem(ModBlocks.RC_WORKBENCH.get(), new Item.Properties()));

    // RC展示ラック (BlockItem)
    public static final DeferredHolder<Item, BlockItem> RC_RACK_ITEM =
            ITEMS.register("rc_rack", () -> new BlockItem(ModBlocks.RC_RACK.get(), new Item.Properties()));

    // RC大型ショウケース (BlockItem)
    public static final DeferredHolder<Item, BlockItem> RC_SHOWCASE_ITEM =
            ITEMS.register("rc_showcase", () -> new BlockItem(ModBlocks.RC_SHOWCASE.get(), new Item.Properties()));

    // === コース小物・設備システム (Phase 1: カラーコーン ＆ Phase 2: 縁石・ガードレール・タイヤバリア) ===
    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.TrafficConeItem> TRAFFIC_CONE =
            ITEMS.register("traffic_cone", () -> new com.microdrone.mod.block.miniature.TrafficConeItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.MiniatureItem> RACING_CURB =
            ITEMS.register("racing_curb", () -> new com.microdrone.mod.block.miniature.MiniatureItem(new Item.Properties().stacksTo(64), "racing_curb"));

    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.MiniatureItem> CURB_STONE =
            ITEMS.register("curb_stone", () -> new com.microdrone.mod.block.miniature.MiniatureItem(new Item.Properties().stacksTo(64), "curb_stone"));

    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.MiniatureItem> GUARDRAIL =
            ITEMS.register("guardrail", () -> new com.microdrone.mod.block.miniature.MiniatureItem(new Item.Properties().stacksTo(64), "guardrail"));

    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.MiniatureItem> TIRE_BARRIER =
            ITEMS.register("tire_barrier", () -> new com.microdrone.mod.block.miniature.MiniatureItem(new Item.Properties().stacksTo(64), "tire_barrier"));

    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.MiniatureItem> GOAL_GATE =
            ITEMS.register("goal_gate", () -> new com.microdrone.mod.block.miniature.MiniatureItem(new Item.Properties().stacksTo(64), "goal_gate"));

    public static final DeferredHolder<Item, com.microdrone.mod.block.miniature.MiniatureItem> CHECKPOINT_PLATE =
            ITEMS.register("checkpoint_plate", () -> new com.microdrone.mod.block.miniature.MiniatureItem(new Item.Properties().stacksTo(64), "checkpoint_plate"));

    public static Item getItemForMiniature(String id) {
        if ("checkpoint_plate".equals(id)) return CHECKPOINT_PLATE.get();
        if ("goal_gate".equals(id)) return GOAL_GATE.get();
        if ("racing_curb".equals(id)) return RACING_CURB.get();
        if ("curb_stone".equals(id)) return CURB_STONE.get();
        if ("guardrail".equals(id)) return GUARDRAIL.get();
        if ("tire_barrier".equals(id)) return TIRE_BARRIER.get();
        return TRAFFIC_CONE.get();
    }

    public static final DeferredHolder<Item, BlockItem> MINIATURE_PLACEMENT_ITEM =
            ITEMS.register("miniature_placement", () -> new BlockItem(ModBlocks.MINIATURE_PLACEMENT.get(), new Item.Properties()));

    // === 4段階の航続距離バッテリーアイテム (Step 5) ===
    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_1 =
            ITEMS.register("battery_tier_1", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 3000));

    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_2 =
            ITEMS.register("battery_tier_2", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 4000));

    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_3 =
            ITEMS.register("battery_tier_3", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 5000));

    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_4 =
            ITEMS.register("battery_tier_4", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 6000));

    // === 4段階のブラシレスモーターアイテム ===
    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_1 =
            ITEMS.register("motor_tier_1", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 1, 0.35, 0.04, 0.25, 0xFF333333, "Standard"));

    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_2 =
            ITEMS.register("motor_tier_2", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 2, 0.50, 0.065, 0.35, 0xFF1E88E5, "Sport"));

    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_3 =
            ITEMS.register("motor_tier_3", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 3, 0.60, 0.075, 0.40, 0xFFFFB300, "High-Speed"));

    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_4 =
            ITEMS.register("motor_tier_4", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 4, 0.72, 0.095, 0.48, 0xFFE53935, "Racing"));

    // === ドローン本体製作用・中間素材パーツ ===
    public static final DeferredHolder<Item, DroneComponentItem> PROPELLER =
            ITEMS.register("propeller", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.propeller_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> DRONE_FRAME =
            ITEMS.register("drone_frame", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.drone_frame_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> ESC_BOARD =
            ITEMS.register("esc_board", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.esc_board_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> DRONE_SCREWS =
            ITEMS.register("drone_screws", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.drone_screws_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> CONNECTION_CABLE =
            ITEMS.register("connection_cable", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.connection_cable_desc"));

    // === プロポ（コントローラー）製作用・中間素材パーツ ===
    // 1. 無線アンテナ
    public static final DeferredHolder<Item, DroneComponentItem> RADIO_ANTENNA =
            ITEMS.register("radio_antenna", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.radio_antenna_desc"));

    // 2. コントローラーケース
    public static final DeferredHolder<Item, DroneComponentItem> CONTROLLER_CASE =
            ITEMS.register("controller_case", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.controller_case_desc"));

    // 3. 操作スティック
    public static final DeferredHolder<Item, DroneComponentItem> GIMBAL_STICK =
            ITEMS.register("gimbal_stick", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.gimbal_stick_desc"));

    // 4. プロポ基板
    public static final DeferredHolder<Item, DroneComponentItem> CONTROLLER_BOARD =
            ITEMS.register("controller_board", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.controller_board_desc"));

    // 5. ばね (リターンスプリング)
    public static final DeferredHolder<Item, DroneComponentItem> TINY_SPRING =
            ITEMS.register("tiny_spring", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.tiny_spring_desc"));

    // 6. 液晶部品 (LCDスクリーン)
    public static final DeferredHolder<Item, DroneComponentItem> LCD_SCREEN =
            ITEMS.register("lcd_screen", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.lcd_screen_desc"));

    // === モーター製作用・中間素材パーツ ===
    // 1. モーターケース (Tier 1〜4)
    public static final DeferredHolder<Item, DroneComponentItem> MOTOR_CAN_TIER_1 =
            ITEMS.register("motor_can_tier_1", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.motor_can_tier_1_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> MOTOR_CAN_TIER_2 =
            ITEMS.register("motor_can_tier_2", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.motor_can_tier_2_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> MOTOR_CAN_TIER_3 =
            ITEMS.register("motor_can_tier_3", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.motor_can_tier_3_desc"));

    public static final DeferredHolder<Item, DroneComponentItem> MOTOR_CAN_TIER_4 =
            ITEMS.register("motor_can_tier_4", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(16), "tooltip.microdrone.motor_can_tier_4_desc"));

    // 2. 電線 (エナメル銅線)
    public static final DeferredHolder<Item, DroneComponentItem> MAGNET_WIRE =
            ITEMS.register("magnet_wire", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.magnet_wire_desc"));

    // 3. 軸 (スチールシャフト)
    public static final DeferredHolder<Item, DroneComponentItem> MOTOR_SHAFT =
            ITEMS.register("motor_shaft", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.motor_shaft_desc"));

    // 4. 磁石 (ネオジム磁石)
    public static final DeferredHolder<Item, DroneComponentItem> NEODYMIUM_MAGNET =
            ITEMS.register("neodymium_magnet", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.neodymium_magnet_desc"));

    // 5. ステート端子 (ステーター端子)
    public static final DeferredHolder<Item, DroneComponentItem> MOTOR_STATOR_TERMINAL =
            ITEMS.register("motor_stator_terminal", () -> new DroneComponentItem(
                    new Item.Properties().stacksTo(64), "tooltip.microdrone.motor_stator_terminal_desc"));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
