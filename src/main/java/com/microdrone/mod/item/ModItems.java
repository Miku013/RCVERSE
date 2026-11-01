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

    public static final DeferredHolder<Item, DroneControllerItem> DRONE_CONTROLLER =
            ITEMS.register("drone_controller", () -> new DroneControllerItem(new Item.Properties().stacksTo(1)));

    // 充電ステーション (BlockItem)
    public static final DeferredHolder<Item, BlockItem> DRONE_CHARGER_ITEM =
            ITEMS.register("drone_charger", () -> new BlockItem(ModBlocks.DRONE_CHARGER.get(), new Item.Properties()));

    // 確認用ピボットテストブロック (BlockItem)
    public static final DeferredHolder<Item, BlockItem> TEST_PIVOT_ITEM =
            ITEMS.register("test_pivot", () -> new BlockItem(ModBlocks.TEST_PIVOT.get(), new Item.Properties()));

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
                    new Item.Properties().stacksTo(1), 3, 0.70, 0.09, 0.45, 0xFFFFB300, "High-Speed"));

    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_4 =
            ITEMS.register("motor_tier_4", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 4, 0.95, 0.12, 0.60, 0xFFE53935, "Racing"));

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
