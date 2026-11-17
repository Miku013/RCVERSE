package com.microdrone.mod.client.gui;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.ModBlocks;
import com.microdrone.mod.inventory.RcWorkbenchMenu;
import com.microdrone.mod.item.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * RC工作台の新世代メカニカルフレームGUIスクリーン (Ver 2.1)
 * - CNC削り出しチタンシルバー外枠 ＆ 六角ボルト ＆ カーボンファイバー背景
 * - メインタイトル「RC WORKBENCH」の完全センタリング配置
 * - ワイド化された左側セレクターラック（幅80px、ゆったりスロット、上下スクロール対応）
 *   - [車両] RCカー、ドローン、戦車
 *   - [設備] プロポ送信機、急速充電器、電光掲示板、ブーストジャンプ台、レーシングスロープ
 *   - [部品] 全27種類の公式クラフトパーツを網羅（バッテリー全Tier、モーター全Tier、缶、シャフト、ワイヤー、磁石、基板、アンテナ等）
 * - 3x3クラフトグリッドへの素材半透明シャドー（ゴースト）表示
 * - 天板展示スロット（ゴールド枠）
 */
public class RcWorkbenchScreen extends AbstractContainerScreen<RcWorkbenchMenu> {

    private static final ResourceLocation WORKBENCH_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/gui/container/rc_workbench.png");

    // 左側レシピパネルの寸法 (ワイド化: 幅80px)
    private static final int PANEL_WIDTH = 80;
    private static final int PANEL_HEIGHT = 166;
    private static final int PANEL_OFFSET_X = 82; // メインウィンドウ左側からのオフセット

    private static final int VISIBLE_SLOT_ROWS = 5; // 1画面に表示する行数 (2列 × 5行 = 10個)

    // レシピカテゴリ
    public enum RecipeCategory {
        VEHICLES("gui.micro_drone_mod.workbench.category.vehicles"),
        STATIONS("gui.micro_drone_mod.workbench.category.stations"),
        PARTS("gui.micro_drone_mod.workbench.category.parts");

        private final String translationKey;
        RecipeCategory(String translationKey) {
            this.translationKey = translationKey;
        }
        public Component getLabel() {
            return Component.translatable(translationKey);
        }
    }

    // レシピカタログ定義
    public record RecipeCatalogEntry(
            ItemStack resultItem,
            ItemStack[] gridIngredients // 長さ9の配列 (3x3)
    ) {
        public RecipeCatalogEntry(ItemStack resultItem, Component ignoredTitle, ItemStack[] gridIngredients) {
            this(resultItem, gridIngredients);
        }

        public Component title() {
            return resultItem.getHoverName();
        }
    }

    private final Map<RecipeCategory, List<RecipeCatalogEntry>> categorizedRecipes = new EnumMap<>(RecipeCategory.class);
    private RecipeCategory currentCategory = RecipeCategory.VEHICLES;
    private int selectedRecipeIndex = 0; // 現在選択中のインデックス
    private int scrollRowOffset = 0;     // 上下スクロール行オフセット

    public RcWorkbenchScreen(RcWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 28; // renderLabels で動的センタリング
        this.titleLabelY = 5;
        this.inventoryLabelY = 73;

        initCategorizedRecipes();
    }

    /**
     * カテゴリ別全RCレシピの初期化
     */
    private void initCategorizedRecipes() {
        // =============================================================
        // 1. 車両・機体 (VEHICLES)
        // =============================================================
        List<RecipeCatalogEntry> vehicles = new ArrayList<>();

        // (1) RCカー
        ItemStack[] carGrid = new ItemStack[9];
        carGrid[1] = new ItemStack(ModItems.DRONE_SCREWS.get());
        carGrid[3] = new ItemStack(Items.LEATHER);
        carGrid[4] = new ItemStack(ModItems.DRONE_FRAME.get());
        carGrid[5] = new ItemStack(Items.REDSTONE);
        carGrid[6] = new ItemStack(ModItems.ESC_BOARD.get());
        carGrid[7] = new ItemStack(ModItems.CONNECTION_CABLE.get());
        carGrid[8] = new ItemStack(ModItems.ESC_BOARD.get());
        vehicles.add(new RecipeCatalogEntry(new ItemStack(ModItems.RC_CAR_ITEM.get()), Component.literal("RCカー"), carGrid));

        // (2) マイクロドローン
        ItemStack[] droneGrid = new ItemStack[9];
        droneGrid[0] = new ItemStack(ModItems.PROPELLER.get());
        droneGrid[1] = new ItemStack(ModItems.MOTOR_TIER_1.get());
        droneGrid[2] = new ItemStack(ModItems.PROPELLER.get());
        droneGrid[3] = new ItemStack(ModItems.DRONE_SCREWS.get());
        droneGrid[4] = new ItemStack(ModItems.DRONE_FRAME.get());
        droneGrid[5] = new ItemStack(ModItems.DRONE_SCREWS.get());
        droneGrid[6] = new ItemStack(ModItems.CONNECTION_CABLE.get());
        droneGrid[7] = new ItemStack(ModItems.ESC_BOARD.get());
        droneGrid[8] = new ItemStack(ModItems.CONNECTION_CABLE.get());
        vehicles.add(new RecipeCatalogEntry(new ItemStack(ModItems.DRONE_SPAWN_ITEM.get()), Component.literal("マイクロドローン"), droneGrid));

        // (3) RC戦車
        ItemStack[] tankGrid = new ItemStack[9];
        tankGrid[1] = new ItemStack(Items.IRON_INGOT);
        tankGrid[3] = new ItemStack(Items.DISPENSER);
        tankGrid[4] = new ItemStack(ModItems.DRONE_FRAME.get());
        tankGrid[5] = new ItemStack(ModItems.MOTOR_TIER_1.get());
        tankGrid[6] = new ItemStack(ModItems.ESC_BOARD.get());
        tankGrid[7] = new ItemStack(ModItems.CONNECTION_CABLE.get());
        tankGrid[8] = new ItemStack(ModItems.ESC_BOARD.get());
        vehicles.add(new RecipeCatalogEntry(new ItemStack(ModItems.RC_TANK_ITEM.get()), Component.literal("RC戦車"), tankGrid));

        categorizedRecipes.put(RecipeCategory.VEHICLES, vehicles);

        // =============================================================
        // 2. 設備・コース (STATIONS)
        // =============================================================
        List<RecipeCatalogEntry> stations = new ArrayList<>();

        // (1) プロポ送信機
        ItemStack[] controllerGrid = new ItemStack[9];
        controllerGrid[0] = new ItemStack(ModItems.RADIO_ANTENNA.get());
        controllerGrid[1] = new ItemStack(ModItems.CONTROLLER_CASE.get());
        controllerGrid[2] = new ItemStack(ModItems.RADIO_ANTENNA.get());
        controllerGrid[3] = new ItemStack(ModItems.GIMBAL_STICK.get());
        controllerGrid[4] = new ItemStack(ModItems.LCD_SCREEN.get());
        controllerGrid[5] = new ItemStack(ModItems.GIMBAL_STICK.get());
        controllerGrid[6] = new ItemStack(ModItems.BATTERY_TIER_1.get());
        controllerGrid[7] = new ItemStack(ModItems.CONTROLLER_BOARD.get());
        controllerGrid[8] = new ItemStack(ModItems.BATTERY_TIER_1.get());
        stations.add(new RecipeCatalogEntry(new ItemStack(ModItems.DRONE_CONTROLLER.get()), Component.literal("プロポ送信機"), controllerGrid));

        // (2) 急速充電ステーション
        ItemStack[] chargerGrid = new ItemStack[9];
        chargerGrid[0] = new ItemStack(Items.COPPER_INGOT);
        chargerGrid[1] = new ItemStack(Items.REDSTONE);
        chargerGrid[2] = new ItemStack(Items.COPPER_INGOT);
        chargerGrid[3] = new ItemStack(Items.REDSTONE);
        chargerGrid[4] = new ItemStack(Items.SMOOTH_STONE);
        chargerGrid[5] = new ItemStack(Items.REDSTONE);
        chargerGrid[6] = new ItemStack(Items.IRON_INGOT);
        chargerGrid[7] = new ItemStack(Items.IRON_INGOT);
        chargerGrid[8] = new ItemStack(Items.IRON_INGOT);
        stations.add(new RecipeCatalogEntry(new ItemStack(ModBlocks.DRONE_CHARGER.get()), Component.literal("急速充電ステーション"), chargerGrid));

        // (3) 電光掲示板
        ItemStack[] displayGrid = new ItemStack[9];
        displayGrid[0] = new ItemStack(Items.IRON_INGOT);
        displayGrid[1] = new ItemStack(Items.IRON_INGOT);
        displayGrid[2] = new ItemStack(Items.IRON_INGOT);
        displayGrid[3] = new ItemStack(Items.GLASS_PANE);
        displayGrid[4] = new ItemStack(ModItems.LCD_SCREEN.get());
        displayGrid[5] = new ItemStack(Items.GLASS_PANE);
        displayGrid[6] = new ItemStack(Items.IRON_INGOT);
        displayGrid[7] = new ItemStack(Items.REDSTONE);
        displayGrid[8] = new ItemStack(Items.IRON_INGOT);
        stations.add(new RecipeCatalogEntry(new ItemStack(ModBlocks.RACE_DISPLAY.get()), Component.literal("電光掲示板"), displayGrid));

        // (4) ブーストジャンプ台
        ItemStack[] rampGrid = new ItemStack[9];
        rampGrid[1] = new ItemStack(Items.REDSTONE);
        rampGrid[3] = new ItemStack(Items.REDSTONE);
        rampGrid[4] = new ItemStack(ModBlocks.RACING_SLOPE.get());
        rampGrid[5] = new ItemStack(Items.REDSTONE);
        rampGrid[7] = new ItemStack(Items.GLOWSTONE_DUST);
        stations.add(new RecipeCatalogEntry(new ItemStack(ModBlocks.BOOST_RAMP.get()), Component.literal("ブーストジャンプ台"), rampGrid));

        // (5) レーシングスロープ
        ItemStack[] slopeGrid = new ItemStack[9];
        slopeGrid[2] = new ItemStack(Items.IRON_INGOT);
        slopeGrid[4] = new ItemStack(Items.SMOOTH_STONE);
        slopeGrid[5] = new ItemStack(Items.SMOOTH_STONE);
        slopeGrid[6] = new ItemStack(Items.SMOOTH_STONE);
        slopeGrid[7] = new ItemStack(Items.SMOOTH_STONE);
        slopeGrid[8] = new ItemStack(Items.SMOOTH_STONE);
        stations.add(new RecipeCatalogEntry(new ItemStack(ModBlocks.RACING_SLOPE.get()), Component.literal("レーシングスロープ"), slopeGrid));

        categorizedRecipes.put(RecipeCategory.STATIONS, stations);

        // =============================================================
        // 3. パーツ・素材 (PARTS - 全27種)
        // =============================================================
        List<RecipeCatalogEntry> parts = new ArrayList<>();

        // (1) LiPoバッテリー Tier 1
        ItemStack[] b1 = new ItemStack[9];
        b1[1] = new ItemStack(Items.COPPER_INGOT);
        b1[3] = new ItemStack(Items.COPPER_INGOT);
        b1[4] = new ItemStack(Items.REDSTONE);
        b1[5] = new ItemStack(Items.COPPER_INGOT);
        b1[7] = new ItemStack(Items.IRON_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.BATTERY_TIER_1.get()), Component.literal("LiPoバッテリー T1"), b1));

        // (2) LiPoバッテリー Tier 2
        ItemStack[] b2 = new ItemStack[9];
        b2[1] = new ItemStack(Items.IRON_INGOT);
        b2[3] = new ItemStack(Items.IRON_INGOT);
        b2[4] = new ItemStack(ModItems.BATTERY_TIER_1.get());
        b2[5] = new ItemStack(Items.IRON_INGOT);
        b2[7] = new ItemStack(Items.REDSTONE);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.BATTERY_TIER_2.get()), Component.literal("LiPoバッテリー T2"), b2));

        // (3) LiPoバッテリー Tier 3
        ItemStack[] b3 = new ItemStack[9];
        b3[1] = new ItemStack(Items.GOLD_INGOT);
        b3[3] = new ItemStack(Items.GOLD_INGOT);
        b3[4] = new ItemStack(ModItems.BATTERY_TIER_2.get());
        b3[5] = new ItemStack(Items.GOLD_INGOT);
        b3[7] = new ItemStack(Items.REDSTONE_BLOCK);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.BATTERY_TIER_3.get()), Component.literal("LiPoバッテリー T3"), b3));

        // (4) LiPoバッテリー Tier 4
        ItemStack[] b4 = new ItemStack[9];
        b4[1] = new ItemStack(Items.DIAMOND);
        b4[3] = new ItemStack(Items.NETHERITE_SCRAP);
        b4[4] = new ItemStack(ModItems.BATTERY_TIER_3.get());
        b4[5] = new ItemStack(Items.NETHERITE_SCRAP);
        b4[7] = new ItemStack(Items.DIAMOND);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.BATTERY_TIER_4.get()), Component.literal("LiPoバッテリー T4"), b4));

        // (5) チューニングモーター Tier 1
        ItemStack[] m1 = new ItemStack[9];
        m1[1] = new ItemStack(ModItems.MOTOR_SHAFT.get());
        m1[3] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m1[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_1.get());
        m1[5] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m1[6] = new ItemStack(ModItems.MAGNET_WIRE.get());
        m1[7] = new ItemStack(ModItems.MOTOR_STATOR_TERMINAL.get());
        m1[8] = new ItemStack(ModItems.TINY_SPRING.get());
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_TIER_1.get()), Component.literal("チューニングモーター T1"), m1));

        // (6) チューニングモーター Tier 2
        ItemStack[] m2 = new ItemStack[9];
        m2[1] = new ItemStack(ModItems.MOTOR_SHAFT.get());
        m2[3] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m2[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_2.get());
        m2[5] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m2[6] = new ItemStack(ModItems.MAGNET_WIRE.get());
        m2[7] = new ItemStack(ModItems.MOTOR_STATOR_TERMINAL.get());
        m2[8] = new ItemStack(ModItems.TINY_SPRING.get());
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_TIER_2.get()), Component.literal("チューニングモーター T2"), m2));

        // (7) チューニングモーター Tier 3
        ItemStack[] m3 = new ItemStack[9];
        m3[1] = new ItemStack(ModItems.MOTOR_SHAFT.get());
        m3[3] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m3[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_3.get());
        m3[5] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m3[6] = new ItemStack(ModItems.MAGNET_WIRE.get());
        m3[7] = new ItemStack(ModItems.MOTOR_STATOR_TERMINAL.get());
        m3[8] = new ItemStack(ModItems.TINY_SPRING.get());
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_TIER_3.get()), Component.literal("チューニングモーター T3"), m3));

        // (8) チューニングモーター Tier 4
        ItemStack[] m4 = new ItemStack[9];
        m4[1] = new ItemStack(ModItems.MOTOR_SHAFT.get());
        m4[3] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m4[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_4.get());
        m4[5] = new ItemStack(ModItems.NEODYMIUM_MAGNET.get());
        m4[6] = new ItemStack(ModItems.MAGNET_WIRE.get());
        m4[7] = new ItemStack(ModItems.MOTOR_STATOR_TERMINAL.get());
        m4[8] = new ItemStack(ModItems.TINY_SPRING.get());
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_TIER_4.get()), Component.literal("チューニングモーター T4"), m4));

        // (9) モーターシャフト
        ItemStack[] mShaft = new ItemStack[9];
        mShaft[1] = new ItemStack(Items.IRON_NUGGET);
        mShaft[4] = new ItemStack(Items.IRON_INGOT);
        mShaft[7] = new ItemStack(Items.IRON_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_SHAFT.get()), Component.literal("モーターシャフト"), mShaft));

        // (10) モーター缶 Tier 1
        ItemStack[] mCan1 = new ItemStack[9];
        mCan1[0] = new ItemStack(Items.IRON_NUGGET);
        mCan1[1] = new ItemStack(Items.IRON_INGOT);
        mCan1[2] = new ItemStack(Items.IRON_NUGGET);
        mCan1[3] = new ItemStack(Items.IRON_INGOT);
        mCan1[5] = new ItemStack(Items.IRON_INGOT);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_CAN_TIER_1.get()), Component.literal("モーター缶 T1"), mCan1));

        // (11) モーター缶 Tier 2
        ItemStack[] mCan2 = new ItemStack[9];
        mCan2[1] = new ItemStack(Items.DIAMOND);
        mCan2[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_1.get());
        mCan2[7] = new ItemStack(Items.LAPIS_LAZULI);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_CAN_TIER_2.get()), Component.literal("モーター缶 T2"), mCan2));

        // (12) モーター缶 Tier 3
        ItemStack[] mCan3 = new ItemStack[9];
        mCan3[1] = new ItemStack(Items.AMETHYST_SHARD);
        mCan3[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_2.get());
        mCan3[7] = new ItemStack(Items.EMERALD);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_CAN_TIER_3.get()), Component.literal("モーター缶 T3"), mCan3));

        // (13) モーター缶 Tier 4
        ItemStack[] mCan4 = new ItemStack[9];
        mCan4[1] = new ItemStack(Items.NETHERITE_INGOT);
        mCan4[4] = new ItemStack(ModItems.MOTOR_CAN_TIER_3.get());
        mCan4[7] = new ItemStack(Items.GOLD_INGOT);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_CAN_TIER_4.get()), Component.literal("モーター缶 T4"), mCan4));

        // (14) ステータ端子
        ItemStack[] term = new ItemStack[9];
        term[1] = new ItemStack(Items.IRON_NUGGET);
        term[4] = new ItemStack(Items.COPPER_INGOT);
        term[7] = new ItemStack(Items.GOLD_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MOTOR_STATOR_TERMINAL.get()), Component.literal("ステータ端子"), term));

        // (15) マグネットワイヤー
        ItemStack[] mWire = new ItemStack[9];
        mWire[3] = new ItemStack(Items.COPPER_INGOT);
        mWire[4] = new ItemStack(Items.REDSTONE);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.MAGNET_WIRE.get()), Component.literal("マグネットワイヤー"), mWire));

        // (16) ネオジム磁石
        ItemStack[] mag = new ItemStack[9];
        mag[3] = new ItemStack(Items.REDSTONE);
        mag[4] = new ItemStack(Items.IRON_INGOT);
        mag[5] = new ItemStack(Items.BLUE_DYE);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.NEODYMIUM_MAGNET.get()), Component.literal("ネオジム磁石"), mag));

        // (17) カーボンプロペラ
        ItemStack[] prop = new ItemStack[9];
        prop[1] = new ItemStack(Items.REDSTONE);
        prop[3] = new ItemStack(Items.STICK);
        prop[4] = new ItemStack(Items.IRON_NUGGET);
        prop[5] = new ItemStack(Items.STICK);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.PROPELLER.get()), Component.literal("カーボンプロペラ"), prop));

        // (18) カーボンフレーム
        ItemStack[] frame = new ItemStack[9];
        frame[0] = new ItemStack(Items.IRON_NUGGET);
        frame[1] = new ItemStack(Items.IRON_INGOT);
        frame[2] = new ItemStack(Items.IRON_NUGGET);
        frame[3] = new ItemStack(Items.IRON_INGOT);
        frame[4] = new ItemStack(Items.COAL);
        frame[5] = new ItemStack(Items.IRON_INGOT);
        frame[6] = new ItemStack(Items.IRON_NUGGET);
        frame[7] = new ItemStack(Items.IRON_INGOT);
        frame[8] = new ItemStack(Items.IRON_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.DRONE_FRAME.get()), Component.literal("CFRPフレーム"), frame));

        // (19) チタン精密ネジ
        ItemStack[] screws = new ItemStack[9];
        screws[0] = new ItemStack(Items.IRON_NUGGET);
        screws[2] = new ItemStack(Items.IRON_NUGGET);
        screws[4] = new ItemStack(Items.IRON_NUGGET);
        screws[6] = new ItemStack(Items.IRON_NUGGET);
        screws[8] = new ItemStack(Items.IRON_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.DRONE_SCREWS.get()), Component.literal("チタン精密ネジ"), screws));

        // (20) サスペンションスプリング
        ItemStack[] spring = new ItemStack[9];
        spring[1] = new ItemStack(Items.IRON_NUGGET);
        spring[3] = new ItemStack(Items.IRON_NUGGET);
        spring[7] = new ItemStack(Items.IRON_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.TINY_SPRING.get()), Component.literal("サスペンションバネ"), spring));

        // (21) ESC電子基板
        ItemStack[] esc = new ItemStack[9];
        esc[0] = new ItemStack(Items.GOLD_NUGGET);
        esc[1] = new ItemStack(Items.REDSTONE);
        esc[2] = new ItemStack(Items.GOLD_NUGGET);
        esc[3] = new ItemStack(Items.COPPER_INGOT);
        esc[4] = new ItemStack(Items.QUARTZ);
        esc[5] = new ItemStack(Items.COPPER_INGOT);
        esc[7] = new ItemStack(Items.REDSTONE);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.ESC_BOARD.get()), Component.literal("ESC電子基板"), esc));

        // (22) 接続ケーブル
        ItemStack[] cable = new ItemStack[9];
        cable[1] = new ItemStack(Items.STRING);
        cable[4] = new ItemStack(Items.COPPER_INGOT);
        cable[7] = new ItemStack(Items.STRING);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.CONNECTION_CABLE.get()), Component.literal("シリコン接続ケーブル"), cable));

        // (23) 無線アンテナ
        ItemStack[] ant = new ItemStack[9];
        ant[2] = new ItemStack(Items.IRON_NUGGET);
        ant[4] = new ItemStack(Items.IRON_INGOT);
        ant[6] = new ItemStack(Items.REDSTONE);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.RADIO_ANTENNA.get()), Component.literal("高利得無線アンテナ"), ant));

        // (24) 送信機シェルケース
        ItemStack[] cCase = new ItemStack[9];
        cCase[0] = new ItemStack(Items.IRON_INGOT);
        cCase[1] = new ItemStack(Items.GLASS_PANE);
        cCase[2] = new ItemStack(Items.IRON_INGOT);
        cCase[3] = new ItemStack(Items.IRON_INGOT);
        cCase[5] = new ItemStack(Items.IRON_INGOT);
        cCase[6] = new ItemStack(Items.IRON_INGOT);
        cCase[7] = new ItemStack(Items.IRON_INGOT);
        cCase[8] = new ItemStack(Items.IRON_INGOT);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.CONTROLLER_CASE.get()), Component.literal("送信機シェルケース"), cCase));

        // (25) 送信機メイン基板
        ItemStack[] cBoard = new ItemStack[9];
        cBoard[0] = new ItemStack(Items.REDSTONE);
        cBoard[1] = new ItemStack(Items.GOLD_INGOT);
        cBoard[2] = new ItemStack(Items.REDSTONE);
        cBoard[3] = new ItemStack(ModItems.ESC_BOARD.get());
        cBoard[4] = new ItemStack(Items.QUARTZ);
        cBoard[5] = new ItemStack(ModItems.ESC_BOARD.get());
        cBoard[6] = new ItemStack(Items.REDSTONE);
        cBoard[7] = new ItemStack(Items.COPPER_INGOT);
        cBoard[8] = new ItemStack(Items.REDSTONE);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.CONTROLLER_BOARD.get()), Component.literal("送信機メイン基板"), cBoard));

        // (26) 高精度ジンバルスティック
        ItemStack[] stick = new ItemStack[9];
        stick[1] = new ItemStack(Items.REDSTONE);
        stick[4] = new ItemStack(Items.STICK);
        stick[7] = new ItemStack(Items.STONE_BUTTON);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.GIMBAL_STICK.get()), Component.literal("高精度ジンバル"), stick));

        // (27) カラー液晶画面
        ItemStack[] lcd = new ItemStack[9];
        lcd[0] = new ItemStack(Items.GLASS_PANE);
        lcd[1] = new ItemStack(Items.GLASS_PANE);
        lcd[2] = new ItemStack(Items.GLASS_PANE);
        lcd[3] = new ItemStack(Items.REDSTONE);
        lcd[4] = new ItemStack(Items.GLOWSTONE_DUST);
        lcd[5] = new ItemStack(Items.REDSTONE);
        lcd[6] = new ItemStack(Items.IRON_NUGGET);
        lcd[7] = new ItemStack(Items.IRON_NUGGET);
        lcd[8] = new ItemStack(Items.IRON_NUGGET);
        parts.add(new RecipeCatalogEntry(new ItemStack(ModItems.LCD_SCREEN.get()), Component.literal("カラー液晶画面"), lcd));

        categorizedRecipes.put(RecipeCategory.PARTS, parts);
    }

    private List<RecipeCatalogEntry> getCurrentRecipes() {
        return categorizedRecipes.getOrDefault(currentCategory, List.of());
    }

    private int getMaxScrollOffset() {
        int totalItems = getCurrentRecipes().size();
        int totalRows = (totalItems + 1) / 2;
        return Math.max(0, totalRows - VISIBLE_SLOT_ROWS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 3x3 クラフトグリッドへのゴースト（シャドー）素材描画
        renderGhostIngredients(graphics);

        // 左側パネルのアイテム描画
        renderRecipeCatalogItems(graphics, mouseX, mouseY);

        // ツールチップ
        this.renderTooltip(graphics, mouseX, mouseY);
        renderRecipeCatalogTooltip(graphics, mouseX, mouseY);
        renderTabTooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 1. メインウィンドウ背景 (x=0, y=0, w=176, h=166)
        graphics.blit(WORKBENCH_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // 2. 左側レシピパネル背景 (テクスチャの x=176, y=0 から幅80, 高さ166)
        int panelX = x - PANEL_OFFSET_X;
        graphics.blit(WORKBENCH_TEXTURE, panelX, y, 176, 0, PANEL_WIDTH, PANEL_HEIGHT);

        // 3. アクティブタブの下線ハイライト (シアンネオン)
        int activeTabIdx = currentCategory.ordinal();
        int[] tabXs = new int[] { panelX + 5, panelX + 29, panelX + 53 };
        int tabUnderlineX = tabXs[activeTabIdx];
        graphics.fill(tabUnderlineX, y + 21, tabUnderlineX + 23, y + 23, 0xFF38BDF8);

        // 4. スクロールバーのサム（つまみ）描画
        int maxScroll = getMaxScrollOffset();
        if (maxScroll > 0) {
            int railHeight = 120;
            int thumbHeight = Math.max(16, railHeight * VISIBLE_SLOT_ROWS / ((getCurrentRecipes().size() + 1) / 2));
            int thumbY = y + 27 + (scrollRowOffset * (railHeight - thumbHeight) / maxScroll);
            int thumbX = panelX + 72;
            graphics.fill(thumbX, thumbY, thumbX + 3, thumbY + thumbHeight, 0xFF38BDF8);
        }

        // 5. 選択中レシピ枠のハイライト (現在のスクロール位置で表示されている場合のみ)
        int visibleIndex = selectedRecipeIndex - (scrollRowOffset * 2);
        if (visibleIndex >= 0 && visibleIndex < VISIBLE_SLOT_ROWS * 2) {
            int col = visibleIndex % 2;
            int row = visibleIndex / 2;
            int slotX = panelX + (col == 0 ? 11 : 43);
            int slotY = y + 26 + (row * 26);

            // シアンブルーの洗練された二重枠で選択を強調
            graphics.fill(slotX, slotY, slotX + 22, slotY + 2, 0xFF38BDF8);
            graphics.fill(slotX, slotY + 20, slotX + 22, slotY + 22, 0xFF38BDF8);
            graphics.fill(slotX, slotY + 2, slotX + 2, slotY + 20, 0xFF38BDF8);
            graphics.fill(slotX + 20, slotY + 2, slotX + 22, slotY + 20, 0xFF38BDF8);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // タイトル（中央揃えで完全にセンタリング！）
        Component titleComp = Component.literal("RC WORKBENCH");
        int titleWidth = this.font.width(titleComp);
        int centerTitleX = (this.imageWidth - titleWidth) / 2;
        graphics.drawString(this.font, titleComp, centerTitleX, 5, 0x38BDF8, false);

        // インベントリラベル
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x94A3B8, false);

        // 天板展示スロットのラベル（完成品スロットの右隣 x=151, y=24）
        graphics.drawString(this.font, Component.translatable("gui.micro_drone_mod.workbench.display"), 151, 24, 0xF59E0B, false);

        // 左側パネルのタブ文字表示 (幅23pxに合わせて配置)
        int panelLeft = -PANEL_OFFSET_X;
        RecipeCategory[] categories = RecipeCategory.values();
        int[] tabLabelXs = new int[] { panelLeft + 6, panelLeft + 30, panelLeft + 54 };

        for (int i = 0; i < 3; i++) {
            boolean isActive = (i == currentCategory.ordinal());
            int color = isActive ? 0x38BDF8 : 0x94A3B8;
            graphics.drawString(this.font, categories[i].getLabel(), tabLabelXs[i], 11, color, false);
        }
    }

    /**
     * 左側パネルのアイテムアイコンを描画 (スクロール対応)
     */
    private void renderRecipeCatalogItems(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = this.leftPos - PANEL_OFFSET_X;
        int panelY = this.topPos;

        List<RecipeCatalogEntry> currentList = getCurrentRecipes();
        int startIndex = scrollRowOffset * 2;
        int endIndex = Math.min(startIndex + (VISIBLE_SLOT_ROWS * 2), currentList.size());

        for (int i = startIndex; i < endIndex; i++) {
            int displayIdx = i - startIndex;
            int col = displayIdx % 2;
            int row = displayIdx / 2;
            int slotX = panelX + (col == 0 ? 11 : 43);
            int slotY = panelY + 26 + (row * 26);
            int itemX = slotX + 3;
            int itemY = slotY + 3;

            RecipeCatalogEntry entry = currentList.get(i);
            graphics.renderItem(entry.resultItem(), itemX, itemY);

            // マウスホバー枠
            if (mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22) {
                graphics.fill(slotX, slotY, slotX + 22, slotY + 22, 0x4038BDF8);
            }
        }
    }

    /**
     * 選択されたレシピの素材を 3x3 グリッドの空きマスに薄くゴースト（シャドー）表示
     */
    private void renderGhostIngredients(GuiGraphics graphics) {
        List<RecipeCatalogEntry> currentList = getCurrentRecipes();
        if (selectedRecipeIndex < 0 || selectedRecipeIndex >= currentList.size()) return;

        RecipeCatalogEntry entry = currentList.get(selectedRecipeIndex);
        ItemStack[] ingredients = entry.gridIngredients();

        // 3x3 スロット (Slot 1〜9)
        for (int i = 0; i < 9; i++) {
            Slot slot = this.menu.getSlot(i + 1);
            ItemStack neededItem = ingredients[i];

            // プレイヤーがまだ何も置いていないスロットのみ、ゴーストを表示
            if (neededItem != null && !neededItem.isEmpty() && !slot.hasItem()) {
                int slotScreenX = this.leftPos + slot.x;
                int slotScreenY = this.topPos + slot.y;

                // アイコンを半透明描画 (35% 不透明度)
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.35F);
                graphics.renderItem(neededItem, slotScreenX, slotScreenY);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                // スロット内に薄い青色の半透明オーバーレイ
                graphics.fill(slotScreenX, slotScreenY, slotScreenX + 16, slotScreenY + 16, 0x1A38BDF8);
            }
        }
    }

    /**
     * 左側カタログアイテムのツールチップ表示
     */
    private void renderRecipeCatalogTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = this.leftPos - PANEL_OFFSET_X;
        int panelY = this.topPos;

        List<RecipeCatalogEntry> currentList = getCurrentRecipes();
        int startIndex = scrollRowOffset * 2;
        int endIndex = Math.min(startIndex + (VISIBLE_SLOT_ROWS * 2), currentList.size());

        for (int i = startIndex; i < endIndex; i++) {
            int displayIdx = i - startIndex;
            int col = displayIdx % 2;
            int row = displayIdx / 2;
            int slotX = panelX + (col == 0 ? 11 : 43);
            int slotY = panelY + 26 + (row * 26);

            if (mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22) {
                RecipeCatalogEntry entry = currentList.get(i);
                graphics.renderTooltip(this.font, entry.title(), mouseX, mouseY);
                break;
            }
        }
    }

    /**
     * タブのホバーツールチップ表示
     */
    private void renderTabTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = this.leftPos - PANEL_OFFSET_X;
        int panelY = this.topPos;

        int[] tabXs = new int[] { panelX + 5, panelX + 29, panelX + 53 };
        Component[] fullLabels = new Component[] {
                Component.translatable("gui.micro_drone_mod.workbench.category.vehicles.full", categorizedRecipes.get(RecipeCategory.VEHICLES).size()),
                Component.translatable("gui.micro_drone_mod.workbench.category.stations.full", categorizedRecipes.get(RecipeCategory.STATIONS).size()),
                Component.translatable("gui.micro_drone_mod.workbench.category.parts.full", categorizedRecipes.get(RecipeCategory.PARTS).size())
        };

        for (int i = 0; i < 3; i++) {
            int tx = tabXs[i];
            int ty = panelY + 8;
            if (mouseX >= tx && mouseX <= tx + 23 && mouseY >= ty && mouseY <= ty + 13) {
                graphics.renderTooltip(this.font, fullLabels[i], mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelX = this.leftPos - PANEL_OFFSET_X;
        int panelY = this.topPos;

        // 左側パネル内でのマウスホイールスクロール
        if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + PANEL_HEIGHT) {
            int maxScroll = getMaxScrollOffset();
            if (maxScroll > 0) {
                if (scrollY > 0) {
                    this.scrollRowOffset = Math.max(0, this.scrollRowOffset - 1);
                } else if (scrollY < 0) {
                    this.scrollRowOffset = Math.min(maxScroll, this.scrollRowOffset + 1);
                }
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = this.leftPos - PANEL_OFFSET_X;
        int panelY = this.topPos;

        // 1. 上部タブのクリック判定
        int[] tabXs = new int[] { panelX + 5, panelX + 29, panelX + 53 };
        for (int i = 0; i < 3; i++) {
            int tx = tabXs[i];
            int ty = panelY + 8;
            if (mouseX >= tx && mouseX <= tx + 23 && mouseY >= ty && mouseY <= ty + 13) {
                if (button == 0) { // 左クリック
                    RecipeCategory newCat = RecipeCategory.values()[i];
                    if (this.currentCategory != newCat) {
                        this.currentCategory = newCat;
                        this.selectedRecipeIndex = 0; // カテゴリ切り替え時は先頭を選択
                        this.scrollRowOffset = 0;     // スクロールリセット
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.2F)
                        );
                        return true;
                    }
                }
            }
        }

        // 2. 左側カタログアイテムのクリック判定 (スクロール位置考慮)
        List<RecipeCatalogEntry> currentList = getCurrentRecipes();
        int startIndex = scrollRowOffset * 2;
        int endIndex = Math.min(startIndex + (VISIBLE_SLOT_ROWS * 2), currentList.size());

        for (int i = startIndex; i < endIndex; i++) {
            int displayIdx = i - startIndex;
            int col = displayIdx % 2;
            int row = displayIdx / 2;
            int slotX = panelX + (col == 0 ? 11 : 43);
            int slotY = panelY + 26 + (row * 26);

            if (mouseX >= slotX && mouseX <= slotX + 22 && mouseY >= slotY && mouseY <= slotY + 22) {
                if (button == 0) { // 左クリック
                    this.selectedRecipeIndex = i;
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                    );
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
