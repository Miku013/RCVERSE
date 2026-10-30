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

    public static final DeferredHolder<Item, DroneControllerItem> DRONE_CONTROLLER =
            ITEMS.register("drone_controller", () -> new DroneControllerItem(new Item.Properties().stacksTo(1)));

    // 充電ステーション (BlockItem)
    public static final DeferredHolder<Item, BlockItem> DRONE_CHARGER_ITEM =
            ITEMS.register("drone_charger", () -> new BlockItem(ModBlocks.DRONE_CHARGER.get(), new Item.Properties()));

    // 確認用ピボットテストブロック (BlockItem)
    public static final DeferredHolder<Item, BlockItem> TEST_PIVOT_ITEM =
            ITEMS.register("test_pivot", () -> new BlockItem(ModBlocks.TEST_PIVOT.get(), new Item.Properties()));

    // === 4段階の航続距離バッテリーアイテム (Step 5) ===
    // Tier 1: 3,000 ブロック
    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_1 =
            ITEMS.register("battery_tier_1", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 3000));

    // Tier 2: 4,000 ブロック
    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_2 =
            ITEMS.register("battery_tier_2", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 4000));

    // Tier 3: 5,000 ブロック
    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_3 =
            ITEMS.register("battery_tier_3", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 5000));

    // Tier 4: 6,000 ブロック
    public static final DeferredHolder<Item, DroneBatteryItem> BATTERY_TIER_4 =
            ITEMS.register("battery_tier_4", () -> new DroneBatteryItem(new Item.Properties().stacksTo(1), 6000));

    // === 4段階のブラシレスモーターアイテム ===
    // Tier 1: スタンダードモーター (7 m/s, ガンメタル)
    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_1 =
            ITEMS.register("motor_tier_1", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 1, 0.35, 0.04, 0.25, 0xFF333333, "Standard"));

    // Tier 2: スポーツモーター (10 m/s, メタリックブルー)
    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_2 =
            ITEMS.register("motor_tier_2", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 2, 0.50, 0.065, 0.35, 0xFF1E88E5, "Sport"));

    // Tier 3: ハイスピードモーター (14 m/s, アルマイトゴールド)
    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_3 =
            ITEMS.register("motor_tier_3", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 3, 0.70, 0.09, 0.45, 0xFFFFB300, "High-Speed"));

    // Tier 4: レーシングモーター (19 m/s, レーシングレッド)
    public static final DeferredHolder<Item, DroneMotorItem> MOTOR_TIER_4 =
            ITEMS.register("motor_tier_4", () -> new DroneMotorItem(
                    new Item.Properties().stacksTo(1), 4, 0.95, 0.12, 0.60, 0xFFE53935, "Racing"));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
