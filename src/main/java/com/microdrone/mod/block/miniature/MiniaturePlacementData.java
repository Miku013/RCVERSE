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

    public static final int GRID_SIZE = 5;
    public static final int TOTAL_SLOTS = GRID_SIZE * GRID_SIZE; // 25スロット
    public static final float DEFAULT_CONE_SCALE = 0.165f; // 前回の1/2サイズ (RCカー比率)

    public static int getSlotFromHit(double hitX, double hitZ) {
        // hitX, hitZ は 0.0〜1.0 の範囲
        int col = Math.max(0, Math.min(GRID_SIZE - 1, (int) (hitX * GRID_SIZE)));
        int row = Math.max(0, Math.min(GRID_SIZE - 1, (int) (hitZ * GRID_SIZE)));
        return row * GRID_SIZE + col;
    }

    public static float getSlotCenterX(int slotId) {
        int col = slotId % GRID_SIZE;
        return (col + 0.5f) / (float) GRID_SIZE;
    }

    public static float getSlotCenterZ(int slotId) {
        int row = slotId / GRID_SIZE;
        return (row + 0.5f) / (float) GRID_SIZE;
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
        float scale = tag.contains("Scale") ? tag.getFloat("Scale") : DEFAULT_CONE_SCALE;
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

