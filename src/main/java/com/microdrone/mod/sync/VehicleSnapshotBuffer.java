package com.microdrone.mod.sync;

import net.minecraft.util.Mth;

import java.util.LinkedList;

/**
 * 車両状態スナップショットバッファ & 補間エンジン（第1段階：Snapshot Interpolation）
 * 
 * - 受信したスナップショットを時系列順に保持
 * - クライアント描画時刻 (currentTime - interpolationDelay) に基づく滑らかな毎フレーム補間
 * - 角度（Yaw/Pitch/Roll）の最短回転補間
 * - 軽微なパケット遅延に対する速度ベース前方予測（Dead Reckoningフォールバック）
 * - 大幅なズレ・ワープ時のテレポート対応
 */
public class VehicleSnapshotBuffer {

    private static final int MAX_SNAPSHOTS = 30; // 約1.5秒分保持
    public static final long DEFAULT_INTERPOLATION_DELAY_MS = 100L; // 提案書調整値: 100ms (2 Ticks分)
    private static final double TELEPORT_DISTANCE_SQ = 64.0; // 8m以上の瞬間移動は補間せずテレポート

    private final LinkedList<VehicleSnapshot> snapshots = new LinkedList<>();

    /**
     * 新しいスナップショットを追加
     */
    public synchronized void push(VehicleSnapshot snapshot) {
        if (!snapshots.isEmpty()) {
            VehicleSnapshot latest = snapshots.getLast();
            // 古いまたは同一Tickのパケットが逆順に届いた場合はスキップ
            if (snapshot.serverTick() <= latest.serverTick() && snapshot.recvTimeMs() <= latest.recvTimeMs()) {
                return;
            }
        }

        snapshots.addLast(snapshot);

        // バッファ上限を超えた古いスナップショットを削除
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.removeFirst();
        }
    }

    /**
     * 指定された目標描画時刻における補間座標・姿勢を算出
     * 
     * @param renderTimeMs 目標描画時刻 (System.currentTimeMillis() - interpolationDelayMs)
     * @return 補間された座標および回転
     */
    public synchronized InterpolatedTransform interpolate(long renderTimeMs) {
        if (snapshots.isEmpty()) {
            return InterpolatedTransform.INVALID;
        }

        // スナップショットが1件のみの場合はその値をそのまま返す
        if (snapshots.size() == 1) {
            VehicleSnapshot single = snapshots.getFirst();
            return new InterpolatedTransform(single.x(), single.y(), single.z(), single.yaw(), single.pitch(), single.roll(), true);
        }

        VehicleSnapshot oldest = snapshots.getFirst();
        VehicleSnapshot newest = snapshots.getLast();

        // 1. 描画時刻が最古データより古い場合（バッファが溜まり始めた初期状態など）
        if (renderTimeMs <= oldest.recvTimeMs()) {
            return new InterpolatedTransform(oldest.x(), oldest.y(), oldest.z(), oldest.yaw(), oldest.pitch(), oldest.roll(), true);
        }

        // 2. 描画時刻が最新データより新しい場合（パケット到着遅延・ジッター発生時）
        if (renderTimeMs >= newest.recvTimeMs()) {
            double dtSec = (renderTimeMs - newest.recvTimeMs()) / 1000.0;
            // 最大 0.15秒 (150ms) まで速度ベクトルを使った前方予測（Dead Reckoning）でカクつきを防止
            if (dtSec <= 0.15) {
                double predX = newest.x() + newest.vx() * dtSec;
                double predY = newest.y() + newest.vy() * dtSec;
                double predZ = newest.z() + newest.vz() * dtSec;
                return new InterpolatedTransform(predX, predY, predZ, newest.yaw(), newest.pitch(), newest.roll(), true);
            }
            return new InterpolatedTransform(newest.x(), newest.y(), newest.z(), newest.yaw(), newest.pitch(), newest.roll(), true);
        }

        // 3. 通常補間: renderTimeMs を挟む2つのスナップショット (s0, s1) を探索
        VehicleSnapshot s0 = oldest;
        VehicleSnapshot s1 = newest;

        for (int i = 0; i < snapshots.size() - 1; i++) {
            VehicleSnapshot cur = snapshots.get(i);
            VehicleSnapshot nxt = snapshots.get(i + 1);
            if (cur.recvTimeMs() <= renderTimeMs && renderTimeMs <= nxt.recvTimeMs()) {
                s0 = cur;
                s1 = nxt;
                break;
            }
        }

        long timeDiff = s1.recvTimeMs() - s0.recvTimeMs();
        if (timeDiff <= 0) {
            return new InterpolatedTransform(s1.x(), s1.y(), s1.z(), s1.yaw(), s1.pitch(), s1.roll(), true);
        }

        // テレポート判定（障害物すり抜けやリスポーンなど）
        double dx = s1.x() - s0.x();
        double dy = s1.y() - s0.y();
        double dz = s1.z() - s0.z();
        if ((dx * dx + dy * dy + dz * dz) > TELEPORT_DISTANCE_SQ) {
            return new InterpolatedTransform(s1.x(), s1.y(), s1.z(), s1.yaw(), s1.pitch(), s1.roll(), true);
        }

        // 補間係数 alpha (0.0 〜 1.0)
        double alpha = (double) (renderTimeMs - s0.recvTimeMs()) / (double) timeDiff;
        alpha = Mth.clamp(alpha, 0.0, 1.0);

        // 位置の線形補間
        double posX = Mth.lerp(alpha, s0.x(), s1.x());
        double posY = Mth.lerp(alpha, s0.y(), s1.y());
        double posZ = Mth.lerp(alpha, s0.z(), s1.z());

        // 姿勢（Yaw / Pitch / Roll）の滑らかな補間（Yawは360度ループ最短回転）
        float yaw = Mth.rotLerp((float) alpha, s0.yaw(), s1.yaw());
        float pitch = Mth.lerp((float) alpha, s0.pitch(), s1.pitch());
        float roll = Mth.lerp((float) alpha, s0.roll(), s1.roll());

        return new InterpolatedTransform(posX, posY, posZ, yaw, pitch, roll, true);
    }

    /**
     * バッファをクリア（リスポーン・回収時など）
     */
    public synchronized void clear() {
        this.snapshots.clear();
    }
}
