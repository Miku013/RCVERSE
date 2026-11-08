package com.microdrone.mod.client.renderer;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.ModularSlopeBlock;
import com.microdrone.mod.block.entity.ModularSlopeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 前後に何個繋げても滑らかに浅くなる動的モジュラースロープ描画
 */
public class ModularSlopeBlockEntityRenderer implements BlockEntityRenderer<ModularSlopeBlockEntity> {

    public static final ResourceLocation ROAD_TEX = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/modular_slope_road.png");
    public static final ResourceLocation CURB_TEX = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/modular_slope_curb.png");
    public static final ResourceLocation SIDE_TEX = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/modular_slope_side.png");

    public ModularSlopeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ModularSlopeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.isDirty()) {
            be.recalculateChain();
        }

        Direction facing = be.getBlockState().getValue(ModularSlopeBlock.FACING);
        int chainIndex = be.getChainIndex();
        int totalChain = be.getTotalChain();
        boolean hasMinSide = be.hasMinSideSlope(); // X=0側
        boolean hasMaxSide = be.hasMaxSideSlope(); // X=1側

        poseStack.pushPose();

        // 原点をブロック中心 (0.5, 0.5) にして回転後、戻す
        poseStack.translate(0.5, 0.0, 0.5);
        switch (facing) {
            case NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
            case SOUTH -> {} // 0度 (ローカルZ+方向が登り)
        }
        poseStack.translate(-0.5, 0.0, -0.5);

        PoseStack.Pose pose = poseStack.last();

        // 登り方向 (Z: 0.0=下り側入口, 1.0=登り側出口)
        float yStart = (float) chainIndex / (float) totalChain;
        float yEnd = (float) (chainIndex + 1) / (float) totalChain;
        float deltaY = yEnd - yStart;

        // 法線ベクトル
        float len = (float) Math.sqrt(1.0f + deltaY * deltaY);
        float ny = 1.0f / len;
        float nz = -deltaY / len;

        float curbWidth = 0.125f;  // 2px幅 (2/16 = 0.125)
        float curbHeight = 0.125f; // 2px高さ

        // --- 1. 道路面 (上向き) ---
        // RenderType.entityCutoutNoCull でカリングを防止
        VertexConsumer roadConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(ROAD_TEX));
        // CCW (反時計回り): (0, yStart, 0) -> (1, yStart, 0) -> (1, yEnd, 1) -> (0, yEnd, 1)
        addQuad(roadConsumer, pose,
                0.0f, yStart, 0.0f,  0.0f, 0.0f,
                1.0f, yStart, 0.0f,  1.0f, 0.0f,
                1.0f, yEnd, 1.0f,    1.0f, 1.0f,
                0.0f, yEnd, 1.0f,    0.0f, 1.0f,
                0.0f, ny, nz, packedLight, packedOverlay);

        // --- 2. 縁石 (赤白ゼブラ) ---
        VertexConsumer curbConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(CURB_TEX));

        // X=0 側の縁石
        if (!hasMinSide) {
            // 縁石上面
            addQuad(curbConsumer, pose,
                    0.0f, yStart + curbHeight, 0.0f,       0.0f, 0.0f,
                    curbWidth, yStart + curbHeight, 0.0f,  0.125f, 0.0f,
                    curbWidth, yEnd + curbHeight, 1.0f,    0.125f, 1.0f,
                    0.0f, yEnd + curbHeight, 1.0f,         0.0f, 1.0f,
                    0.0f, ny, nz, packedLight, packedOverlay);
            // 縁石内側垂直面 (法線 nx = 1.0)
            addQuad(curbConsumer, pose,
                    curbWidth, yStart, 0.0f,             0.0f, 0.125f,
                    curbWidth, yStart + curbHeight, 0.0f, 0.0f, 0.0f,
                    curbWidth, yEnd + curbHeight, 1.0f,   1.0f, 0.0f,
                    curbWidth, yEnd, 1.0f,               1.0f, 0.125f,
                    1.0f, 0.0f, 0.0f, packedLight, packedOverlay);
        }

        // X=1 側の縁石
        if (!hasMaxSide) {
            float inner = 1.0f - curbWidth;
            // 縁石上面
            addQuad(curbConsumer, pose,
                    inner, yStart + curbHeight, 0.0f, 0.875f, 0.0f,
                    1.0f, yStart + curbHeight, 0.0f,  1.0f, 0.0f,
                    1.0f, yEnd + curbHeight, 1.0f,    1.0f, 1.0f,
                    inner, yEnd + curbHeight, 1.0f,   0.875f, 1.0f,
                    0.0f, ny, nz, packedLight, packedOverlay);
            // 縁石内側垂直面 (法線 nx = -1.0)
            addQuad(curbConsumer, pose,
                    inner, yStart + curbHeight, 0.0f, 0.0f, 0.0f,
                    inner, yStart, 0.0f,             0.0f, 0.125f,
                    inner, yEnd, 1.0f,               1.0f, 0.125f,
                    inner, yEnd + curbHeight, 1.0f,   1.0f, 0.0f,
                    -1.0f, 0.0f, 0.0f, packedLight, packedOverlay);
        }

        // --- 3. 側面 (地面と道路の隙間を塞ぐ) ---
        VertexConsumer sideConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(SIDE_TEX));

        // X=0 側面 (法線 nx = -1.0)
        if (!hasMinSide) {
            addQuad(sideConsumer, pose,
                    0.0f, 0.0f, 0.0f,              0.0f, 1.0f,
                    0.0f, yStart + curbHeight, 0.0f, 0.0f, 0.0f,
                    0.0f, yEnd + curbHeight, 1.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f,              1.0f, 1.0f,
                    -1.0f, 0.0f, 0.0f, packedLight, packedOverlay);
        }

        // X=1 側面 (法線 nx = 1.0)
        if (!hasMaxSide) {
            addQuad(sideConsumer, pose,
                    1.0f, 0.0f, 1.0f,              1.0f, 1.0f,
                    1.0f, yEnd + curbHeight, 1.0f, 1.0f, 0.0f,
                    1.0f, yStart + curbHeight, 0.0f, 0.0f, 0.0f,
                    1.0f, 0.0f, 0.0f,              0.0f, 1.0f,
                    1.0f, 0.0f, 0.0f, packedLight, packedOverlay);
        }

        // 登り側背面 (Z = 1.0) - 最上段ブロックのみ背面を描画 (法線 nz = 1.0)
        if (chainIndex == totalChain - 1) {
            addQuad(sideConsumer, pose,
                    0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
                    1.0f, 0.0f, 1.0f, 0.0f, 1.0f,
                    1.0f, yEnd, 1.0f, 0.0f, 0.0f,
                    0.0f, yEnd, 1.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f, packedLight, packedOverlay);
        }

        // 下り側前面 (Z = 0.0) - 最下段ブロック（chainIndex == 0）で startY が 0 でない場合、または地面との境
        if (chainIndex == 0 && yStart > 0.001f) {
            addQuad(sideConsumer, pose,
                    1.0f, 0.0f, 0.0f, 1.0f, 1.0f,
                    0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
                    0.0f, yStart, 0.0f, 0.0f, 0.0f,
                    1.0f, yStart, 0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, -1.0f, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private void addQuad(VertexConsumer consumer, PoseStack.Pose pose,
                         float x0, float y0, float z0, float u0, float v0,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float nx, float ny, float nz, int light, int overlay) {
        Matrix4f mat = pose.pose();
        consumer.addVertex(mat, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
