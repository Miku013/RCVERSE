package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcCarGeoModel;
import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RcCarGeoRenderer extends GeoEntityRenderer<RcCarEntity> {

    public RcCarGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RcCarGeoModel());
        this.shadowRadius = 0.25F; // 1/5 RCカーの影の半径
    }

    @Override
    public void render(RcCarEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 1/5 スケールに縮小（手のひら・卓上マイクロRCカーサイズ）
        poseStack.scale(0.2F, 0.2F, 0.2F);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // 車体の進行方向の回転（正面合わせ）
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // サスペンション・路面追従によるロール（横傾き: 符号反転 -roll で持ち上がり/沈み込みを正常化）
        float roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        // サスペンション・路面追従によるピッチ（前後傾き: ノーズダイブ/スクワット/段差傾斜）
        float pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
