package com.microdrone.mod.client.controller;

import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 各RC機体（ドローン、カー、戦車、プレーン等）のクライアント側操縦・カメラ制御を担当するストラテジーインターフェース
 */
public interface RcVehicleClientController {

    /**
     * 操縦開始時の初期化処理
     */
    void onStart(Entity vehicle, LocalPlayer player, double startX, double startY, double startZ);

    /**
     * 操縦終了時のクリーンアップ処理
     */
    void onStop(Entity vehicle, LocalPlayer player);

    /**
     * 20Hz Tickごとの入力処理およびサーバーパケット送信
     */
    void handleTickInput(LocalPlayer player, Entity vehicle, InputSnapshot input, double startX, double startY, double startZ);

    /**
     * 描画フレーム単位（144Hz+）でのカメラ角度計算およびローカル姿勢同期
     */
    void computeCameraAngles(ViewportEvent.ComputeCameraAngles event, Entity vehicle, double startX, double startY, double startZ);

    /**
     * F5キー押下による視点モード切替
     */
    void toggleViewMode(Entity vehicle, LocalPlayer player);

    /**
     * 現在の視点モードを取得
     */
    RcViewMode getViewMode();

    /**
     * 現在の視点モードに応じたMinecraft標準カメラ種別を取得
     */
    CameraType getExpectedCameraType();

    /**
     * キー押下イベント（プレーンのRキー等、機体固有の機能キー用）
     */
    default void onKeyInput(int key, int action, Entity vehicle, LocalPlayer player) {}

    /**
     * マウスホイールスクロールイベント（ズーム等用）
     * @return イベントを消費してホットバースロット切替をキャンセルする場合は true
     */
    default boolean onMouseScroll(double scrollDeltaY, boolean isShiftDown, Entity vehicle, LocalPlayer player) {
        return false;
    }

    /**
     * マウスボタンイベント（中クリックリセット等用）
     * @return イベントを消費する場合は true
     */
    default boolean onMouseButton(int button, int action, int mods, Entity vehicle, LocalPlayer player) {
        return false;
    }

    /**
     * FOV（視野角/望遠）計算イベント
     */
    default void computeFov(ViewportEvent.ComputeFov event, Entity vehicle) {}

    /**
     * 操縦中のプレイヤー自身のモデルを描画するかどうか
     * （地上視点時は false でプロポを持ったプレイヤーを表示、FPV/チェイス時は true で透明化）
     */
    default boolean shouldHidePlayerBody() {
        return getViewMode() != RcViewMode.GROUND;
    }
}
