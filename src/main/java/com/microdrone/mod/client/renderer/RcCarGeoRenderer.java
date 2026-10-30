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
        this.shadowRadius = 0.8F; // 車の影の半径
    }

    @Override
    public void render(RcCarEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // 車体の進行方向の回転（正面合わせ）
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}

