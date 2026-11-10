package com.microdrone.mod.physics;

/**
 * RCカーの走行物理モード
 * - RACE: 従来の遊びやすいマリオカート式ドリフト・補助ありモード
 * - REAL: 前後/横速度分離・可変グリップ・荷重移動によるリアル物理モード
 */
public enum DrivingMode {
    RACE(false, "RACE"),
    REAL(true, "REAL");

    private final boolean realMode;
    private final String label;

    DrivingMode(boolean realMode, String label) {
        this.realMode = realMode;
        this.label = label;
    }

    public boolean isRealMode() {
        return this.realMode;
    }

    public String getLabel() {
        return this.label;
    }

    public static DrivingMode fromBoolean(boolean isReal) {
        return isReal ? REAL : RACE;
    }
}
