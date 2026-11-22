package com.microdrone.mod.client.gui;

import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.decal.RcDecalHolder;
import com.microdrone.mod.entity.*;
import com.microdrone.mod.item.*;
import com.mojang.blaze3d.platform.GlStateManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * RCピット作業台のメインGUI画面。
 * マシンを読み込み、360度自由回転・上下ピッチ・ズーム・パンによる自由な3Dプレビューを提供します。
 */
@OnlyIn(Dist.CLIENT)
public class RcPitStationScreen extends Screen {
    private final BlockPos blockPos;
    private final ItemStack vehicleStack;

    // 3Dプレビュー用のダミーエンティティ
    private Entity previewEntity;
    private RcDecalHolder decalHolder;

    // 3Dカメラ・視点パラメータ
    private float previewYaw = -45.0F;     // 左右回転（初期値: 斜め45度）
    private float previewPitch = 15.0F;    // 上下角度（初期値: やや見下ろし15度）
    private float previewZoom = 80.0F;     // 拡大率
    private float modelPanX = 0.0F;        // 平行移動 X
    private float modelPanY = 0.0F;        // 平行移動 Y

    // マウスドラッグ状態
    private boolean isRotating = false;
    private boolean isPanning = false;

    public RcPitStationScreen(BlockPos pos, ItemStack vehicleStack) {
        super(Component.translatable("gui.microdrone.pit_station.title"));
        this.blockPos = pos;
        this.vehicleStack = vehicleStack;
    }

    @Override
    protected void init() {
        super.init();

        // ダミーエンティティの初期化
        initDummyEntity();

        // リセットボタン（左下に配置）
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.microdrone.pit_station.reset"),
                button -> resetCamera()
        ).bounds(20, this.height - 40, 90, 20).build());

        // 閉じるボタン（右上に配置）
        this.addRenderableWidget(Button.builder(
                Component.literal("✕"),
                button -> this.onClose()
        ).bounds(this.width - 32, 10, 22, 20).build());
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
        // 左クリック: 3Dモデルの回転開始
        if (button == 0) {
            this.isRotating = true;
            return true;
        }
        // 右クリックまたは中クリック: 画面内パン（平行移動）開始
        if (button == 1 || button == 2) {
            this.isPanning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        // 1. 背景の暗転グラデーション
        graphics.fillGradient(0, 0, this.width, this.height, 0xF00a0e17, 0xF5111827);

        // 2. 中央の3Dビューポート枠
        int viewMargin = 12;
        graphics.fillGradient(viewMargin, viewMargin, this.width - viewMargin, this.height - viewMargin, 0xEE090d16, 0xEE111827);
        graphics.renderOutline(viewMargin, viewMargin, this.width - viewMargin * 2, this.height - viewMargin * 2, 0xFF0284c7);

        // 3. ヘッダータイトル
        graphics.fill(viewMargin, viewMargin, this.width - viewMargin, viewMargin + 28, 0xFF0f172a);
        graphics.hLine(viewMargin, this.width - viewMargin, viewMargin + 28, 0xFF38bdf8);
        graphics.drawString(this.font, Component.translatable("gui.microdrone.pit_station.title"), viewMargin + 12, viewMargin + 9, 0xFF38bdf8);

        // 4. 操作ヘルプ（画面下部）
        Component helpText = Component.translatable("gui.microdrone.pit_station.help");
        graphics.drawCenteredString(this.font, helpText, this.width / 2, this.height - 28, 0xFF94a3b8);

        // 5. 3Dモデルのレンダリング
        if (this.previewEntity != null) {
            int centerX = this.width / 2 + (int) this.modelPanX;
            int centerY = this.height / 2 + 10 + (int) this.modelPanY;
            renderEntityInGui(graphics, centerX, centerY, this.previewZoom, this.previewYaw, this.previewPitch, this.previewEntity);
        } else {
            // マシン未セット時の案内
            graphics.drawCenteredString(this.font, Component.translatable("gui.microdrone.pit_station.empty"), this.width / 2, this.height / 2, 0xFF64748b);
        }

        // 6. 右下：マシン情報カード
        if (!this.vehicleStack.isEmpty()) {
            renderVehicleInfoCard(graphics);
        }

        // 7. ボタンなどのウィジェット描画
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderVehicleInfoCard(GuiGraphics graphics) {
        int cardWidth = 180;
        int cardHeight = 56;
        int cardX = this.width - cardWidth - 20;
        int cardY = this.height - cardHeight - 38;

        graphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xDD0f172a);
        graphics.renderOutline(cardX, cardY, cardWidth, cardHeight, 0x6638bdf8);

        Component name = this.vehicleStack.getHoverName();
        graphics.drawString(this.font, name, cardX + 10, cardY + 8, 0xFFFFFFFF);

        String variant = this.decalHolder != null ? this.decalHolder.getVariantId() : "standard";
        graphics.drawString(this.font, Component.literal("Model: " + variant), cardX + 10, cardY + 24, 0xFF38bdf8);

        String zoomText = String.format("Zoom: %.0f%% | Pitch: %.0f°", (this.previewZoom / 80.0F) * 100.0F, this.previewPitch);
        graphics.drawString(this.font, Component.literal(zoomText), cardX + 10, cardY + 38, 0xFF64748b);
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
