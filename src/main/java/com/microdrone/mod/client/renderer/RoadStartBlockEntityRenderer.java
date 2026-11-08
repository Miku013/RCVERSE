package com.microdrone.mod.client.renderer;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.road.RoadEndBlock;
import com.microdrone.mod.block.road.RoadSplineData;
import com.microdrone.mod.block.road.RoadSplineGenerator;
import com.microdrone.mod.block.road.RoadSplineGenerator.SplineSample;
import com.microdrone.mod.block.road.RoadStartBlock;
import com.microdrone.mod.block.road.RoadStartBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 道路・ヘアピンカーブ動的メッシュ描画レンダラー
 * 始点から終点までの3次ベジェ曲線に沿って道路面・赤白縁石・側面をプロシージャルに描画します。
 */
public class RoadStartBlockEntityRenderer implements BlockEntityRenderer<RoadStartBlockEntity> {

    public static final ResourceLocation ROAD_TEX = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/modular_slope_road.png");
    public static final ResourceLocation CURB_TEX = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/modular_slope_curb.png");
    public static final ResourceLocation SIDE_TEX = ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/modular_slope_side.png");

    public RoadStartBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(RoadStartBlockEntity be) {
        return true; // チャンク境界をまたいでも描画を継続
    }

    @Override
    public int getViewDistance() {
        return 256; // 遠くからでも見通せる広域描画距離
    }

    @Override
    public AABB getRenderBoundingBox(RoadStartBlockEntity be) {
        return be.getRoadBoundingBox();
    }

    @Override
    public void render(RoadStartBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockPos endPos = be.getLinkedEndPos();
        if (endPos == null || be.getLevel() == null) return;

        RoadSplineData data = be.getSplineData();
        // プレビューも確定もOFFなら描画しない
        if (!data.isPreview() && !data.isGenerated()) return;

        Direction startFacing = be.getBlockState().getValue(RoadStartBlock.FACING);
        Direction endFacing = Direction.SOUTH;
        if (be.getLevel().getBlockState(endPos).getBlock() instanceof RoadEndBlock) {
            endFacing = be.getLevel().getBlockState(endPos).getValue(RoadEndBlock.FACING);
        }

        List<SplineSample> samples = RoadSplineGenerator.generateSpline(
                be.getBlockPos(), startFacing,
                endPos, endFacing,
                data
        );
        if (samples.size() < 2) return;

        // クライアント側物理用マネージャーに登録・同期
        com.microdrone.mod.block.road.RoadSplineManager.registerRoad(be.getLevel(), be);

        poseStack.pushPose();

        // 原点を BlockPos (始点ブロック) に合わせるため、絶対座標から始点座標を引く
        BlockPos origin = be.getBlockPos();
        double ox = origin.getX();
        double oy = origin.getY();
        double oz = origin.getZ();

        PoseStack.Pose pose = poseStack.last();

        float halfWidth = data.getRoadWidth() * 0.5f;
        float curbWidth = 0.40f;  // 縁石幅 (0.4m)
        float curbHeight = 0.15f; // 縁石高さ (0.15m)

        boolean isPreviewMode = data.isPreview() && !data.isGenerated();
        int alpha = isPreviewMode ? 170 : 255;
        RenderType roadRenderType = isPreviewMode ? RenderType.entityTranslucent(ROAD_TEX) : RenderType.entityCutoutNoCull(ROAD_TEX);
        RenderType curbRenderType = isPreviewMode ? RenderType.entityTranslucent(CURB_TEX) : RenderType.entityCutoutNoCull(CURB_TEX);
        RenderType sideRenderType = isPreviewMode ? RenderType.entityTranslucent(SIDE_TEX) : RenderType.entityCutoutNoCull(SIDE_TEX);

        // =========================================================================
        // パス 1: 道路面 (Road Surface)
        // MultiBufferSource の仕様上、同一 RenderType をまとめて連続描画
        // =========================================================================
        VertexConsumer roadConsumer = buffer.getBuffer(roadRenderType);
        for (int i = 0; i < samples.size() - 1; i++) {
            SplineSample s0 = samples.get(i);
            SplineSample s1 = samples.get(i + 1);

            Vec3 c0 = s0.center.subtract(ox, oy, oz);
            Vec3 c1 = s1.center.subtract(ox, oy, oz);

            Vec3 l0 = c0.subtract(s0.rightBanked.scale(halfWidth));
            Vec3 r0 = c0.add(s0.rightBanked.scale(halfWidth));
            Vec3 l1 = c1.subtract(s1.rightBanked.scale(halfWidth));
            Vec3 r1 = c1.add(s1.rightBanked.scale(halfWidth));

            float v0 = (float) (s0.distSoFar / 2.0);
            float v1 = (float) (s1.distSoFar / 2.0);

            Vec3 norm = s0.upBanked.add(s1.upBanked).normalize();
            float nx = (float) norm.x;
            float ny = (float) norm.y;
            float nz = (float) norm.z;

            // 道路面 (CCW 反時計回り)
            addQuad(roadConsumer, pose,
                    (float) l0.x, (float) l0.y, (float) l0.z, 0.0f, v0,
                    (float) r0.x, (float) r0.y, (float) r0.z, 1.0f, v0,
                    (float) r1.x, (float) r1.y, (float) r1.z, 1.0f, v1,
                    (float) l1.x, (float) l1.y, (float) l1.z, 0.0f, v1,
                    nx, ny, nz, alpha, packedLight, packedOverlay);
        }

        // =========================================================================
        // パス 2: 縁石 (Curb)
        // =========================================================================
        VertexConsumer curbConsumer = buffer.getBuffer(curbRenderType);
        for (int i = 0; i < samples.size() - 1; i++) {
            SplineSample s0 = samples.get(i);
            SplineSample s1 = samples.get(i + 1);

            Vec3 c0 = s0.center.subtract(ox, oy, oz);
            Vec3 c1 = s1.center.subtract(ox, oy, oz);

            Vec3 l0 = c0.subtract(s0.rightBanked.scale(halfWidth));
            Vec3 r0 = c0.add(s0.rightBanked.scale(halfWidth));
            Vec3 l1 = c1.subtract(s1.rightBanked.scale(halfWidth));
            Vec3 r1 = c1.add(s1.rightBanked.scale(halfWidth));

            float v0 = (float) (s0.distSoFar / 2.0);
            float v1 = (float) (s1.distSoFar / 2.0);

            Vec3 norm = s0.upBanked.add(s1.upBanked).normalize();
            float nx = (float) norm.x;
            float ny = (float) norm.y;
            float nz = (float) norm.z;

            // 左縁石
            Vec3 l0_up = l0.add(s0.upBanked.scale(curbHeight));
            Vec3 l1_up = l1.add(s1.upBanked.scale(curbHeight));
            Vec3 l0_in = l0.add(s0.rightBanked.scale(curbWidth)).add(s0.upBanked.scale(curbHeight));
            Vec3 l1_in = l1.add(s1.rightBanked.scale(curbWidth)).add(s1.upBanked.scale(curbHeight));
            Vec3 l0_base_in = l0.add(s0.rightBanked.scale(curbWidth));
            Vec3 l1_base_in = l1.add(s1.rightBanked.scale(curbWidth));

            // 左縁石上面
            addQuad(curbConsumer, pose,
                    (float) l0_up.x, (float) l0_up.y, (float) l0_up.z, 0.0f, v0,
                    (float) l0_in.x, (float) l0_in.y, (float) l0_in.z, 0.125f, v0,
                    (float) l1_in.x, (float) l1_in.y, (float) l1_in.z, 0.125f, v1,
                    (float) l1_up.x, (float) l1_up.y, (float) l1_up.z, 0.0f, v1,
                    nx, ny, nz, alpha, packedLight, packedOverlay);

            // 左縁石内側垂直面 (法線: 右向き)
            addQuad(curbConsumer, pose,
                    (float) l0_base_in.x, (float) l0_base_in.y, (float) l0_base_in.z, 0.0f, 0.125f,
                    (float) l0_in.x, (float) l0_in.y, (float) l0_in.z, 0.0f, 0.0f,
                    (float) l1_in.x, (float) l1_in.y, (float) l1_in.z, 1.0f, 0.0f,
                    (float) l1_base_in.x, (float) l1_base_in.y, (float) l1_base_in.z, 1.0f, 0.125f,
                    (float) s0.rightBanked.x, (float) s0.rightBanked.y, (float) s0.rightBanked.z, alpha, packedLight, packedOverlay);

            // 右縁石
            Vec3 r0_up = r0.add(s0.upBanked.scale(curbHeight));
            Vec3 r1_up = r1.add(s1.upBanked.scale(curbHeight));
            Vec3 r0_in = r0.subtract(s0.rightBanked.scale(curbWidth)).add(s0.upBanked.scale(curbHeight));
            Vec3 r1_in = r1.subtract(s1.rightBanked.scale(curbWidth)).add(s1.upBanked.scale(curbHeight));
            Vec3 r0_base_in = r0.subtract(s0.rightBanked.scale(curbWidth));
            Vec3 r1_base_in = r1.subtract(s1.rightBanked.scale(curbWidth));

            // 右縁石上面
            addQuad(curbConsumer, pose,
                    (float) r0_in.x, (float) r0_in.y, (float) r0_in.z, 0.875f, v0,
                    (float) r0_up.x, (float) r0_up.y, (float) r0_up.z, 1.0f, v0,
                    (float) r1_up.x, (float) r1_up.y, (float) r1_up.z, 1.0f, v1,
                    (float) r1_in.x, (float) r1_in.y, (float) r1_in.z, 0.875f, v1,
                    nx, ny, nz, alpha, packedLight, packedOverlay);

            // 右縁石内側垂直面 (法線: 左向き)
            addQuad(curbConsumer, pose,
                    (float) r0_in.x, (float) r0_in.y, (float) r0_in.z, 0.0f, 0.0f,
                    (float) r0_base_in.x, (float) r0_base_in.y, (float) r0_base_in.z, 0.0f, 0.125f,
                    (float) r1_base_in.x, (float) r1_base_in.y, (float) r1_base_in.z, 1.0f, 0.125f,
                    (float) r1_in.x, (float) r1_in.y, (float) r1_in.z, 1.0f, 0.0f,
                    -(float) s0.rightBanked.x, -(float) s0.rightBanked.y, -(float) s0.rightBanked.z, alpha, packedLight, packedOverlay);
        }

        // =========================================================================
        // パス 3: 側面スカート (Side Walls)
        // =========================================================================
        VertexConsumer sideConsumer = buffer.getBuffer(sideRenderType);
        for (int i = 0; i < samples.size() - 1; i++) {
            SplineSample s0 = samples.get(i);
            SplineSample s1 = samples.get(i + 1);

            Vec3 c0 = s0.center.subtract(ox, oy, oz);
            Vec3 c1 = s1.center.subtract(ox, oy, oz);

            Vec3 l0 = c0.subtract(s0.rightBanked.scale(halfWidth));
            Vec3 r0 = c0.add(s0.rightBanked.scale(halfWidth));
            Vec3 l1 = c1.subtract(s1.rightBanked.scale(halfWidth));
            Vec3 r1 = c1.add(s1.rightBanked.scale(halfWidth));

            Vec3 l0_up = l0.add(s0.upBanked.scale(curbHeight));
            Vec3 l1_up = l1.add(s1.upBanked.scale(curbHeight));
            Vec3 r0_up = r0.add(s0.upBanked.scale(curbHeight));
            Vec3 r1_up = r1.add(s1.upBanked.scale(curbHeight));

            Vec3 l0_down = l0_up.subtract(0, 0.5, 0);
            Vec3 l1_down = l1_up.subtract(0, 0.5, 0);
            Vec3 r0_down = r0_up.subtract(0, 0.5, 0);
            Vec3 r1_down = r1_up.subtract(0, 0.5, 0);

            // 左外側面
            addQuad(sideConsumer, pose,
                    (float) l0_down.x, (float) l0_down.y, (float) l0_down.z, 0.0f, 1.0f,
                    (float) l0_up.x, (float) l0_up.y, (float) l0_up.z, 0.0f, 0.0f,
                    (float) l1_up.x, (float) l1_up.y, (float) l1_up.z, 1.0f, 0.0f,
                    (float) l1_down.x, (float) l1_down.y, (float) l1_down.z, 1.0f, 1.0f,
                    -(float) s0.rightBanked.x, 0.0f, -(float) s0.rightBanked.z, alpha, packedLight, packedOverlay);

            // 右外側面
            addQuad(sideConsumer, pose,
                    (float) r0_up.x, (float) r0_up.y, (float) r0_up.z, 0.0f, 0.0f,
                    (float) r0_down.x, (float) r0_down.y, (float) r0_down.z, 0.0f, 1.0f,
                    (float) r1_down.x, (float) r1_down.y, (float) r1_down.z, 1.0f, 1.0f,
                    (float) r1_up.x, (float) r1_up.y, (float) r1_up.z, 1.0f, 0.0f,
                    (float) s0.rightBanked.x, 0.0f, (float) s0.rightBanked.z, alpha, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private void addQuad(VertexConsumer consumer, PoseStack.Pose pose,
                         float x0, float y0, float z0, float u0, float v0,
                         float x1, float y1, float z1, float u1, float v1,
                         float x2, float y2, float z2, float u2, float v2,
                         float x3, float y3, float z3, float u3, float v3,
                         float nx, float ny, float nz, int alpha, int light, int overlay) {
        Matrix4f mat = pose.pose();
        consumer.addVertex(mat, x0, y0, z0).setColor(255, 255, 255, alpha).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x1, y1, z1).setColor(255, 255, 255, alpha).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x2, y2, z2).setColor(255, 255, 255, alpha).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(mat, x3, y3, z3).setColor(255, 255, 255, alpha).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
