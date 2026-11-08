package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcCarGeoModel;
import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.Locale;

public class RcCarGeoRenderer extends GeoEntityRenderer<RcCarEntity> {

    public RcCarGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RcCarGeoModel());
        this.shadowRadius = 0.25F; // 1/5 RCカーの影の半径
    }

    @Override
    public void render(RcCarEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // レスキュー点滅演出: 点滅タイマー作動中は2tickおきに描画をスキップして高速点滅
        if (entity.getResyncBlinkTicks() > 0) {
            if ((entity.getResyncBlinkTicks() / 2) % 2 == 0) {
                return;
            }
        }

        poseStack.pushPose();

        // 1/5 スケールに縮小（手のひら・卓上マイクロRCカーサイズ）+ 外部コンフィグ倍率適用
        float configScale = com.microdrone.mod.config.car.CarConfigManager.getConfig(entity.getVariantId()).properties.scale;
        poseStack.scale(0.2F * configScale, 0.2F * configScale, 0.2F * configScale);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // 車体の進行方向の回転（正面合わせ）
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // サスペンション・路面追従によるロール（横傾き: 符号反転 -roll で持ち上がり/沈み込みを正常化）
        float roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        // サスペンション・路面追従によるピッチ（前後傾き: 符号反転 -pitch で段差前上がり/ノーズダイブを正常化）
        float pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, RcCarEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (animatable.isHeadlightOn()) {
            String name = bone.getName().toLowerCase(Locale.ROOT);
            // リトラクタブル、ヘッドライト、テールライト、各種ランプボーンをフルブライト発光
            if (name.contains("retract") || name.contains("headlight") || name.contains("light") || name.contains("lamp") || name.contains("taillight")) {
                packedLight = LightTexture.FULL_BRIGHT;
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
