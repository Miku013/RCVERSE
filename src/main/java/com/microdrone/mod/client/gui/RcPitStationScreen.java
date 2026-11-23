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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * 360度自由回転・上下ピッチ・ズーム・パンによる自由な3Dプレビューと、
 * 前後タイヤ換装・セッティング機能を提供します。
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

    // 3Dカメラ・視点パラメータ
    private float previewYaw = -45.0F;     // 左右回転（初期値: 斜め45度）
    private float previewPitch = 15.0F;    // 上下角度（初期値: やや見下ろし15度）
    private float previewZoom = 80.0F;     // 拡大率
    private float modelPanX = 0.0F;        // 平行移動 X
    private float modelPanY = 0.0F;        // 平行移動 Y

    // マウスドラッグ状態
    private boolean isRotating = false;
    private boolean isPanning = false;

    private static final String[] TIRE_TYPES = {"normal", "drift", "grip", "offroad", "spike"};
    private static final String[] TIRE_NAMES = {"NORMAL", "DRIFT", "GRIP", "OFFROAD", "SPIKE"};
    private static final int[] TIRE_COLORS = {0xFFaaaaaa, 0xFF55ffff, 0xFFffaa00, 0xFF55ff55, 0xFFff55ff};

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

        // 左側：タイヤカスタムパネル
        int leftX = 22;
        int topY = 48;
        int btnW = 105;
        int btnH = 17;

        this.frontTireButtons.clear();
        for (int i = 0; i < TIRE_TYPES.length; i++) {
            final String tireId = TIRE_TYPES[i];
            final int idx = i;
            Button btn = Button.builder(
                    getTireButtonLabel("F: ", idx, tireId.equals(this.selectedFrontTire)),
                    button -> {
                        this.selectedFrontTire = tireId;
                        updateTireButtons();
                        applyTiresToPreview();
                    }
            ).bounds(leftX, topY + 12 + i * (btnH + 2), btnW, btnH).build();
            this.frontTireButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        int rearTopY = topY + 12 + TIRE_TYPES.length * (btnH + 2) + 10;
        this.rearTireButtons.clear();
        for (int i = 0; i < TIRE_TYPES.length; i++) {
            final String tireId = TIRE_TYPES[i];
            final int idx = i;
            Button btn = Button.builder(
                    getTireButtonLabel("R: ", idx, tireId.equals(this.selectedRearTire)),
                    button -> {
                        this.selectedRearTire = tireId;
                        updateTireButtons();
                        applyTiresToPreview();
                    }
            ).bounds(leftX, rearTopY + 12 + i * (btnH + 2), btnW, btnH).build();
            this.rearTireButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        // 右側：アクションボタン
        int rightX = this.width - 138;
        int actionY = this.height - 76;

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.microdrone.pit_station.save"),
                button -> saveTireSettings(false)
        ).bounds(rightX, actionY, 116, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.microdrone.pit_station.take_out"),
                button -> saveTireSettings(true)
        ).bounds(rightX, actionY + 24, 116, 20).build());

        // アングル初期化ボタン（左下に配置）
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.microdrone.pit_station.reset"),
                button -> resetCamera()
        ).bounds(22, this.height - 34, 105, 18).build());

        // 閉じるボタン（右上に配置）
        this.addRenderableWidget(Button.builder(
                Component.literal("✕"),
                button -> this.onClose()
        ).bounds(this.width - 32, 10, 22, 20).build());
    }

    private Component getTireButtonLabel(String prefix, int idx, boolean isSelected) {
        String base = prefix + TIRE_NAMES[idx];
        if (isSelected) {
            return Component.literal("▶ " + base + " ◀");
        }
        return Component.literal("  " + base);
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
            this.onClose();
        }
    }

    private void resetCamera() {
        this.previewYaw = -45.0F;
        this.previewPitch = 15.0F;
        this.previewZoom = 80.0F;
        this.modelPanX = 0.0F;
        this.modelPanY = 0.0F;
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
            this.previewZoom = 80.0F;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ボタン等のウィジェットクリックを最優先で処理
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // ウィジェット以外の空間クリックで3Dカメラ操作開始
        if (button == 0) {
            this.isRotating = true;
            return true;
        }
        if (button == 1 || button == 2) {
            this.isPanning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isRotating = false;
        }
        if (button == 1 || button == 2) {
            this.isPanning = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isRotating) {
            this.previewYaw += (float) dragX * 0.8F;
            this.previewPitch = Mth.clamp(this.previewPitch + (float) dragY * 0.8F, -85.0F, 85.0F);
            return true;
        }
        if (this.isPanning) {
            this.modelPanX += (float) dragX;
            this.modelPanY += (float) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // マウスホイールによるズームイン・アウト
        this.previewZoom = Mth.clamp(this.previewZoom + (float) scrollY * 6.0F, 25.0F, 180.0F);
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. super.render を最初に実行 (背景メニューブラーと下地を最奥に描画)
        super.render(graphics, mouseX, mouseY, partialTick);

        // 2. 中央の3Dビューポート枠と背景パネル
        int viewMargin = 10;
        graphics.fillGradient(viewMargin, viewMargin, this.width - viewMargin, this.height - viewMargin, 0xEE090d16, 0xEE111827);
        graphics.renderOutline(viewMargin, viewMargin, this.width - viewMargin * 2, this.height - viewMargin * 2, 0xFF0284c7);

        // 3. ヘッダータイトル
        graphics.fill(viewMargin, viewMargin, this.width - viewMargin, viewMargin + 26, 0xFF0f172a);
        graphics.hLine(viewMargin, this.width - viewMargin, viewMargin + 26, 0xFF38bdf8);
        graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.title"), viewMargin + 10, viewMargin + 9, 0xFF38bdf8);

        // 4. 左側パネル背景 & セクションタイトル
        int leftPanelX = 16;
        int leftPanelY = 40;
        int leftPanelW = 117;
        int leftPanelH = this.height - leftPanelY - 40;
        graphics.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, 0xAA0b1329);
        graphics.renderOutline(leftPanelX, leftPanelY, leftPanelW, leftPanelH, 0x550284c7);

        graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.front_tires"), leftPanelX + 6, leftPanelY + 6, 0xFF7dd3fc);
        int rearLabelY = leftPanelY + 6 + 12 + TIRE_TYPES.length * 19 + 6;
        graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.rear_tires"), leftPanelX + 6, rearLabelY, 0xFF7dd3fc);

        // 5. 右側スペックパネル
        renderSpecPanel(graphics);

        // 6. 操作ヘルプ（画面下部）
        Component helpText = Component.translatable("gui.microdrone.pit_station.help");
        graphics.drawCenteredString(this.font, helpText, this.width / 2, this.height - 24, 0xFF94a3b8);

        // 7. 3Dモデルのレンダリング (中央に鮮明に描画)
        if (this.previewEntity != null) {
            int centerX = this.width / 2 + (int) this.modelPanX;
            int centerY = this.height / 2 + 10 + (int) this.modelPanY;
            renderEntityInGui(graphics, centerX, centerY, this.previewZoom, this.previewYaw, this.previewPitch, this.previewEntity);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("gui.microdrone.pit_station.empty"), this.width / 2, this.height / 2, 0xFF64748b);
        }

        // 8. 前景ウィジェット（ボタン等）の最前面描画
        for (var renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderSpecPanel(GuiGraphics graphics) {
        int panelX = this.width - 144;
        int panelY = 40;
        int panelW = 128;
        int panelH = this.height - panelY - 40;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xAA0b1329);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0x550284c7);

        // 車両タイトル
        Component name = this.vehicleStack.isEmpty() ? Component.literal("No Vehicle") : this.vehicleStack.getHoverName();
        graphics.drawString(this.font, name, panelX + 6, panelY + 6, 0xFFFFFFFF);

        String variant = this.decalHolder != null ? this.decalHolder.getVariantId() : "standard";
        graphics.drawString(this.font, Component.literal("Model: " + variant), panelX + 6, panelY + 18, 0xFF38bdf8);

        graphics.hLine(panelX + 4, panelX + panelW - 4, panelY + 30, 0x4438bdf8);

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

        int barY = panelY + 36;
        renderSpecBar(graphics, panelX + 6, barY, panelW - 12, Component.translatable("gui.microdrone.pit_station.spec_agility"), agilityScore, 0xFF38bdf8);
        renderSpecBar(graphics, panelX + 6, barY + 22, panelW - 12, Component.translatable("gui.microdrone.pit_station.spec_traction"), tractionScore, 0xFF4ade80);
        renderSpecBar(graphics, panelX + 6, barY + 44, panelW - 12, Component.translatable("gui.microdrone.pit_station.spec_drift"), driftScore, 0xFFf472b6);
        renderSpecBar(graphics, panelX + 6, barY + 66, panelW - 12, Component.translatable("gui.microdrone.pit_station.spec_allweather"), weatherScore, 0xFFfacc15);
    }

    private void renderSpecBar(GuiGraphics graphics, int x, int y, int width, Component label, float ratio, int color) {
        graphics.drawString(this.font, label, x, y, 0xFFcbd5e1);
        int barH = 5;
        int barY = y + 10;
        graphics.fill(x, barY, x + width, barY + barH, 0xFF1e293b);
        int fillW = (int) (width * Mth.clamp(ratio, 0.05f, 1.0f));
        graphics.fill(x, barY, x + fillW, barY + barH, color);
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
