package com.microdrone.mod.sync;

/**
 * スナップショット補間（Snapshot Interpolation）をサポートする車両エンティティの共通インターフェース
 */
public interface ISnapshotInterpolated {
    /**
     * 車両が保持するスナップショットバッファを取得
     */
    VehicleSnapshotBuffer getSnapshotBuffer();

    /**
     * 自プレイヤー（ローカルクライアント）が現在操縦中かどうか
     */
    boolean isLocallyControlled();
}

