package com.microdrone.mod.block.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * 道路生成（ヘアピンカーブ・スプライン道路）の設定パラメータ
 */
public class RoadSplineData {
    /**
     * 道路生成モード（将来他モードも拡張可能）
     */
    public enum RoadMode {
        ROAD(0, "通常道路"),
        JUMP_RAMP(1, "ジャンプ台"),
        LOOP_360(2, "360°ループ");

        private final int id;
        private final String displayName;

        RoadMode(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public RoadMode next() {
            RoadMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public static RoadMode fromId(int id) {
            for (RoadMode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }
            return ROAD;
        }
    }

    /**
     * 道路路面タイプ
     */
    public enum RoadSurfaceType {
        ASPHALT(0, "asphalt", "アスファルト"),
        DIRT(1, "dirt", "ダート / オフロード"),
        ICE(2, "ice", "アイス / 氷上"),
        PUDDLE(3, "puddle", "水たまり");

        private final int id;
        private final String name;
        private final String displayName;

        RoadSurfaceType(int id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public RoadSurfaceType next() {
            RoadSurfaceType[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }

        public static RoadSurfaceType fromId(int id) {
            for (RoadSurfaceType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return ASPHALT;
        }
    }

    // 規定値
    public static final float DEFAULT_ROAD_WIDTH = 5.0f;
    public static final float MIN_ROAD_WIDTH = 4.0f;
    public static final float MAX_ROAD_WIDTH = 9.0f;

    public static final float DEFAULT_MAX_BANK = 0.0f;
    public static final float MIN_MAX_BANK = 0.0f;
    public static final float MAX_MAX_BANK = 45.0f;

    public static final float DEFAULT_LOOP_HEIGHT = 10.0f;
    public static final float MIN_LOOP_HEIGHT = 6.0f;  // 破綻しない最低ループ直径(半径3.0m)
    public static final float MAX_LOOP_HEIGHT = 32.0f;

    public static final float DEFAULT_LOOP_BOOST = 1.0f;
    public static final float MIN_LOOP_BOOST = 1.0f;
    public static final float MAX_LOOP_BOOST = 3.0f;

    // パラメータ
    private RoadMode roadMode = RoadMode.ROAD;
    private RoadSurfaceType surfaceType = RoadSurfaceType.ASPHALT;
    private float roadWidth = DEFAULT_ROAD_WIDTH;
    private float maxBankAngle = DEFAULT_MAX_BANK;
    private float loopHeight = DEFAULT_LOOP_HEIGHT;
    private float loopBoostMultiplier = DEFAULT_LOOP_BOOST;
    private float startBankAngle = 0.0f;
    private float endBankAngle = 0.0f;
    private int turnDirection = 0; // 0=自動 (AUTO), 1=左 (LEFT), 2=右 (RIGHT)
    private float controlPointDistance = 12.0f; // ベジェ制御点の突き出し距離 (m)
    private int segments = 64; // 分割ステップ数 (16〜128)
    private boolean arcFitMode = true; // 真円（円弧）自動フィットモード
    private boolean isPreview = true; // プレビュー中か
    private boolean isGenerated = false; // 正式生成済みか

    public RoadSplineData() {
    }

    public RoadMode getRoadMode() {
        return roadMode;
    }

    public void setRoadMode(RoadMode roadMode) {
        this.roadMode = roadMode != null ? roadMode : RoadMode.ROAD;
    }

    public RoadSurfaceType getSurfaceType() {
        return surfaceType;
    }

    public void setSurfaceType(RoadSurfaceType surfaceType) {
        this.surfaceType = surfaceType != null ? surfaceType : RoadSurfaceType.ASPHALT;
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

    public float getLoopHeight() {
        return loopHeight;
    }

    public void setLoopHeight(float loopHeight) {
        this.loopHeight = Math.max(MIN_LOOP_HEIGHT, Math.min(MAX_LOOP_HEIGHT, loopHeight));
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

    public float getLoopBoostMultiplier() {
        return loopBoostMultiplier;
    }

    public void setLoopBoostMultiplier(float loopBoostMultiplier) {
        this.loopBoostMultiplier = Math.max(MIN_LOOP_BOOST, Math.min(MAX_LOOP_BOOST, loopBoostMultiplier));
    }

    public boolean isArcFitMode() {
        return arcFitMode;
    }

    public void setArcFitMode(boolean arcFitMode) {
        this.arcFitMode = arcFitMode;
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
        tag.putInt("RoadMode", this.roadMode.getId());
        tag.putInt("SurfaceType", this.surfaceType.getId());
        tag.putFloat("RoadWidth", this.roadWidth);
        tag.putFloat("MaxBankAngle", this.maxBankAngle);
        tag.putFloat("LoopHeight", this.loopHeight);
        tag.putFloat("LoopBoostMultiplier", this.loopBoostMultiplier);
        tag.putFloat("StartBankAngle", this.startBankAngle);
        tag.putFloat("EndBankAngle", this.endBankAngle);
        tag.putInt("TurnDirection", this.turnDirection);
        tag.putFloat("ControlPointDistance", this.controlPointDistance);
        tag.putInt("Segments", this.segments);
        tag.putBoolean("ArcFitMode", this.arcFitMode);
        tag.putBoolean("IsPreview", this.isPreview);
        tag.putBoolean("IsGenerated", this.isGenerated);
    }

    public void loadFromNbt(CompoundTag tag) {
        this.roadMode = tag.contains("RoadMode") ? RoadMode.fromId(tag.getInt("RoadMode")) : RoadMode.ROAD;
        this.surfaceType = tag.contains("SurfaceType") ? RoadSurfaceType.fromId(tag.getInt("SurfaceType")) : RoadSurfaceType.ASPHALT;
        if (tag.contains("RoadWidth")) this.roadWidth = tag.getFloat("RoadWidth");
        if (tag.contains("MaxBankAngle")) this.maxBankAngle = tag.getFloat("MaxBankAngle");
        if (tag.contains("LoopHeight")) setLoopHeight(tag.getFloat("LoopHeight"));
        if (tag.contains("LoopBoostMultiplier")) setLoopBoostMultiplier(tag.getFloat("LoopBoostMultiplier"));
        if (tag.contains("StartBankAngle")) this.startBankAngle = tag.getFloat("StartBankAngle");
        if (tag.contains("EndBankAngle")) this.endBankAngle = tag.getFloat("EndBankAngle");
        if (tag.contains("TurnDirection")) this.turnDirection = tag.getInt("TurnDirection");
        if (tag.contains("ControlPointDistance")) this.controlPointDistance = tag.getFloat("ControlPointDistance");
        if (tag.contains("Segments")) this.segments = tag.getInt("Segments");
        this.arcFitMode = tag.contains("ArcFitMode") && tag.getBoolean("ArcFitMode");
        if (tag.contains("IsPreview")) this.isPreview = tag.getBoolean("IsPreview");
        if (tag.contains("IsGenerated")) this.isGenerated = tag.getBoolean("IsGenerated");
    }
}

