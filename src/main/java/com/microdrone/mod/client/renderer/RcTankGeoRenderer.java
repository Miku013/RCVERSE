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
        this.shadowRadius = 0.3F; // 1/5 RC戦車の影の半径
    }

    @Override
    public void render(RcTankEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 1/5 スケールに縮小（卓上マイクロRC戦車サイズ）
        poseStack.scale(0.2F, 0.2F, 0.2F);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // 車体の進行方向の回転（正面合わせ）
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        // 【接地ポイント4点方式】地形追従ピッチ・ロール傾斜
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

