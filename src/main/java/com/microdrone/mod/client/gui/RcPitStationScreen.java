package com.microdrone.mod.client.gui;

import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.decal.RcDecalHolder;
import com.microdrone.mod.entity.*;
import com.microdrone.mod.item.*;
import com.microdrone.mod.network.UpdatePitTiresPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * RCピット作業台のメインGUI画面。
 * スクロール対応のタイヤ選択パネル、前後タイヤのテーマカラー表示、
 * リアルタイムスペックメーター、および360度自由回転3Dプレビューを提供します。
 */
@OnlyIn(Dist.CLIENT)
public class RcPitStationScreen extends Screen {
    private final BlockPos blockPos;
    private final ItemStack vehicleStack;

    // 3Dプレビュー用のダミーエンティティ
    private Entity previewEntity;
    private RcDecalHolder decalHolder;

    // タイヤ選択状態 (初期値: vehicleStackのタグまたはnormal)
    private String selectedFrontTire = "normal";
    private String selectedRearTire = "normal";

    private final List<Button> frontTireButtons = new ArrayList<>();
    private final List<Button> rearTireButtons = new ArrayList<>();
    private Button saveButton;
    private Button takeOutButton;
    private Button resetButton;
    private Button closeButton;

    // スクロール状態
    private double scrollAmount = 0.0;
    private int maxScroll = 0;
    private boolean isScrollingBar = false;

    // 3Dプレビュー用のデフォルトアングル定数 (浅い見下ろし角で側面・前面・タイヤが一番映えるアングル)
    private static final float DEFAULT_YAW = -60.0F;     // 左右回転（初期値: 斜め前約60度）
    private static final float DEFAULT_PITCH = 12.0F;    // 上下角度（浅い見下ろし12度）
    private static final float DEFAULT_ZOOM = 85.0F;     // 拡大率
    private static final long ROTATION_RESUME_DELAY_MS = 2200L; // タイヤ変更・操作後の停止時間（約2.2秒）
    private static final float ROTATION_DEGREES_PER_SEC = 24.0F; // ショールーム自動回転速度（1秒で24度、約15秒で1周）

    // 3Dカメラ・視点パラメータ
    private float previewYaw = DEFAULT_YAW;     // 左右回転（初期値: 斜め前約60度）
    private float previewPitch = DEFAULT_PITCH; // 上下角度（浅い見下ろし12度）
    private float previewZoom = DEFAULT_ZOOM;   // 拡大率
    private float modelPanX = 0.0F;             // 平行移動 X
    private float modelPanY = 0.0F;             // 平行移動 Y

    // 自動回転タイマー管理
    private long lastInteractionTime = System.currentTimeMillis();
    private long lastFrameTime = System.currentTimeMillis();

    // マウスドラッグ状態
    private boolean isRotating = false;
    private boolean isPanning = false;

    private static final String[] TIRE_TYPES = {"normal", "drift", "grip", "offroad", "spike"};
    private static final String[] TIRE_NAMES = {"NORMAL", "DRIFT", "GRIP", "OFFROAD", "SPIKE"};
    private static final String[] TIRE_COLOR_CODES = {"§f", "§b", "§6", "§a", "§d"};
    private static final int[] TIRE_COLORS = {0xFFe2e8f0, 0xFF38bdf8, 0xFFf59e0b, 0xFF22c55e, 0xFFd946ef};

    public RcPitStationScreen(BlockPos pos, ItemStack vehicleStack) {
        super(Component.translatable("gui.microdrone.pit_station.title"));
        this.blockPos = pos;
        this.vehicleStack = vehicleStack;

        // 保存されたタイヤセッティングの読み込み
        if (!vehicleStack.isEmpty()) {
            var customData = vehicleStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("FrontTire")) {
                    this.selectedFrontTire = tag.getString("FrontTire");
                }
                if (tag.contains("RearTire")) {
                    this.selectedRearTire = tag.getString("RearTire");
                }
            }
        }
    }

    @Override
    protected void init() {
        super.init();

        // ダミーエンティティの初期化
        initDummyEntity();

        this.clearWidgets();
        this.frontTireButtons.clear();
        this.rearTireButtons.clear();

        // 左側：タイヤカスタムスクロールパネル
        int leftX = 18;
        int btnW = 104;
        int btnH = 18;

        for (int i = 0; i < TIRE_TYPES.length; i++) {
            final String tireId = TIRE_TYPES[i];
            final int idx = i;
            Button btn = Button.builder(
                    getTireButtonLabel("F: ", idx, tireId.equals(this.selectedFrontTire)),
                    button -> {
                        this.selectedFrontTire = tireId;
                        playEquipSound();
                        updateTireButtons();
                        applyTiresToPreview();
                        resetToDefaultAngle();
                    }
            ).bounds(leftX + 4, 0, btnW, btnH).build();
            this.frontTireButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        for (int i = 0; i < TIRE_TYPES.length; i++) {
            final String tireId = TIRE_TYPES[i];
            final int idx = i;
            Button btn = Button.builder(
                    getTireButtonLabel("R: ", idx, tireId.equals(this.selectedRearTire)),
                    button -> {
                        this.selectedRearTire = tireId;
                        playEquipSound();
                        updateTireButtons();
                        applyTiresToPreview();
                        resetToDefaultAngle();
                    }
            ).bounds(leftX + 4, 0, btnW, btnH).build();
            this.rearTireButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        // 右側：アクションボタン（下部に整然と配置）
        int rightX = this.width - 146;
        int panelW = 132;
        int rightPanelY = 38;
        int rightPanelH = this.height - rightPanelY - 14;

        int btnActionW = panelW - 12;
        int saveBtnY = rightPanelY + rightPanelH - 46;
        int takeOutBtnY = rightPanelY + rightPanelH - 24;

        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.literal("§a✔ ")
                        .append(Component.translatable("gui.microdrone.pit_station.save")),
                button -> saveTireSettings(false)
        ).bounds(rightX + 6, saveBtnY, btnActionW, 19).build());

        this.takeOutButton = this.addRenderableWidget(Button.builder(
                Component.literal("§b⬆ ")
                        .append(Component.translatable("gui.microdrone.pit_station.take_out")),
                button -> saveTireSettings(true)
        ).bounds(rightX + 6, takeOutBtnY, btnActionW, 19).build());

        // アングル初期化ボタン（ヘッダー右側にスマートに配置）
        this.resetButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.microdrone.pit_station.reset"),
                button -> resetCamera()
        ).bounds(this.width - 144, 13, 108, 18).build());

        // 閉じるボタン（ヘッダー最右端に配置）
        this.closeButton = this.addRenderableWidget(Button.builder(
                Component.literal("✕"),
                button -> this.onClose()
        ).bounds(this.width - 32, 13, 20, 18).build());

        updateLayoutPositions();
    }

    private void playEquipSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.6F, 0.45F)
            );
        }
    }

    private Component getTireButtonLabel(String prefix, int idx, boolean isSelected) {
        String code = TIRE_COLOR_CODES[idx];
        String name = TIRE_NAMES[idx];
        if (isSelected) {
            return Component.literal(code + "▶ " + prefix + name + " ◀");
        }
        return Component.literal("§7" + prefix + code + name);
    }

    private void updateTireButtons() {
        for (int i = 0; i < this.frontTireButtons.size(); i++) {
            String tireId = TIRE_TYPES[i];
            this.frontTireButtons.get(i).setMessage(getTireButtonLabel("F: ", i, tireId.equals(this.selectedFrontTire)));
        }
        for (int i = 0; i < this.rearTireButtons.size(); i++) {
            String tireId = TIRE_TYPES[i];
            this.rearTireButtons.get(i).setMessage(getTireButtonLabel("R: ", i, tireId.equals(this.selectedRearTire)));
        }
    }

    private void applyTiresToPreview() {
        if (this.previewEntity instanceof RcCarEntity car) {
            car.setFrontTireType(this.selectedFrontTire);
            car.setRearTireType(this.selectedRearTire);
        }
    }

    private void saveTireSettings(boolean takeOut) {
        PacketDistributor.sendToServer(new UpdatePitTiresPayload(
                this.blockPos,
                this.selectedFrontTire,
                this.selectedRearTire,
                takeOut
        ));
        if (takeOut) {
            if (this.minecraft != null && this.minecraft.level != null) {
                var be = this.minecraft.level.getBlockEntity(this.blockPos);
                if (be instanceof com.microdrone.mod.block.entity.RcPitStationBlockEntity station) {
                    station.setStoredVehicle(ItemStack.EMPTY);
                }
            }
            this.onClose();
        }
    }

    private void resetToDefaultAngle() {
        this.previewYaw = DEFAULT_YAW;
        this.previewPitch = DEFAULT_PITCH;
        this.modelPanX = 0.0F;
        this.modelPanY = 0.0F;
        this.lastInteractionTime = System.currentTimeMillis();
    }

    private void resetCamera() {
        resetToDefaultAngle();
        if (this.vehicleStack.getItem() instanceof RcPlaneItem) {
            this.previewZoom = 60.0F;
        } else if (this.vehicleStack.getItem() instanceof RcBoatItem) {
            this.previewZoom = 70.0F;
        } else if (this.vehicleStack.getItem() instanceof RcSubmarineItem) {
            this.previewZoom = 70.0F;
        } else if (this.vehicleStack.getItem() instanceof RcTankItem) {
            this.previewZoom = 65.0F;
        } else if (this.vehicleStack.getItem() instanceof DroneSpawnItem) {
            this.previewZoom = 85.0F;
        } else {
            this.previewZoom = DEFAULT_ZOOM;
        }
    }

    private void initDummyEntity() {
        if (this.vehicleStack.isEmpty() || this.minecraft == null || this.minecraft.level == null) {
            this.previewEntity = null;
            this.decalHolder = null;
            return;
        }

        var item = this.vehicleStack.getItem();
        if (item instanceof RcPlaneItem) {
            this.previewEntity = ModEntities.RC_PLANE.get().create(this.minecraft.level);
            this.previewZoom = 60.0F;
        } else if (item instanceof RcBoatItem) {
            this.previewEntity = ModEntities.RC_BOAT.get().create(this.minecraft.level);
            this.previewZoom = 70.0F;
        } else if (item instanceof RcSubmarineItem) {
            this.previewEntity = ModEntities.RC_SUBMARINE.get().create(this.minecraft.level);
            this.previewZoom = 70.0F;
        } else if (item instanceof RcTankItem) {
            this.previewEntity = ModEntities.RC_TANK.get().create(this.minecraft.level);
            this.previewZoom = 65.0F;
        } else if (item instanceof DroneSpawnItem) {
            this.previewEntity = ModEntities.DRONE.get().create(this.minecraft.level);
            this.previewZoom = 85.0F;
        } else {
            this.previewEntity = ModEntities.RC_CAR.get().create(this.minecraft.level);
            this.previewZoom = DEFAULT_ZOOM;
        }

        if (this.previewEntity instanceof RcDecalHolder holder) {
            this.decalHolder = holder;
            String resolvedVariant = resolveVariantFromStack(this.vehicleStack);
            holder.setVariantId(resolvedVariant);

            // 車体色・デカール等のNBT復元
            var customData = this.vehicleStack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("BodyColor") && this.previewEntity instanceof RcCarEntity car) {
                    car.setBodyColor(tag.getInt("BodyColor"));
                }
                if (tag.contains("Decals", 10)) {
                    List<CarDecalData> loaded = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
                    holder.setDecals(loaded);
                }
            }
        }

        applyTiresToPreview();
    }

    private static String resolveVariantFromStack(ItemStack stack) {
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            if (name != null) {
                String plain = name.getString().trim().toLowerCase();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
        }
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("VariantId", 8)) {
                return tag.getString("VariantId");
            }
        }
        return "hachiroku";
    }

    private void updateLayoutPositions() {
        int leftPanelY = 38;
        int leftPanelH = this.height - leftPanelY - 14;
        int clipY = leftPanelY + 4;
        int clipH = leftPanelH - 8;

        int btnH = 18;
        int btnGap = 2;
        int sectionGap = 10;
        int headerH = 14;

        // コンテンツ全高: (見出し + 5ボタン) + 間隔 + (見出し + 5ボタン)
        int totalContentHeight = (headerH + TIRE_TYPES.length * (btnH + btnGap)) * 2 + sectionGap;
        this.maxScroll = Math.max(0, totalContentHeight - clipH);
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, this.maxScroll);

        int startY = clipY + 4 - (int) this.scrollAmount;

        // 前輪ボタン位置
        int curY = startY + headerH;
        for (int i = 0; i < this.frontTireButtons.size(); i++) {
            Button btn = this.frontTireButtons.get(i);
            btn.setY(curY + i * (btnH + btnGap));
            // スクロール領域外にあるボタンはクリックを無効化
            boolean visible = btn.getY() + btn.getHeight() > clipY && btn.getY() < clipY + clipH;
            btn.active = visible;
            btn.visible = visible;
        }

        // 後輪ボタン位置
        curY += TIRE_TYPES.length * (btnH + btnGap) + sectionGap + headerH;
        for (int i = 0; i < this.rearTireButtons.size(); i++) {
            Button btn = this.rearTireButtons.get(i);
            btn.setY(curY + i * (btnH + btnGap));
            boolean visible = btn.getY() + btn.getHeight() > clipY && btn.getY() < clipY + clipH;
            btn.active = visible;
            btn.visible = visible;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int leftPanelX = 18;
        int leftPanelY = 38;
        int leftPanelW = 118;
        int leftPanelH = this.height - leftPanelY - 14;

        // 左パネル上でマウスホイールが動いた場合は左パネルをスクロール
        if (mouseX >= leftPanelX && mouseX <= leftPanelX + leftPanelW &&
            mouseY >= leftPanelY && mouseY <= leftPanelY + leftPanelH) {
            if (this.maxScroll > 0) {
                this.scrollAmount = Mth.clamp(this.scrollAmount - scrollY * 18.0, 0.0, this.maxScroll);
                updateLayoutPositions();
                return true;
            }
        }

        // それ以外の領域では3Dモデルのズームイン・アウト
        this.previewZoom = Mth.clamp(this.previewZoom + (float) scrollY * 6.0F, 25.0F, 180.0F);
        this.lastInteractionTime = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ボタン等のウィジェットクリックを最優先で処理
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int leftPanelX = 18;
        int leftPanelY = 38;
        int leftPanelW = 118;
        int leftPanelH = this.height - leftPanelY - 14;

        // スクロールバーのクリック判定
        if (button == 0 && this.maxScroll > 0) {
            int barX = leftPanelX + leftPanelW - 6;
            if (mouseX >= barX - 2 && mouseX <= barX + 6 && mouseY >= leftPanelY && mouseY <= leftPanelY + leftPanelH) {
                this.isScrollingBar = true;
                return true;
            }
        }

        // ウィジェット以外の空間クリックで3Dカメラ操作開始
        if (button == 0) {
            this.isRotating = true;
            this.lastInteractionTime = System.currentTimeMillis();
            return true;
        }
        if (button == 1 || button == 2) {
            this.isPanning = true;
            this.lastInteractionTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isRotating = false;
            this.isScrollingBar = false;
            this.lastInteractionTime = System.currentTimeMillis();
        }
        if (button == 1 || button == 2) {
            this.isPanning = false;
            this.lastInteractionTime = System.currentTimeMillis();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrollingBar && this.maxScroll > 0) {
            int leftPanelY = 38;
            int leftPanelH = this.height - leftPanelY - 14;
            double ratio = dragY / (double) leftPanelH;
            this.scrollAmount = Mth.clamp(this.scrollAmount + ratio * this.maxScroll, 0.0, this.maxScroll);
            updateLayoutPositions();
            return true;
        }

        if (this.isRotating) {
            this.previewYaw += (float) dragX * 0.8F;
            this.previewPitch = Mth.clamp(this.previewPitch + (float) dragY * 0.8F, -85.0F, 85.0F);
            this.lastInteractionTime = System.currentTimeMillis();
            return true;
        }
        if (this.isPanning) {
            this.modelPanX += (float) dragX;
            this.modelPanY += (float) dragY;
            this.lastInteractionTime = System.currentTimeMillis();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // ショールーム自動回転の更新処理
        long now = System.currentTimeMillis();
        float deltaSec = (now - this.lastFrameTime) / 1000.0F;
        this.lastFrameTime = now;
        if (deltaSec > 0.1F) deltaSec = 0.1F;

        // ユーザーの手動操作中ではなく、かつ直近の操作/タイヤ変更から一定時間経過している場合に自動回転
        if (!this.isRotating && !this.isPanning && (now - this.lastInteractionTime) >= ROTATION_RESUME_DELAY_MS) {
            this.previewYaw = Mth.wrapDegrees(this.previewYaw + ROTATION_DEGREES_PER_SEC * deltaSec);
        }

        // 1. super.render を最初に実行 (背景メニューブラーと下地を最奥に描画)
        super.render(graphics, mouseX, mouseY, partialTick);

        // 2. 中央の3Dビューポート枠と背景パネル
        int viewMargin = 10;
        graphics.fillGradient(viewMargin, viewMargin, this.width - viewMargin, this.height - viewMargin, 0xEE090d16, 0xEE111827);
        graphics.renderOutline(viewMargin, viewMargin, this.width - viewMargin * 2, this.height - viewMargin * 2, 0xFF0284c7);

        // 3. ヘッダータイトル
        graphics.fill(viewMargin, viewMargin, this.width - viewMargin, viewMargin + 24, 0xFF0f172a);
        graphics.hLine(viewMargin, this.width - viewMargin, viewMargin + 24, 0xFF38bdf8);
        graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.title"), viewMargin + 10, viewMargin + 8, 0xFF38bdf8);

        // 4. 左側スクロールパネル
        renderLeftScrollPanel(graphics, mouseX, mouseY);

        // 5. 右側スペックパネル
        renderSpecPanel(graphics);

        // 6. 操作ヘルプ（画面下部・アングル初期化ボタンがヘッダーへ移動したため全幅で綺麗に中央配置）
        Component helpText = Component.translatable("gui.microdrone.pit_station.help");
        graphics.drawCenteredString(this.font, helpText, this.width / 2, this.height - 20, 0xFF94a3b8);

        // 7. 3Dモデルのレンダリング (中央に鮮明に描画)
        if (this.previewEntity != null) {
            int centerX = this.width / 2 + (int) this.modelPanX;
            int centerY = this.height / 2 + 10 + (int) this.modelPanY;

            // 車両下部に円形ショールームターンテーブル演出を描画
            renderPitPedestal(graphics, centerX, centerY + (int)(this.previewZoom * 0.42F));

            renderEntityInGui(graphics, centerX, centerY, this.previewZoom, this.previewYaw, this.previewPitch, this.previewEntity);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("gui.microdrone.pit_station.empty"), this.width / 2, this.height / 2, 0xFF64748b);
        }

        // 8. 前景ウィジェット（ボタン等）の最前面描画
        // 左パネルのボタンはシザー領域内で綺麗に描画
        int leftPanelX = 18;
        int leftPanelY = 38;
        int leftPanelW = 118;
        int leftPanelH = this.height - leftPanelY - 14;
        int clipY = leftPanelY + 4;
        int clipH = leftPanelH - 8;

        graphics.enableScissor(leftPanelX + 2, clipY, leftPanelX + leftPanelW - 2, clipY + clipH);
        for (Button btn : this.frontTireButtons) {
            if (btn.visible) {
                btn.render(graphics, mouseX, mouseY, partialTick);
                // 選択中ボタンのカラー枠ハイライト
                renderButtonColorGlow(graphics, btn, this.selectedFrontTire);
            }
        }
        for (Button btn : this.rearTireButtons) {
            if (btn.visible) {
                btn.render(graphics, mouseX, mouseY, partialTick);
                renderButtonColorGlow(graphics, btn, this.selectedRearTire);
            }
        }
        graphics.disableScissor();

        // 静的ボタン（アクションボタン、リセット、閉じる）
        if (this.saveButton != null) this.saveButton.render(graphics, mouseX, mouseY, partialTick);
        if (this.takeOutButton != null) this.takeOutButton.render(graphics, mouseX, mouseY, partialTick);
        if (this.resetButton != null) this.resetButton.render(graphics, mouseX, mouseY, partialTick);
        if (this.closeButton != null) this.closeButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderButtonColorGlow(GuiGraphics graphics, Button btn, String activeTireId) {
        int color = getTireColor(activeTireId);
        // ボタンのラベルにアクティブタイヤ名が含まれているか確認
        String msg = btn.getMessage().getString();
        if (msg.contains("▶")) {
            graphics.renderOutline(btn.getX() - 1, btn.getY() - 1, btn.getWidth() + 2, btn.getHeight() + 2, color);
        }
    }

    private void renderPitPedestal(GuiGraphics graphics, int centerX, int centerY) {
        int frontColor = getTireColor(this.selectedFrontTire);
        int rearColor = getTireColor(this.selectedRearTire);

        int radiusX = (int) (this.previewZoom * 0.62F);
        int radiusY = (int) (this.previewZoom * 0.20F);

        // 1. 最下層：ターンテーブルの影
        drawEllipse(graphics, centerX, centerY + 3, radiusX + 3, radiusY + 1, 0x66000000);

        // 2. ターンテーブル台座本体（円形メタリックステージ）
        drawEllipse(graphics, centerX, centerY, radiusX, radiusY, 0xDD0f172a);

        // 3. インナーリング
        int innerRx = (int) (radiusX * 0.68F);
        int innerRy = (int) (radiusY * 0.68F);
        drawEllipseOutline(graphics, centerX, centerY, innerRx, innerRy, 0x3338bdf8);

        // 4. 前後タイヤのテーマカラーネオングロー（左前側：前輪色、右後側：後輪色）
        int neonAlpha = 0xAA000000;
        int fNeon = (frontColor & 0x00FFFFFF) | neonAlpha;
        int rNeon = (rearColor & 0x00FFFFFF) | neonAlpha;

        graphics.fillGradient(centerX - radiusX + 6, centerY - 2, centerX - 2, centerY + 3,
                (frontColor & 0x00FFFFFF) | 0x11000000, fNeon);
        graphics.fillGradient(centerX + 2, centerY - 2, centerX + radiusX - 6, centerY + 3,
                rNeon, (rearColor & 0x00FFFFFF) | 0x11000000);

        // 5. 外周ネオンフレームリング
        drawEllipseOutline(graphics, centerX, centerY, radiusX, radiusY, 0x7738bdf8);
    }

    private void drawEllipse(GuiGraphics graphics, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        for (int y = -ry; y <= ry; y++) {
            float normY = (float) y / (float) ry;
            int spanX = (int) (Math.sqrt(Math.max(0.0, 1.0 - normY * normY)) * rx);
            graphics.fill(cx - spanX, cy + y, cx + spanX + 1, cy + y + 1, color);
        }
    }

    private void drawEllipseOutline(GuiGraphics graphics, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        for (int y = -ry; y <= ry; y++) {
            float normY = (float) y / (float) ry;
            int spanX = (int) (Math.sqrt(Math.max(0.0, 1.0 - normY * normY)) * rx);
            if (spanX > 0) {
                graphics.fill(cx - spanX, cy + y, cx - spanX + 1, cy + y + 1, color);
                graphics.fill(cx + spanX, cy + y, cx + spanX + 1, cy + y + 1, color);
            }
        }
    }

    private void renderLeftScrollPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int leftPanelX = 18;
        int leftPanelY = 38;
        int leftPanelW = 118;
        int leftPanelH = this.height - leftPanelY - 14;
        int clipY = leftPanelY + 4;
        int clipH = leftPanelH - 8;

        // パネル背景
        graphics.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xAA0b1329);
        graphics.renderOutline(leftPanelX, leftPanelY, leftPanelW, leftPanelH, 0x550284c7);

        // シザー領域内でセクションタイトルを描画
        graphics.enableScissor(leftPanelX + 2, clipY, leftPanelX + leftPanelW - 2, clipY + clipH);

        int startY = clipY + 4 - (int) this.scrollAmount;
        int frontHeaderY = startY;
        if (frontHeaderY + 10 >= clipY && frontHeaderY <= clipY + clipH) {
            int fColor = getTireColor(this.selectedFrontTire);
            graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.front_tires"), leftPanelX + 6, frontHeaderY, fColor);
        }

        int rearHeaderY = startY + 14 + TIRE_TYPES.length * 20 + 8;
        if (rearHeaderY + 10 >= clipY && rearHeaderY <= clipY + clipH) {
            int rColor = getTireColor(this.selectedRearTire);
            graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.rear_tires"), leftPanelX + 6, rearHeaderY, rColor);
        }

        graphics.disableScissor();

        // スクロールバー描画 (スクロール可能な場合)
        if (this.maxScroll > 0) {
            int barTrackX = leftPanelX + leftPanelW - 4;
            int barTrackY = clipY;
            int barTrackH = clipH;

            graphics.fill(barTrackX, barTrackY, barTrackX + 2, barTrackY + barTrackH, 0x44000000);

            int thumbH = Math.max(16, (int) ((float) clipH / (clipH + this.maxScroll) * clipH));
            int thumbY = barTrackY + (int) ((float) this.scrollAmount / this.maxScroll * (barTrackH - thumbH));

            int thumbColor = this.isScrollingBar ? 0xFF38bdf8 : 0xAA0284c7;
            graphics.fill(barTrackX - 1, thumbY, barTrackX + 3, thumbY + thumbH, thumbColor);
        }
    }

    private void renderSpecPanel(GuiGraphics graphics) {
        int panelX = this.width - 146;
        int panelY = 38;
        int panelW = 132;
        int panelH = this.height - panelY - 14;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA0b1329);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0x550284c7);

        // 車両タイトル
        Component name = this.vehicleStack.isEmpty() ? Component.literal("No Vehicle") : this.vehicleStack.getHoverName();
        graphics.drawString(this.font, name, panelX + 6, panelY + 6, 0xFFFFFFFF);

        String variant = this.decalHolder != null ? this.decalHolder.getVariantId() : "standard";
        graphics.drawString(this.font, Component.literal("Model: " + variant), panelX + 6, panelY + 18, 0xFF38bdf8);

        graphics.hLine(panelX + 4, panelX + panelW - 4, panelY + 28, 0x4438bdf8);

        // 装着タイヤのカラーバッジ情報
        int fColor = getTireColor(this.selectedFrontTire);
        int rColor = getTireColor(this.selectedRearTire);

        int badgeY = panelY + 32;
        graphics.fill(panelX + 6, badgeY + 1, panelX + 11, badgeY + 6, fColor);
        graphics.drawString(this.font, Component.literal("F: " + this.selectedFrontTire.toUpperCase()), panelX + 14, badgeY, fColor);

        graphics.fill(panelX + 68, badgeY + 1, panelX + 73, badgeY + 6, rColor);
        graphics.drawString(this.font, Component.literal("R: " + this.selectedRearTire.toUpperCase()), panelX + 76, badgeY, rColor);

        graphics.hLine(panelX + 4, panelX + panelW - 4, badgeY + 11, 0x3338bdf8);

        // スペック評価バーの計算
        RcTireItem.TireType fType = getTireTypeFromId(this.selectedFrontTire);
        RcTireItem.TireType rType = getTireTypeFromId(this.selectedRearTire);

        // 1. 回頭性 (前輪グリップ70% + 後輪30%)
        float agilityScore = (fType.getLateralGripMultiplier() * 0.70f + rType.getLateralGripMultiplier() * 0.30f) / 3.08f;
        // 2. 加速トラクション (後輪75% + 前輪25%)
        float tractionScore = (rType.getTractionMultiplier() * 0.75f + fType.getTractionMultiplier() * 0.25f) / 1.96f;
        // 3. ドリフト性 (後輪の低スクラブ抵抗 & 前輪グリップによる回頭誘導)
        float driftScore = (1.5f - rType.getScrubDragMultiplier() * 0.5f) / 1.5f;
        if (rType == RcTireItem.TireType.DRIFT) driftScore = 1.0f;
        // 4. 悪路/氷上走破性
        float weatherScore = 0.2f;
        if (fType == RcTireItem.TireType.OFFROAD || rType == RcTireItem.TireType.OFFROAD) weatherScore += 0.4f;
        if (fType == RcTireItem.TireType.OFFROAD && rType == RcTireItem.TireType.OFFROAD) weatherScore += 0.4f;
        if (fType == RcTireItem.TireType.SPIKE || rType == RcTireItem.TireType.SPIKE) weatherScore += 0.3f;
        weatherScore = Mth.clamp(weatherScore, 0.1f, 1.0f);

        int barY = badgeY + 15;
        int barW = panelW - 12;
        renderSpecBar(graphics, panelX + 6, barY, barW, Component.translatable("gui.microdrone.pit_station.spec_agility"), agilityScore, 0xFF38bdf8);
        renderSpecBar(graphics, panelX + 6, barY + 20, barW, Component.translatable("gui.microdrone.pit_station.spec_traction"), tractionScore, 0xFF4ade80);
        renderSpecBar(graphics, panelX + 6, barY + 40, barW, Component.translatable("gui.microdrone.pit_station.spec_drift"), driftScore, 0xFFf472b6);
        renderSpecBar(graphics, panelX + 6, barY + 60, barW, Component.translatable("gui.microdrone.pit_station.spec_allweather"), weatherScore, 0xFFfacc15);

        // アクションボタンの上部境界線
        int saveBtnY = panelY + panelH - 52;
        graphics.hLine(panelX + 4, panelX + panelW - 4, saveBtnY, 0x4438bdf8);
    }

    private void renderSpecBar(GuiGraphics graphics, int x, int y, int width, Component label, float ratio, int color) {
        graphics.drawString(this.font, label, x, y, 0xFFcbd5e1);
        int barH = 4;
        int barY = y + 9;
        graphics.fill(x, barY, x + width, barY + barH, 0xFF1e293b);
        int fillW = (int) (width * Mth.clamp(ratio, 0.05f, 1.0f));
        graphics.fill(x, barY, x + fillW, barY + barH, color);
    }

    private static int getTireColor(String id) {
        for (int i = 0; i < TIRE_TYPES.length; i++) {
            if (TIRE_TYPES[i].equalsIgnoreCase(id)) {
                return TIRE_COLORS[i];
            }
        }
        return 0xFFe2e8f0;
    }

    private static RcTireItem.TireType getTireTypeFromId(String id) {
        for (RcTireItem.TireType t : RcTireItem.TireType.values()) {
            if (t.getId().equalsIgnoreCase(id)) return t;
        }
        return RcTireItem.TireType.NORMAL;
    }

    /**
     * GUI内にGeckoLibエンティティを3D描画
     */
    private static void renderEntityInGui(GuiGraphics graphics, int centerX, int centerY, float scale, float yaw, float pitch, Entity entity) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 150.0);
        poseStack.scale(scale, -scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

        RenderSystem.enableDepthTest();

        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, poseStack, graphics.bufferSource(), 15728880);
        graphics.flush();
        dispatcher.setRenderShadow(true);

        RenderSystem.disableDepthTest();
        poseStack.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
