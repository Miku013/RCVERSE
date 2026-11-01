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
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(RcPlaneEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // RCプレーンスケール（1/4スケール、模型飛行機サイズ）
        poseStack.scale(0.25F, 0.25F, 0.25F);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // ヨー回転（進行方向）
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        // 3軸航空機姿勢角（ピッチ・ロール）
        float pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
        float roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.getRollAngle());

        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
