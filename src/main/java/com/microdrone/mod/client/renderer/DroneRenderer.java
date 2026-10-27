package com.microdrone.mod.client.renderer;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.model.DroneModel;
import com.microdrone.mod.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class DroneRenderer extends EntityRenderer<DroneEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/drone.png");

    private final DroneModel model;

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
        this.model = new DroneModel(context.bakeLayer(DroneModel.LAYER_LOCATION));
    }

    @Override
    public void render(DroneEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.rotLerp(partialTick, entity.xRotO, entity.getXRot());

        // 機首方向の回転
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // 移動速度に応じた傾き（ピッチ・ロール）の算出
        Vec3 vel = entity.getDeltaMovement();
        float rad = (float) Math.toRadians(yaw);
        double localForward = -Math.sin(rad) * vel.x + Math.cos(rad) * vel.z;
        double localStrafe = Math.cos(rad) * vel.x + Math.sin(rad) * vel.z;

        float tiltPitch = (float) (localForward / DroneEntity.MAX_HORIZONTAL_SPEED) * 20.0F;
        float tiltRoll = (float) (localStrafe / DroneEntity.MAX_HORIZONTAL_SPEED) * 20.0F;

        tiltPitch = Mth.clamp(tiltPitch, -25.0F, 25.0F);
        tiltRoll = Mth.clamp(tiltRoll, -25.0F, 25.0F);

        // 機体のピッチとロールの適用（前進時は前傾、後退時は後傾、左右移動時は自然なバンク傾き）
        poseStack.mulPose(Axis.XP.rotationDegrees(-tiltPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltRoll));

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.5D, 0.0D);

        this.model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTick, yaw, pitch);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DroneEntity entity) {
        return TEXTURE;
    }
}
