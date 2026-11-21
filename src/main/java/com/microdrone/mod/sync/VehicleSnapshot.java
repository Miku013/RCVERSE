package com.microdrone.mod.sync;

/**
 * マルチプレイ同期用 車両状態スナップショット（第1段階：Snapshot Interpolation）
 * 
 * サーバーから受信した1時点における車両の正確な物理状態（位置・姿勢・速度）。
 */
public record VehicleSnapshot(
        long recvTimeMs,     // クライアント側でのパケット受信タイムスタンプ (ミリ秒)
        int serverTick,      // サーバー側Tick
        double x,
        double y,
        double z,
        float yaw,           // 水平旋回角 (度)
        float pitch,         // 前後傾斜角 (度)
        float roll,          // 左右横傾斜角 (度)
        double vx,           // 速度ベクトルX
        double vy,           // 速度ベクトルY
        double vz            // 速度ベクトルZ
) {}
