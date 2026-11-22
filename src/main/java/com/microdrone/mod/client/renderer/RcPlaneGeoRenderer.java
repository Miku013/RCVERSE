package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcPlaneGeoModel;
import com.microdrone.mod.entity.RcPlaneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RcPlaneGeoRenderer extends GeoEntityRenderer<RcPlaneEntity> {

    public RcPlaneGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RcPlaneGeoModel());
        this.addRenderLayer(new RcVehicleDecalLayer<>(this));
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(RcPlaneEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw;
        float pitch;
        float roll;

        if (!entity.isLocallyControlled() && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            // 他プレイヤー操縦・リモート機体: Snapshot Interpolation（第1段階）
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
                pitch = Mth.rotLerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
                roll = Mth.rotLerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
            }
        } else {
            yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            pitch = Mth.rotLerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
            roll = Mth.rotLerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
        }

        // RCプレーンスケール（1/4スケール、模型飛行機サイズ）+ 外部コンフィグ倍率適用
        float configScale = com.microdrone.mod.config.plane.PlaneConfigManager.getConfig(entity.getVariantId()).properties.scale;
        poseStack.scale(0.25F * configScale, 0.25F * configScale, 0.25F * configScale);

        // 1. 進行方向（ワールド水平Yaw旋回）
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 2. 機体の進行軸周りのロール（主翼のバンク傾き: Dキー(roll>0)で右下がりバンク）
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        // 3. 傾いた主翼を基準としたエレベーターピッチ（機首上下）
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
