package com.microdrone.mod.client.renderer;

import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.decal.RcDecalHolder;
import com.microdrone.mod.entity.*;
import com.microdrone.mod.item.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ラックやショウケースなどの展示ブロック上で、RCビークルアイテムを立体的な3Dモデルとして描画するヘルパークラス
 */
public class RcVehicleDisplayRenderer {
    private static RcCarEntity dummyCar;
    private static DroneEntity dummyDrone;
    private static RcPlaneEntity dummyPlane;
    private static RcTankEntity dummyTank;
    private static RcBoatEntity dummyBoat;
    private static RcSubmarineEntity dummySubmarine;

    public static boolean renderVehicle3D(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Level level, float scale) {
        if (stack.isEmpty() || level == null) return false;

        Entity entity = null;
        float yOffset = 0.0F;

        if (stack.is(ModItems.RC_CAR_ITEM.get())) {
            if (dummyCar == null || dummyCar.level() != level) {
                dummyCar = ModEntities.RC_CAR.get().create(level);
            }
            if (dummyCar != null) {
                syncVehicleData(dummyCar, stack);
                entity = dummyCar;
                yOffset = 0.02F;
            }
        } else if (stack.is(ModItems.DRONE_SPAWN_ITEM.get())) {
            if (dummyDrone == null || dummyDrone.level() != level) {
                dummyDrone = ModEntities.DRONE.get().create(level);
            }
            if (dummyDrone != null) {
                syncVehicleData(dummyDrone, stack);
                entity = dummyDrone;
                yOffset = 0.04F;
            }
        } else if (stack.is(ModItems.RC_TANK_ITEM.get())) {
            if (dummyTank == null || dummyTank.level() != level) {
                dummyTank = ModEntities.RC_TANK.get().create(level);
            }
            if (dummyTank != null) {
                syncVehicleData(dummyTank, stack);
                entity = dummyTank;
                yOffset = 0.02F;
            }
        } else if (stack.is(ModItems.RC_PLANE_ITEM.get())) {
            if (dummyPlane == null || dummyPlane.level() != level) {
                dummyPlane = ModEntities.RC_PLANE.get().create(level);
            }
            if (dummyPlane != null) {
                syncVehicleData(dummyPlane, stack);
                entity = dummyPlane;
                yOffset = 0.03F;
            }
        } else if (stack.is(ModItems.RC_BOAT_ITEM.get())) {
            if (dummyBoat == null || dummyBoat.level() != level) {
                dummyBoat = ModEntities.RC_BOAT.get().create(level);
            }
            if (dummyBoat != null) {
                syncVehicleData(dummyBoat, stack);
                entity = dummyBoat;
                yOffset = 0.02F;
            }
        } else if (stack.is(ModItems.RC_SUBMARINE_ITEM.get())) {
            if (dummySubmarine == null || dummySubmarine.level() != level) {
                dummySubmarine = ModEntities.RC_SUBMARINE.get().create(level);
            }
            if (dummySubmarine != null) {
                syncVehicleData(dummySubmarine, stack);
                entity = dummySubmarine;
                yOffset = 0.03F;
            }
        }

        if (entity != null) {
            poseStack.pushPose();
            poseStack.translate(0.0, yOffset, 0.0);
            poseStack.scale(scale, scale, scale);

            // 車体向きの補正（正面を向ける）
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, poseStack, bufferSource, packedLight);
            dispatcher.setRenderShadow(true);

            poseStack.popPose();
            return true;
        }

        return false;
    }

    private static void syncVehicleData(Entity entity, ItemStack stack) {
        if (entity instanceof RcDecalHolder holder) {
            var customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("Variant")) {
                    holder.setVariantId(tag.getString("Variant"));
                }
                if (tag.contains("BodyColor") && entity instanceof RcCarEntity car) {
                    car.setBodyColor(tag.getInt("BodyColor"));
                }
                if (tag.contains("Decals", 10)) {
                    List<CarDecalData> loaded = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
                    holder.setDecals(loaded);
                }
            }
        }
    }
}
