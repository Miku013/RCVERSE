package com.microdrone.mod.client.renderer;

import com.microdrone.mod.config.boat.BoatConfigManager;
import com.microdrone.mod.config.car.CarConfig;
import com.microdrone.mod.config.car.CarConfigManager;
import com.microdrone.mod.config.drone.DroneConfigManager;
import com.microdrone.mod.config.plane.PlaneConfigManager;
import com.microdrone.mod.config.submarine.SubmarineConfigManager;
import com.microdrone.mod.config.tank.TankConfig;
import com.microdrone.mod.config.tank.TankConfigManager;
import com.microdrone.mod.decal.CarDecalData;
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

import java.util.Collections;
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
        return renderVehicle3D(stack, poseStack, bufferSource, packedLight, level, scale, 0.0F);
    }

    public static boolean renderVehicle3D(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, Level level, float scale, float yRotation) {
        if (stack.isEmpty() || level == null) return false;

        Entity entity = null;
        float yOffset = 0.0F;

        if (stack.is(ModItems.RC_CAR_ITEM.get())) {
            if (dummyCar == null || dummyCar.level() != level) {
                dummyCar = ModEntities.RC_CAR.get().create(level);
            }
            if (dummyCar != null) {
                syncCarData(dummyCar, stack);
                entity = dummyCar;
                yOffset = 0.02F;
            }
        } else if (stack.is(ModItems.DRONE_SPAWN_ITEM.get())) {
            if (dummyDrone == null || dummyDrone.level() != level) {
                dummyDrone = ModEntities.DRONE.get().create(level);
            }
            if (dummyDrone != null) {
                syncDroneData(dummyDrone, stack);
                entity = dummyDrone;
                yOffset = 0.04F;
            }
        } else if (stack.is(ModItems.RC_TANK_ITEM.get())) {
            if (dummyTank == null || dummyTank.level() != level) {
                dummyTank = ModEntities.RC_TANK.get().create(level);
            }
            if (dummyTank != null) {
                syncTankData(dummyTank, stack);
                entity = dummyTank;
                yOffset = 0.02F;
            }
        } else if (stack.is(ModItems.RC_PLANE_ITEM.get())) {
            if (dummyPlane == null || dummyPlane.level() != level) {
                dummyPlane = ModEntities.RC_PLANE.get().create(level);
            }
            if (dummyPlane != null) {
                syncPlaneData(dummyPlane, stack);
                entity = dummyPlane;
                yOffset = 0.03F;
            }
        } else if (stack.is(ModItems.RC_BOAT_ITEM.get())) {
            if (dummyBoat == null || dummyBoat.level() != level) {
                dummyBoat = ModEntities.RC_BOAT.get().create(level);
            }
            if (dummyBoat != null) {
                syncBoatData(dummyBoat, stack);
                entity = dummyBoat;
                yOffset = 0.02F;
            }
        } else if (stack.is(ModItems.RC_SUBMARINE_ITEM.get())) {
            if (dummySubmarine == null || dummySubmarine.level() != level) {
                dummySubmarine = ModEntities.RC_SUBMARINE.get().create(level);
            }
            if (dummySubmarine != null) {
                syncSubmarineData(dummySubmarine, stack);
                entity = dummySubmarine;
                yOffset = 0.03F;
            }
        }

        if (entity != null) {
            poseStack.pushPose();
            poseStack.translate(0.0, yOffset, 0.0);
            if (yRotation != 0.0F) {
                poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
            }
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

    private static void syncCarData(RcCarEntity car, ItemStack stack) {
        String variantToSet = "default";
        int bodyColor = 0xFFFFFF;
        List<CarDecalData> decals = Collections.emptyList();
        String frontTire = "standard";
        String rearTire = "standard";

        // 1. 金床でのカスタム名によるバリアント指定
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().trim();
            CarConfig config = CarConfigManager.getConfig(customName);
            if (config != null && !"default".equalsIgnoreCase(config.id)) {
                variantToSet = config.id;
            }
        }

        // 2. アイテムのカスタムデータ内の明示的な Variant タグや塗装色
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                variantToSet = tag.getString("Variant");
            }
            if (tag.contains("BodyColor")) {
                bodyColor = tag.getInt("BodyColor");
            }
            if (tag.contains("Decals", 10)) {
                decals = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
            }
            if (tag.contains("FrontTire")) {
                frontTire = tag.getString("FrontTire");
            }
            if (tag.contains("RearTire")) {
                rearTire = tag.getString("RearTire");
            }
        }

        car.setVariantId(variantToSet);
        car.setBodyColor(bodyColor);
        car.setDecals(decals);
        car.setFrontTireType(frontTire);
        car.setRearTireType(rearTire);
    }

    private static void syncDroneData(DroneEntity drone, ItemStack stack) {
        String variantToSet = "default";
        List<CarDecalData> decals = Collections.emptyList();

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                variantToSet = tag.getString("Variant");
            }
            if (tag.contains("Decals", 10)) {
                decals = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
            }
        } else if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().trim().toLowerCase();
            if (DroneConfigManager.getAllConfigs().containsKey(customName)) {
                variantToSet = customName;
            }
        }

        drone.setVariantId(variantToSet);
        drone.setDecals(decals);
    }

    private static void syncTankData(RcTankEntity tank, ItemStack stack) {
        String variantToSet = "default";
        List<CarDecalData> decals = Collections.emptyList();

        if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().trim();
            TankConfig config = TankConfigManager.getConfig(customName);
            if (config != null && !"default".equalsIgnoreCase(config.id)) {
                variantToSet = config.id;
            }
        }

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                variantToSet = tag.getString("Variant");
            }
            if (tag.contains("Decals", 10)) {
                decals = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
            }
        }

        tank.setVariantId(variantToSet);
        tank.setDecals(decals);
    }

    private static void syncPlaneData(RcPlaneEntity plane, ItemStack stack) {
        String variantToSet = "default";
        List<CarDecalData> decals = Collections.emptyList();

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                variantToSet = tag.getString("Variant");
            }
            if (tag.contains("Decals", 10)) {
                decals = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
            }
        } else if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().trim().toLowerCase();
            if (PlaneConfigManager.getAllConfigs().containsKey(customName)) {
                variantToSet = customName;
            }
        }

        plane.setVariantId(variantToSet);
        plane.setDecals(decals);
    }

    private static void syncBoatData(RcBoatEntity boat, ItemStack stack) {
        String variantToSet = "default";
        List<CarDecalData> decals = Collections.emptyList();

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                variantToSet = tag.getString("Variant");
            }
            if (tag.contains("Decals", 10)) {
                decals = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
            }
        } else if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().trim().toLowerCase();
            if (BoatConfigManager.getAllConfigs().containsKey(customName)) {
                variantToSet = customName;
            }
        }

        boat.setVariantId(variantToSet);
        boat.setDecals(decals);
    }

    private static void syncSubmarineData(RcSubmarineEntity submarine, ItemStack stack) {
        String variantToSet = "default";
        List<CarDecalData> decals = Collections.emptyList();

        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Variant")) {
                variantToSet = tag.getString("Variant");
            }
            if (tag.contains("Decals", 10)) {
                decals = CarDecalData.deserializeDecals(tag.getCompound("Decals"));
            }
        } else if (stack.has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString().trim().toLowerCase();
            if (SubmarineConfigManager.getAllConfigs().containsKey(customName)) {
                variantToSet = customName;
            }
        }

        submarine.setVariantId(variantToSet);
        submarine.setDecals(decals);
    }
}
