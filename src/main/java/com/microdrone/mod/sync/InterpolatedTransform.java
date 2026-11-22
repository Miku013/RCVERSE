package com.microdrone.mod.sync;

/**
 * スナップショット補間によって算出された描画用トランスフォーム（位置・回転）
 */
public record InterpolatedTransform(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        boolean valid
) {
    public static final InterpolatedTransform INVALID = new InterpolatedTransform(0, 0, 0, 0, 0, 0, false);
}
