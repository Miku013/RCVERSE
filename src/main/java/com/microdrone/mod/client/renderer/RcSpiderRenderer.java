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

        com.microdrone.mod.config.spider.SpiderConfig config = com.microdrone.mod.config.spider.SpiderConfigManager.getConfig(entity.getVariantId());
        float configScale = (config != null) ? config.properties.scale : 0.35F;
        float yOffset = (config != null) ? config.properties.yOffset : 0.0F;
        float scale = Math.max(0.05F, configScale);

        // 進行方向の回転（正面合わせ: 180.0F - yaw）
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 6本の足の接地高さから算出されたピッチ・ロール傾斜（スムーズ補間）
        float pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.pitchAngle);
        float roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.rollAngle);
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        // スプリング復元力による車高リフト（地面接触時に機体を持ち上げてお腹擦りを防止）
        float bodyLift = Mth.lerp(partialTick, entity.prevBodyLiftY, entity.bodyLiftY);
        poseStack.translate(0, yOffset + bodyLift, 0);

        // スケール倍率の適用（卓上RCサイズ）
        poseStack.scale(scale, scale, scale);

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
