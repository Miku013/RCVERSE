package com.microdrone.mod.client.gui;

import com.microdrone.mod.block.entity.DecalTableBlockEntity;
import com.microdrone.mod.client.decal.DecalTextureManager;
import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.network.UpdateDecalsPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * デカール作業台の専用3Dリバリーエディター画面 (GUI)
 * マウスドラッグで車体を360°回転させながら、透過PNGステッカーの位置・回転・サイズをリアルタイム編集可能
 */
public class DecalEditorScreen extends Screen {

    private final DecalTableBlockEntity table;
    private final ItemStack carStack;
    private RcCarEntity previewCar;

    // 編集中のデカールリスト
    private final List<CarDecalData> editingDecals = new ArrayList<>();
    private int selectedDecalIndex = 0;

    // 利用可能なステッカーファイル一覧
    private List<String> availableStickers = new ArrayList<>();
    private int selectedStickerFileIndex = 0;

    // 3Dプレビュー操作パラメータ
    private float previewYaw = -45.0f;
    private float previewPitch = 20.0f;
    private float previewZoom = 80.0f;
    private float modelPanX = 0.0f;
    private float modelPanY = 0.0f;
    private boolean isDragging = false;
    private boolean isDraggingDecal = false;
    private boolean isPanningModel = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    // UIコントロール
    private Button prevStickerBtn;
    private Button nextStickerBtn;
    private Button prevDecalLayerBtn;
    private Button nextDecalLayerBtn;
    private Button addDecalBtn;
    private Button removeDecalBtn;
    private FloatSlider posXSlider;
    private FloatSlider posYSlider;
    private FloatSlider posZSlider;
    private FloatSlider rotXSlider;
    private FloatSlider rotYSlider;
    private FloatSlider rotSlider;
    private FloatSlider scaleSlider;
    private FloatSlider aspectSlider;
    private Checkbox mirrorCheckbox;

    // スクロール管理
    private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();
    private final List<Integer> scrollableBaseY = new ArrayList<>();
    private float panelScrollY = 0.0f;
    private float maxScrollY = 0.0f;

    public DecalEditorScreen(DecalTableBlockEntity table) {
        this(table, null);
    }

    public DecalEditorScreen(DecalTableBlockEntity table, ItemStack initialCarStack) {
        super(Component.translatable("gui.microdrone.decal_editor.title"));
        this.table = table;
        if (initialCarStack != null && !initialCarStack.isEmpty()) {
            this.carStack = initialCarStack.copy();
        } else if (table != null && !table.getStoredCar().isEmpty()) {
            this.carStack = table.getStoredCar().copy();
        } else {
            this.carStack = ItemStack.EMPTY;
        }
    }

    @Override
    protected void init() {
        super.init();

        // 外部車種設定 (JSON) を即座にリロード検知 (F3+T なしで最新設定を反映)
        com.microdrone.mod.config.car.CarConfigManager.loadConfigs();

        // 1. ダミーRCカーの生成（プレビュー用）
        if (this.minecraft != null && this.minecraft.level != null) {
            this.previewCar = ModEntities.RC_CAR.get().create(this.minecraft.level);
            if (this.previewCar != null) {
                var customData = this.carStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("Variant")) {
                        this.previewCar.setVariantId(tag.getString("Variant"));
                    }
                    if (tag.contains("BodyColor")) {
                        this.previewCar.setBodyColor(tag.getInt("BodyColor"));
                    }
                    if (tag.contains("Decals", 10)) {
                        List<CarDecalData> loaded = RcCarEntity.deserializeDecals(tag.getCompound("Decals"));
                        this.editingDecals.clear();
                        for (CarDecalData d : loaded) {
                            this.editingDecals.add(d.copy());
                        }
                    }
                }
            }
        }

        // デカールが空の場合は初期デカールを1枚用意 (車種別の最適位置)
        this.availableStickers = DecalTextureManager.getAvailableStickerFiles();
        if (this.editingDecals.isEmpty()) {
            String initialTex = this.availableStickers.isEmpty() ? "sample_racing_43.png" : this.availableStickers.get(0);
            boolean isTruck = this.previewCar != null && "truck".equalsIgnoreCase(this.previewCar.getVariantId());
            float naturalAspect = DecalTextureManager.getImageAspectRatio(initialTex);
            CarDecalData first;
            if (isTruck) {
                first = new CarDecalData(initialTex, "body", 0.0f, 26.2f, 14.0f, -90.0f, 0.0f, 0.0f, 7.0f, naturalAspect);
            } else {
                first = new CarDecalData(initialTex, "body", 0.0f, 10.5f, -34.0f, -80.0f, 0.0f, 0.0f, 6.0f, naturalAspect);
            }
            this.editingDecals.add(first);
        }
        this.selectedDecalIndex = Mth.clamp(this.selectedDecalIndex, 0, this.editingDecals.size() - 1);

        // 2. UIコントロールの配置 (画面右側エリア: panelX)
        int panelWidth = 155;
        int panelX = this.width - panelWidth - 10;
        int y = 22;

        // --- [固定ヘッダー] ステッカー画像選択 ---
        this.prevStickerBtn = Button.builder(Component.literal("◀"), b -> cycleSticker(-1))
                .bounds(panelX, y, 22, 18).build();
        this.nextStickerBtn = Button.builder(Component.literal("▶"), b -> cycleSticker(1))
                .bounds(panelX + panelWidth - 22, y, 22, 18).build();
        this.addRenderableWidget(this.prevStickerBtn);
        this.addRenderableWidget(this.nextStickerBtn);
        y += 22;

        // --- [固定ヘッダー] 部位プリセットボタン ---
        int pw = (panelWidth - 4) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.part_hood"), b -> applyPreset("HOOD"))
                .bounds(panelX, y, pw, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.part_roof"), b -> applyPreset("ROOF"))
                .bounds(panelX + pw + 2, y, pw, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.part_trunk"), b -> applyPreset("TRUNK"))
                .bounds(panelX + (pw + 2) * 2, y, pw, 18).build());
        y += 20;

        int pw2 = (panelWidth - 2) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.part_door_l"), b -> applyPreset("DOOR_L"))
                .bounds(panelX, y, pw2, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.part_door_r"), b -> applyPreset("DOOR_R"))
                .bounds(panelX + pw2 + 2, y, pw2, 18).build());
        y += 22;

        // --- [スクロール可能エリア] スライダー群 & 反転チェックボックス ---
        this.scrollableWidgets.clear();
        this.scrollableBaseY.clear();
        int scrollStartY = y;

        CarDecalData cur = getCurrentDecal();
        float curX = cur != null ? cur.getPosX() : 0.0f;
        float curY = cur != null ? cur.getPosY() : 10.5f;
        float curZ = cur != null ? cur.getPosZ() : -34.0f;
        float curRotX = cur != null ? cur.getRotX() : -80.0f;
        float curRotY = cur != null ? cur.getRotY() : 0.0f;
        float curRot = cur != null ? cur.getRotZ() : 0.0f;
        float curScale = cur != null ? cur.getScale() : 6.0f;
        float curAspect = cur != null ? cur.getAspect() : 1.0f;

        this.posXSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.literal("X (左右)"), curX, -30.0f, 30.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setPosX(val);
        }), y);
        y += 18;

        this.posYSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.literal("Y (高さ)"), curY, 0.0f, 50.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setPosY(val);
        }), y);
        y += 18;

        this.posZSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.literal("Z (前後)"), curZ, -55.0f, 55.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setPosZ(val);
        }), y);
        y += 18;

        this.rotXSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.literal("傾き (ピッチ)"), curRotX, -180.0f, 180.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setRotX(val);
        }), y);
        y += 18;

        this.rotYSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.translatable("gui.microdrone.decal.rot_y"), curRotY, -180.0f, 180.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setRotY(val);
        }), y);
        y += 18;

        this.rotSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.literal("回転 (面内)"), curRot, -180.0f, 180.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setRotZ(val);
        }), y);
        y += 18;

        this.scaleSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.literal("サイズ"), curScale, 1.0f, 20.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setScale(val);
        }), y);
        y += 18;

        this.aspectSlider = addScrollable(new FloatSlider(panelX, y, panelWidth, 16, Component.translatable("gui.microdrone.decal.aspect"), curAspect, 0.2f, 5.0f, val -> {
            if (getCurrentDecal() != null) getCurrentDecal().setAspect(val);
        }), y);
        y += 18;

        // 左右反転チェックボックス
        boolean curMirror = cur != null && cur.isMirrorX();
        this.mirrorCheckbox = addScrollable(Checkbox.builder(Component.translatable("gui.microdrone.decal.mirror"), this.font)
                .pos(panelX, y)
                .selected(curMirror)
                .onValueChange((cb, val) -> {
                    if (getCurrentDecal() != null) getCurrentDecal().setMirrorX(val);
                })
                .build(), y);
        y += 20;

        // スクロール可能範囲の計算
        int bottomFooterStart = this.height - 84;
        int contentHeight = y - scrollStartY;
        int viewHeight = bottomFooterStart - scrollStartY;
        this.maxScrollY = Math.max(0.0f, contentHeight - viewHeight);

        // --- [固定フッター] ステッカーレイヤー管理 (追加・削除・切替) ---
        int layerY = this.height - 66;
        this.prevDecalLayerBtn = Button.builder(Component.literal("◀ 前"), b -> selectDecalLayer(-1))
                .bounds(panelX, layerY, 36, 18).build();
        this.addDecalBtn = Button.builder(Component.literal("+ 追加"), b -> addNewDecal())
                .bounds(panelX + 38, layerY, 38, 18).build();
        this.removeDecalBtn = Button.builder(Component.literal("- 削除"), b -> removeCurrentDecal())
                .bounds(panelX + 78, layerY, 38, 18).build();
        this.nextDecalLayerBtn = Button.builder(Component.literal("次 ▶"), b -> selectDecalLayer(1))
                .bounds(panelX + panelWidth - 36, layerY, 36, 18).build();

        this.addRenderableWidget(this.prevDecalLayerBtn);
        this.addRenderableWidget(this.addDecalBtn);
        this.addRenderableWidget(this.removeDecalBtn);
        this.addRenderableWidget(this.nextDecalLayerBtn);

        // --- [固定フッター] 保存・回収・キャンセルボタン ---
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.save_and_take"), b -> save(true))
                .bounds(panelX, this.height - 44, panelWidth, 18).build());

        int halfBtnWidth = (panelWidth - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.microdrone.decal.save"), b -> save(false))
                .bounds(panelX, this.height - 24, halfBtnWidth, 18).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(panelX + halfBtnWidth + 4, this.height - 24, halfBtnWidth, 18).build());

        updateScrollablePositions();
        syncSlidersFromCurrentDecal();
    }

    private <T extends AbstractWidget> T addScrollable(T widget, int baseY) {
        this.scrollableWidgets.add(widget);
        this.scrollableBaseY.add(baseY);
        this.addRenderableWidget(widget);
        return widget;
    }

    private void updateScrollablePositions() {
        int topClip = 84;
        int bottomClip = this.height - 84;
        for (int i = 0; i < this.scrollableWidgets.size(); i++) {
            AbstractWidget widget = this.scrollableWidgets.get(i);
            int curY = this.scrollableBaseY.get(i) - (int) this.panelScrollY;
            widget.setY(curY);
            widget.visible = (curY + widget.getHeight() >= topClip && curY <= bottomClip);
        }

        syncSlidersFromCurrentDecal();
    }

    private CarDecalData getCurrentDecal() {
        if (this.editingDecals.isEmpty()) return null;
        if (this.selectedDecalIndex < 0 || this.selectedDecalIndex >= this.editingDecals.size()) {
            this.selectedDecalIndex = 0;
        }
        return this.editingDecals.get(this.selectedDecalIndex);
    }

    private void cycleSticker(int direction) {
        if (this.availableStickers.isEmpty()) return;
        this.selectedStickerFileIndex = (this.selectedStickerFileIndex + direction + this.availableStickers.size()) % this.availableStickers.size();
        String selected = this.availableStickers.get(this.selectedStickerFileIndex);
        CarDecalData cur = getCurrentDecal();
        if (cur != null) {
            cur.setTextureName(selected);
            // 元画像のアスペクト比 (幅 / 高さ) を自動検出して反映
            float naturalAspect = DecalTextureManager.getImageAspectRatio(selected);
            cur.setAspect(naturalAspect);
            syncSlidersFromCurrentDecal();
        }
    }

    private void selectDecalLayer(int direction) {
        if (this.editingDecals.isEmpty()) return;
        this.selectedDecalIndex = (this.selectedDecalIndex + direction + this.editingDecals.size()) % this.editingDecals.size();
        syncSlidersFromCurrentDecal();
    }

    private void addNewDecal() {
        if (this.editingDecals.size() >= 8) return; // 最大8枚まで
        CarDecalData cur = getCurrentDecal();
        CarDecalData newDecal = cur != null ? cur.copy() : new CarDecalData();
        newDecal.setPosZ(newDecal.getPosZ() - 2.0f); // 重なりを避けるため少しずらす
        this.editingDecals.add(newDecal);
        this.selectedDecalIndex = this.editingDecals.size() - 1;
        syncSlidersFromCurrentDecal();
    }

    private void removeCurrentDecal() {
        if (this.editingDecals.size() <= 1) return; // 最低1枚は残す
        this.editingDecals.remove(this.selectedDecalIndex);
        if (this.selectedDecalIndex >= this.editingDecals.size()) {
            this.selectedDecalIndex = this.editingDecals.size() - 1;
        }
        syncSlidersFromCurrentDecal();
    }

    private void syncSlidersFromCurrentDecal() {
        CarDecalData cur = getCurrentDecal();
        if (cur == null) return;
        if (this.posXSlider != null) this.posXSlider.setFloatValue(cur.getPosX());
        if (this.posYSlider != null) this.posYSlider.setFloatValue(cur.getPosY());
        if (this.posZSlider != null) this.posZSlider.setFloatValue(cur.getPosZ());
        if (this.rotXSlider != null) this.rotXSlider.setFloatValue(cur.getRotX());
        if (this.rotYSlider != null) this.rotYSlider.setFloatValue(cur.getRotY());
        if (this.rotSlider != null) this.rotSlider.setFloatValue(cur.getRotZ());
        if (this.scaleSlider != null) this.scaleSlider.setFloatValue(cur.getScale());
        if (this.aspectSlider != null) this.aspectSlider.setFloatValue(cur.getAspect());
        if (this.mirrorCheckbox != null && this.mirrorCheckbox.selected() != cur.isMirrorX()) {
            this.mirrorCheckbox.onPress();
        }

        // 現在のステッカーファイル名とインデックスを同期
        int idx = this.availableStickers.indexOf(cur.getTextureName());
        if (idx != -1) {
            this.selectedStickerFileIndex = idx;
        }
    }

    private void applyPreset(String part) {
        CarDecalData cur = getCurrentDecal();
        if (cur == null || this.previewCar == null) return;
        boolean isTruck = "truck".equalsIgnoreCase(this.previewCar.getVariantId());

        switch (part) {
            case "HOOD" -> { // ボンネット
                cur.setPosX(0.0f);
                cur.setPosY(isTruck ? 26.2f : 10.5f);
                cur.setPosZ(isTruck ? 14.0f : -34.0f);
                cur.setRotX(isTruck ? -90.0f : -80.0f);
                cur.setRotY(0.0f);
                cur.setRotZ(0.0f);
                cur.setScale(isTruck ? 7.0f : 6.0f);
                cur.setMirrorX(false);
            }
            case "ROOF" -> { // ルーフ
                cur.setPosX(0.0f);
                cur.setPosY(isTruck ? 36.2f : 22.0f);
                cur.setPosZ(isTruck ? 4.0f : 9.4f);
                cur.setRotX(-90.0f);
                cur.setRotY(0.0f);
                cur.setRotZ(0.0f);
                cur.setScale(isTruck ? 7.0f : 6.0f);
                cur.setMirrorX(false);
            }
            case "DOOR_L" -> { // 左ドア
                cur.setPosX(isTruck ? -9.2f : -13.2f);
                cur.setPosY(isTruck ? 20.0f : 12.5f);
                cur.setPosZ(isTruck ? 4.0f : 4.5f);
                cur.setRotX(0.0f);
                cur.setRotY(-90.0f);
                cur.setRotZ(0.0f);
                cur.setScale(isTruck ? 6.0f : 5.0f);
                cur.setMirrorX(false);
            }
            case "DOOR_R" -> { // 右ドア
                cur.setPosX(isTruck ? 9.2f : 13.2f);
                cur.setPosY(isTruck ? 20.0f : 12.5f);
                cur.setPosZ(isTruck ? 4.0f : 4.5f);
                cur.setRotX(0.0f);
                cur.setRotY(90.0f);
                cur.setRotZ(0.0f);
                cur.setScale(isTruck ? 6.0f : 5.0f);
                cur.setMirrorX(true); // 右ドアはUV反転
            }
            case "TRUNK" -> { // トランク / リア
                cur.setPosX(0.0f);
                cur.setPosY(isTruck ? 22.0f : 16.2f);
                cur.setPosZ(isTruck ? -14.0f : 31.0f);
                cur.setRotX(isTruck ? -90.0f : -90.0f);
                cur.setRotY(0.0f);
                cur.setRotZ(0.0f);
                cur.setScale(isTruck ? 6.0f : 5.0f);
                cur.setMirrorX(false);
            }
        }
        syncSlidersFromCurrentDecal();
    }

    private void save(boolean takeOut) {
        CompoundTag decalsTag = RcCarEntity.serializeDecals(this.editingDecals);
        PacketDistributor.sendToServer(new UpdateDecalsPayload(this.table.getBlockPos(), decalsTag, takeOut));
        if (takeOut && this.table != null) {
            this.table.setStoredCar(ItemStack.EMPTY);
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 左側の3Dビューポート内での操作判定
        int panelWidth = 155;
        int panelX = this.width - panelWidth - 10;
        int viewportWidth = panelX - 10;
        if (mouseX >= 10 && mouseX <= viewportWidth && mouseY >= 10 && mouseY <= this.height - 10) {
            if (button == 1) {
                // 右クリックドラッグ: 車両モデル本体の上下左右移動（パン）
                this.isPanningModel = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                return true;
            } else if (button == 0) {
                ScreenPos pos = getDecalScreenPos();
                boolean hitDecal = false;
                if (pos != null) {
                    double distSq = (mouseX - pos.x) * (mouseX - pos.x) + (mouseY - pos.y) * (mouseY - pos.y);
                    if (distSq <= pos.radius * pos.radius) {
                        hitDecal = true;
                    }
                }

                if (hitDecal || hasShiftDown()) {
                    this.isDraggingDecal = true;
                } else {
                    this.isDragging = true;
                }
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
            this.isDraggingDecal = false;
        } else if (button == 1) {
            this.isPanningModel = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isPanningModel) {
            int panelWidth = 155;
            int panelX = this.width - panelWidth - 10;
            int viewportWidth = panelX - 10;
            this.modelPanX = Mth.clamp(this.modelPanX + (float) dragX, -viewportWidth * 0.8f, viewportWidth * 0.8f);
            this.modelPanY = Mth.clamp(this.modelPanY + (float) dragY, -this.height * 0.8f, this.height * 0.8f);
            return true;
        }
        if (this.isDraggingDecal) {
            CarDecalData cur = getCurrentDecal();
            if (cur != null && this.previewCar != null) {
                float configScale = com.microdrone.mod.config.car.CarConfigManager.getConfig(this.previewCar.getVariantId()).properties.scale;
                float factor = this.previewZoom * 0.2f * configScale * 0.0625f;
                float sens = 1.0f / Math.max(0.001f, factor);

                float dxCam = (float) dragX * sens;
                float dyCam = (float) -dragY * sens;

                // レンダラー内の 180度正面合わせ回転 (Axis.YP(180)) を反映
                float effectiveYaw = this.previewYaw + 180.0f;
                float yawRad = (float) Math.toRadians(effectiveYaw);
                float cosY = (float) Math.cos(yawRad);
                float sinY = (float) Math.sin(yawRad);

                float pitchRad = (float) Math.toRadians(this.previewPitch);
                float cosP = (float) Math.cos(pitchRad);
                float sinP = (float) Math.sin(pitchRad);

                float dX = dxCam * cosY + dyCam * sinP * sinY;
                float dY = dyCam * cosP;
                float dZ = dxCam * sinY - dyCam * sinP * cosY;

                cur.setPosX(Mth.clamp(cur.getPosX() + dX, -30.0f, 30.0f));
                cur.setPosY(Mth.clamp(cur.getPosY() + dY, 0.0f, 50.0f));
                cur.setPosZ(Mth.clamp(cur.getPosZ() + dZ, -55.0f, 55.0f));

                syncSlidersFromCurrentDecal();
                return true;
            }
        }
        if (this.isDragging) {
            this.previewYaw += (float) (dragX * 0.85);
            this.previewPitch = Mth.clamp(this.previewPitch + (float) (dragY * 0.85), -85.0f, 85.0f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private record ScreenPos(float x, float y, float radius) {}

    private ScreenPos getDecalScreenPos() {
        CarDecalData cur = getCurrentDecal();
        if (cur == null || this.previewCar == null) return null;

        int panelWidth = 155;
        int panelX = this.width - panelWidth - 10;
        int viewportWidth = panelX - 10;
        int centerX = (10 + viewportWidth) / 2 + (int) this.modelPanX;
        int centerY = this.height / 2 + 10 + (int) this.modelPanY;

        float configScale = com.microdrone.mod.config.car.CarConfigManager.getConfig(this.previewCar.getVariantId()).properties.scale;
        float factor = this.previewZoom * 0.2f * configScale * 0.0625f;

        float x0 = cur.getPosX();
        float y0 = cur.getPosY();
        float z0 = cur.getPosZ();

        // レンダラー内の 180度正面合わせ回転 (Axis.YP(180)) を反映
        float effectiveYaw = this.previewYaw + 180.0f;
        float yawRad = (float) Math.toRadians(effectiveYaw);
        float cosY = (float) Math.cos(yawRad);
        float sinY = (float) Math.sin(yawRad);

        float x1 = x0 * cosY + z0 * sinY;
        float y1 = y0;
        float z1 = -x0 * sinY + z0 * cosY;

        float pitchRad = (float) Math.toRadians(this.previewPitch);
        float cosP = (float) Math.cos(pitchRad);
        float sinP = (float) Math.sin(pitchRad);

        float x2 = x1;
        float y2 = y1 * cosP - z1 * sinP;

        float sx = centerX + x2 * factor;
        float sy = centerY - y2 * factor;

        float radius = Math.max(14.0f, (cur.getScale() * factor) * 0.8f);
        return new ScreenPos(sx, sy, radius);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelWidth = 155;
        int panelX = this.width - panelWidth - 10;
        int viewportWidth = panelX - 10;

        // 右側パネル上でのホイール: 操作パネルの上下スクロール
        if (mouseX >= panelX - 5 && mouseX <= this.width) {
            if (this.maxScrollY > 0) {
                this.panelScrollY = Mth.clamp(this.panelScrollY - (float)(scrollY * 16.0), 0.0f, this.maxScrollY);
                updateScrollablePositions();
                return true;
            }
        }

        // 左側3Dビューポート上でのホイール: 車両の拡大縮小ズーム
        if (mouseX <= viewportWidth) {
            this.previewZoom = Mth.clamp(this.previewZoom + (float) (scrollY * 8.0), 30.0f, 220.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. super.render を最初に実行 (背景メニューブラーとウィジェットを奥に描画)
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelWidth = 155;
        int panelX = this.width - panelWidth - 10;
        int viewportWidth = panelX - 10;

        // 2. 左側3Dビューポートの枠と背景パネル
        graphics.fillGradient(10, 10, viewportWidth, this.height - 10, 0xEE101015, 0xEE181820);
        graphics.renderOutline(10, 10, viewportWidth - 10, this.height - 20, 0xFF404050);

        // 3. 右側操作パネルの枠と背景
        graphics.fillGradient(panelX - 5, 10, panelX + panelWidth + 5, this.height - 10, 0xEE14141A, 0xEE1C1C24);
        graphics.renderOutline(panelX - 5, 10, panelWidth + 10, this.height - 20, 0xFF505060);

        // スクロールエリアの上下仕切り線
        graphics.hLine(panelX - 4, panelX + panelWidth + 4, 82, 0xFF353545);
        graphics.hLine(panelX - 4, panelX + panelWidth + 4, this.height - 84, 0xFF353545);

        // スクロールインジケータ（スクロールバーのツマミ）
        if (this.maxScrollY > 0) {
            int trackTop = 84;
            int trackBottom = this.height - 86;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = Math.max(12, (int) (trackHeight * (trackHeight / (trackHeight + this.maxScrollY))));
            int thumbY = trackTop + (int) ((trackHeight - thumbHeight) * (this.panelScrollY / this.maxScrollY));
            graphics.fill(panelX + panelWidth + 2, thumbY, panelX + panelWidth + 4, thumbY + thumbHeight, 0xFF707090);
        }

        // 4. タイトルと情報テキスト
        graphics.drawString(this.font, this.title, 18, 16, 0xFFE0E0E0);
        graphics.drawString(this.font, Component.literal("ドラッグ: 視点回転 / 右ドラッグ: モデル移動 / ステッカー(Shift): 移動 / ホイール: ズーム"), 18, this.height - 24, 0xFFA0A0B0);

        // ステッカーファイル名表示
        CarDecalData cur = getCurrentDecal();
        String currentStickerName = cur != null ? cur.getTextureName() : "None";
        String stickerLabel = currentStickerName.length() > 14 ? currentStickerName.substring(0, 12) + ".." : currentStickerName;
        graphics.drawCenteredString(this.font, Component.literal(stickerLabel), panelX + panelWidth / 2, 27, 0xFFFFFF);

        // デカールレイヤー表示 (固定フッター上部: スライダーとは絶対に被らない)
        String layerText = String.format("デカール %d / %d", this.selectedDecalIndex + 1, Math.max(1, this.editingDecals.size()));
        graphics.drawCenteredString(this.font, Component.literal(layerText), panelX + panelWidth / 2, this.height - 78, 0xFFE0E0E0);

        // 5. 3D RCカーモデルのリアルタイムプレビュー描画
        if (this.previewCar != null) {
            // 編集中のデカールリストをダミー車体に反映
            this.previewCar.setDecals(this.editingDecals);

            int centerX = (10 + viewportWidth) / 2 + (int) this.modelPanX;
            int centerY = this.height / 2 + 10 + (int) this.modelPanY;
            renderCarInGui(graphics, centerX, centerY, this.previewZoom, this.previewYaw, this.previewPitch, this.previewCar);
        }

        // 6. 選択中ステッカーのドラッグ用ハイライト枠表示 (くっきり最前面で描画)
        ScreenPos dPos = getDecalScreenPos();
        if (dPos != null && dPos.x >= 10 && dPos.x <= viewportWidth && dPos.y >= 10 && dPos.y <= this.height - 10) {
            int ringColor = this.isDraggingDecal ? 0xFFFFEE44 : 0xFF00EEFF;
            int innerColor = this.isDraggingDecal ? 0x33FFDD00 : 0x2200CCFF;
            int r = (int) dPos.radius;
            int cx = (int) dPos.x;
            int cy = (int) dPos.y;

            // 半透明ハイライト背景とくっきり二重枠
            graphics.fill(cx - r, cy - r, cx + r, cy + r, innerColor);
            graphics.renderOutline(cx - r, cy - r, r * 2, r * 2, ringColor);
            graphics.renderOutline(cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2, 0x88000000);

            // 「✛ つかんで移動」バッジ表示 (ドロップシャドウ付き)
            graphics.fill(cx - 36, cy - r - 13, cx + 36, cy - r - 1, 0xCC101018);
            graphics.renderOutline(cx - 36, cy - r - 13, 72, 12, ringColor);
            graphics.drawCenteredString(this.font, Component.literal("✛ つかんで移動"), cx, cy - r - 11, ringColor);
        }
    }

    /**
     * GUI内にRCカーエンティティ（GeckoLib 3Dモデル＋デカールレイヤー）をリアルタイム描画
     */
    private static void renderCarInGui(GuiGraphics graphics, int centerX, int centerY, float scale, float yaw, float pitch, RcCarEntity car) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 150.0);
        // スケール
        poseStack.scale(scale, -scale, scale);
        // 回転
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

        RenderSystem.enableDepthTest();

        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        dispatcher.render(car, 0.0, 0.0, 0.0, 0.0f, 1.0f, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        bufferSource.endBatch();

        dispatcher.setRenderShadow(true);
        RenderSystem.disableDepthTest();

        poseStack.popPose();
    }

    /**
     * 数値微調整スライダー
     */
    private static class FloatSlider extends AbstractSliderButton {
        private final Component prefix;
        private final float min;
        private final float max;
        private final Consumer<Float> onChange;

        public FloatSlider(int x, int y, int width, int height, Component prefix, float value, float min, float max, Consumer<Float> onChange) {
            super(x, y, width, height, CommonComponents.EMPTY, (value - min) / (max - min));
            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float val = getFloatValue();
            setMessage(Component.empty().append(this.prefix).append(String.format(": %.1f", val)));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(getFloatValue());
        }

        public float getFloatValue() {
            return this.min + (float) this.value * (this.max - this.min);
        }

        public void setFloatValue(float val) {
            this.value = Mth.clamp((val - this.min) / (this.max - this.min), 0.0, 1.0);
            updateMessage();
        }
    }
}

