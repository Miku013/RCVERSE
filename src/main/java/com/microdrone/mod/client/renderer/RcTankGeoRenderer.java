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

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}

