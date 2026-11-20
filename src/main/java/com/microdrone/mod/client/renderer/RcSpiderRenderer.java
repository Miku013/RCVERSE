package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcSpiderGeoModel;
import com.microdrone.mod.entity.spider.RcSpiderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * RC多脚ロボット（ヘキサポッド）のレンダラー
 * - スケール調整（1/3.5スケール）
 * - 地形傾斜に追従するピッチ・ロール姿勢変換
 */
public class RcSpiderRenderer extends GeoEntityRenderer<RcSpiderEntity> {

    public RcSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new RcSpiderGeoModel());
        this.shadowRadius = 0.45F;
    }

    @Override
    public void render(RcSpiderEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // 卓上RCスケール（約 0.35倍）
        float scale = 0.35F;
        poseStack.scale(scale, scale, scale);

        // 進行方向の回転（正面合わせ: 180.0F - yaw）
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 6本の足の接地高さから算出されたピッチ・ロール傾斜
        float pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.pitchAngle);
        float roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.rollAngle);
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
