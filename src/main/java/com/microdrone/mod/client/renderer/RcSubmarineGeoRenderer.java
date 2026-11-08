package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcSubmarineGeoModel;
import com.microdrone.mod.entity.RcSubmarineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RcSubmarineGeoRenderer extends GeoEntityRenderer<RcSubmarineEntity> {

    public RcSubmarineGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RcSubmarineGeoModel());
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(RcSubmarineEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 1/4模型サイズ + 外部コンフィグ倍率適用
        float configScale = com.microdrone.mod.config.submarine.SubmarineConfigManager.getConfig(entity.getVariantId()).properties.scale;
        poseStack.scale(0.25F * configScale, 0.25F * configScale, 0.25F * configScale);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.rotLerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
        float roll = Mth.rotLerp(partialTick, entity.prevRollAngle, entity.getRollAngle());

        // 進行方向ヨー
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 旋回ロール
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        // 潜航・浮上ピッチ
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}

