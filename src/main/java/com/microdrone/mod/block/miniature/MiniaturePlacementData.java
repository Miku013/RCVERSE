package com.microdrone.mod.block.miniature;

import net.minecraft.nbt.CompoundTag;

/**
 * 1つのスロットに配置された小物のデータ
 */
public class MiniaturePlacementData {
    private final int slotId;
    private final String miniatureId;
    private float yaw;
    private float scale;

    public MiniaturePlacementData(int slotId, String miniatureId, float yaw, float scale) {
        this.slotId = slotId;
        this.miniatureId = miniatureId;
        this.yaw = yaw;
        this.scale = scale;
    }

    public static int getSlotFromHit(double hitX, double hitZ) {
        // hitX, hitZ は 0.0〜1.0 の範囲
        int col = Math.max(0, Math.min(2, (int) (hitX * 3.0)));
        int row = Math.max(0, Math.min(2, (int) (hitZ * 3.0)));
        return row * 3 + col;
    }

    public static float getSlotCenterX(int slotId) {
        int col = slotId % 3;
        return (col + 0.5f) / 3.0f;
    }

    public static float getSlotCenterZ(int slotId) {
        int row = slotId / 3;
        return (row + 0.5f) / 3.0f;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SlotId", slotId);
        tag.putString("MiniatureId", miniatureId);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Scale", scale);
        return tag;
    }

    public static MiniaturePlacementData load(CompoundTag tag) {
        int slotId = tag.getInt("SlotId");
        String miniatureId = tag.getString("MiniatureId");
        float yaw = tag.getFloat("Yaw");
        float scale = tag.contains("Scale") ? tag.getFloat("Scale") : 0.33f;
        return new MiniaturePlacementData(slotId, miniatureId, yaw, scale);
    }

    public int getSlotId() {
        return slotId;
    }

    public String getMiniatureId() {
        return miniatureId;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getXOffset() {
        return getSlotCenterX(slotId);
    }

    public float getZOffset() {
        return getSlotCenterZ(slotId);
    }
}
