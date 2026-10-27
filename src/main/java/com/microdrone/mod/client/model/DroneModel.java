package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.DroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

public class DroneModel extends EntityModel<DroneEntity> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "drone"), "main");

    private final ModelPart root;
    private final ModelPart body;

    public DroneModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 中央メインボディ (4x2x4)
        PartDefinition body = partdefinition.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // アームとローター（仮の4隅突起）
        // 前左
        body.addOrReplaceChild("arm_fl",
                CubeListBuilder.create()
                        .texOffs(16, 0)
                        .addBox(2.0F, -1.5F, -4.0F, 2.0F, 1.0F, 2.0F),
                PartPose.ZERO);
        // 前右
        body.addOrReplaceChild("arm_fr",
                CubeListBuilder.create()
                        .texOffs(16, 4)
                        .addBox(-4.0F, -1.5F, -4.0F, 2.0F, 1.0F, 2.0F),
                PartPose.ZERO);
        // 後左
        body.addOrReplaceChild("arm_bl",
                CubeListBuilder.create()
                        .texOffs(16, 8)
                        .addBox(2.0F, -1.5F, 2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.ZERO);
        // 後右
        body.addOrReplaceChild("arm_br",
                CubeListBuilder.create()
                        .texOffs(16, 12)
                        .addBox(-4.0F, -1.5F, 2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.ZERO);

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(DroneEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Phase 1：静止モデル。姿勢調整
        this.body.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.body.xRot = headPitch * ((float)Math.PI / 180F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
