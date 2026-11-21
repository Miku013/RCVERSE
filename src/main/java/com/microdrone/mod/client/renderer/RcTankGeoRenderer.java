package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcTankGeoModel;
import com.microdrone.mod.entity.RcTankEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RcTankGeoRenderer extends GeoEntityRenderer<RcTankEntity> {

    public RcTankGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RcTankGeoModel());
        this.addRenderLayer(new RcVehicleDecalLayer<>(this));
        this.shadowRadius = 0.3F; // 1/5 RC戦車の影の半径
    }

    @Override
    public void render(RcTankEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw;
        float pitch;
        float roll;

        if (!entity.isLocallyControlled() && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            // 他プレイヤー操縦・リモート車両: Snapshot Interpolation（第1段階）
            long renderTime = System.currentTimeMillis() - com.microdrone.mod.sync.VehicleSnapshotBuffer.DEFAULT_INTERPOLATION_DELAY_MS;
            com.microdrone.mod.sync.InterpolatedTransform transform = entity.getSnapshotBuffer().interpolate(renderTime);

            if (transform.valid()) {
                double vanillaX = Mth.lerp((double) partialTick, entity.xOld, entity.getX());
                double vanillaY = Mth.lerp((double) partialTick, entity.yOld, entity.getY());
                double vanillaZ = Mth.lerp((double) partialTick, entity.zOld, entity.getZ());

                poseStack.translate(transform.x() - vanillaX, transform.y() - vanillaY, transform.z() - vanillaZ);

                yaw = transform.yaw();
                pitch = transform.pitch();
                roll = transform.roll();
            } else {
                yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
                pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
                roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
            }
        } else {
            yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
            roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
        }

        // 外部コンフィグによるスケール倍率の適用（基本 0.2F = 1/5 卓上サイズ）
        float configScale = com.microdrone.mod.config.tank.TankConfigManager.getConfig(entity.getVariantId()).properties.scale;
        float baseScale = 0.2F * Math.max(0.1F, configScale);
        poseStack.scale(baseScale, baseScale, baseScale);

        // 車体の進行方向の回転（正面合わせ: 他車種と統一して 180.0F - yaw）
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 【接地ポイント4点方式】地形追従ピッチ・ロール傾斜
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}

