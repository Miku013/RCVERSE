package com.microdrone.mod.block.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * 道路生成（ヘアピンカーブ・スプライン道路）の設定パラメータ
 */
public class RoadSplineData {
    // 規定値
    public static final float DEFAULT_ROAD_WIDTH = 5.0f;
    public static final float MIN_ROAD_WIDTH = 4.0f;
    public static final float MAX_ROAD_WIDTH = 7.0f;

    public static final float DEFAULT_MAX_BANK = 0.0f;
    public static final float MIN_MAX_BANK = 0.0f;
    public static final float MAX_MAX_BANK = 45.0f;

    // パラメータ
    private float roadWidth = DEFAULT_ROAD_WIDTH;
    private float maxBankAngle = DEFAULT_MAX_BANK;
    private float startBankAngle = 0.0f;
    private float endBankAngle = 0.0f;
    private int turnDirection = 0; // 0=自動 (AUTO), 1=左 (LEFT), 2=右 (RIGHT)
    private float controlPointDistance = 12.0f; // ベジェ制御点の突き出し距離 (m)
    private int segments = 32; // 分割ステップ数
    private boolean isPreview = true; // プレビュー中か
    private boolean isGenerated = false; // 正式生成済みか

    public RoadSplineData() {
    }

    public float getRoadWidth() {
        return roadWidth;
    }

    public void setRoadWidth(float roadWidth) {
        this.roadWidth = Math.max(MIN_ROAD_WIDTH, Math.min(MAX_ROAD_WIDTH, roadWidth));
    }

    public float getMaxBankAngle() {
        return maxBankAngle;
    }

    public void setMaxBankAngle(float maxBankAngle) {
        this.maxBankAngle = Math.max(MIN_MAX_BANK, Math.min(MAX_MAX_BANK, maxBankAngle));
    }

    public float getStartBankAngle() {
        return startBankAngle;
    }

    public void setStartBankAngle(float startBankAngle) {
        this.startBankAngle = startBankAngle;
    }

    public float getEndBankAngle() {
        return endBankAngle;
    }

    public void setEndBankAngle(float endBankAngle) {
        this.endBankAngle = endBankAngle;
    }

    public int getTurnDirection() {
        return turnDirection;
    }

    public void setTurnDirection(int turnDirection) {
        this.turnDirection = turnDirection;
    }

    public float getControlPointDistance() {
        return controlPointDistance;
    }

    public void setControlPointDistance(float controlPointDistance) {
        this.controlPointDistance = Math.max(2.0f, Math.min(64.0f, controlPointDistance));
    }

    public int getSegments() {
        return segments;
    }

    public void setSegments(int segments) {
        this.segments = Math.max(8, Math.min(128, segments));
    }

    public boolean isPreview() {
        return isPreview;
    }

    public void setPreview(boolean preview) {
        isPreview = preview;
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    public void setGenerated(boolean generated) {
        isGenerated = generated;
    }

    public void saveToNbt(CompoundTag tag) {
        tag.putFloat("RoadWidth", this.roadWidth);
        tag.putFloat("MaxBankAngle", this.maxBankAngle);
        tag.putFloat("StartBankAngle", this.startBankAngle);
        tag.putFloat("EndBankAngle", this.endBankAngle);
        tag.putInt("TurnDirection", this.turnDirection);
        tag.putFloat("ControlPointDistance", this.controlPointDistance);
        tag.putInt("Segments", this.segments);
        tag.putBoolean("IsPreview", this.isPreview);
        tag.putBoolean("IsGenerated", this.isGenerated);
    }

    public void loadFromNbt(CompoundTag tag) {
        if (tag.contains("RoadWidth")) this.roadWidth = tag.getFloat("RoadWidth");
        if (tag.contains("MaxBankAngle")) this.maxBankAngle = tag.getFloat("MaxBankAngle");
        if (tag.contains("StartBankAngle")) this.startBankAngle = tag.getFloat("StartBankAngle");
        if (tag.contains("EndBankAngle")) this.endBankAngle = tag.getFloat("EndBankAngle");
        if (tag.contains("TurnDirection")) this.turnDirection = tag.getInt("TurnDirection");
        if (tag.contains("ControlPointDistance")) this.controlPointDistance = tag.getFloat("ControlPointDistance");
        if (tag.contains("Segments")) this.segments = tag.getInt("Segments");
        if (tag.contains("IsPreview")) this.isPreview = tag.getBoolean("IsPreview");
        if (tag.contains("IsGenerated")) this.isGenerated = tag.getBoolean("IsGenerated");
    }
}

