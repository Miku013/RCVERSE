package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.DroneGeoModel;
import com.microdrone.mod.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DroneGeoRenderer extends GeoEntityRenderer<DroneEntity> {

    public DroneGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new DroneGeoModel());
        this.shadowRadius = 0.25F;

        // battery_slot ボーンへのバッテリー描画レイヤーを追加
        addRenderLayer(new DroneBatteryLayer(this));
    }

    @Override
    public void render(DroneEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float configScale = com.microdrone.mod.config.drone.DroneConfigManager.getConfig(entity.getVariantId()).properties.scale;
        if (configScale != 1.0f) {
            poseStack.scale(configScale, configScale, configScale);
        }

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());

        // 移動速度に応じた傾き（ピッチ・ロール）の算出
        Vec3 vel = entity.getDeltaMovement();
        float rad = (float) Math.toRadians(yaw);
        double localForward = -Math.sin(rad) * vel.x + Math.cos(rad) * vel.z;
        double localStrafe = Math.cos(rad) * vel.x + Math.sin(rad) * vel.z;

        double maxSpeed = entity.getCurrentMaxSpeed();
        float tiltPitch = (float) (localForward / maxSpeed) * 20.0F;
        float tiltRoll = (float) (localStrafe / maxSpeed) * 20.0F;

        tiltPitch = Mth.clamp(tiltPitch, -25.0F, 25.0F);
        tiltRoll = Mth.clamp(tiltRoll, -25.0F, 25.0F);

        // 機首方向の回転
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 機体のピッチとロールの適用（前進時は前傾、後退時は後傾、左右移動時は自然なバンク傾き）
        poseStack.mulPose(Axis.XP.rotationDegrees(-tiltPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltRoll));

        // GeckoLib 描画
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, DroneEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        // モーターボーン（motor1〜motor4等）の色を、装着中モーターのカラーに染める
        if (bone.getName().startsWith("motor")) {
            colour = animatable.getMotorColor();
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
