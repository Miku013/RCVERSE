package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.RcPitStationBlock;
import com.microdrone.mod.block.entity.RcPitStationBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * RCピット作業台ブロックエンティティのレンダラー。
 * 天板（Y=0.875）の上に保管されたRCマシンを実物の3Dモデルで立体展示します。
 */
@OnlyIn(Dist.CLIENT)
public class RcPitStationBlockEntityRenderer implements BlockEntityRenderer<RcPitStationBlockEntity> {

    public RcPitStationBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RcPitStationBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack vehicle = blockEntity.getStoredVehicle();
        if (vehicle.isEmpty() || blockEntity.getLevel() == null) {
            return;
        }

        Direction facing = blockEntity.getBlockState().hasProperty(RcPitStationBlock.FACING)
                ? blockEntity.getBlockState().getValue(RcPitStationBlock.FACING)
                : Direction.NORTH;

        poseStack.pushPose();
        // 天板の中央上に配置（天板上面 Y=0.875）
        poseStack.translate(0.5, 0.88, 0.5);

        // ブロックの向きに合わせて回転
        float yRot = switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

        // 3Dモデル立体描画（スケール 0.65F）
        RcVehicleDisplayRenderer.renderVehicle3D(
                vehicle,
                poseStack,
                bufferSource,
                packedLight,
                blockEntity.getLevel(),
                0.65F,
                0.0F
        );

        poseStack.popPose();
    }
}
