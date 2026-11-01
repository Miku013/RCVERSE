# Micro Drone MOD - 開発引き継ぎノート (DEVELOPER_NOTES.md)

**最終更新日**: 2026-11-01  
**対象環境**: Minecraft 1.21.1 / NeoForge 21.1.244 / GeckoLib 4.7.7  
**対象ディレクトリ**: `micro_drone_mod`  

---

## 1. プロジェクト概要
Minecraft 1.21.1 (NeoForge) 向けの超リアル・高機能ラジコン MOD。  
プレイヤーは手持ちのプロポ（コントローラー）を使って、以下の多彩なRCビークルを遠隔操縦可能：
1. **マイクロFPVドローン**: 6軸ジャイロ風アクロバティック飛行、144Hz超滑らか視線追従、FPVゴーグルHUD。
2. **マイクロRCカー**: ドリフト物理、超信地旋回、サイドブレーキ、ミニターボ、カウンターステア。
3. **マイクロRC戦車**: 独立履帯駆動、超信地旋回、360度砲塔旋回＆仰俯角、主砲射撃、4点接地ピッチ・ロール傾斜物理、視点切替（地上プロポ / 車載チェイス / ガンナー照準FPS）。

---

## 2. 直近の進捗状況（動作確認済み・実装完了機能）

### A. マイクロRC戦車（RcTankEntity）の物理・描画・操作
- [x] **接地ポイント4点方式（地形追従ピッチ・ロール傾斜物理）**:
  - 車体四隅（左前・右前・左後・右後）の接地高さをレイキャストサンプリングし、ピッチ角・ロール角を計算（$\pm 28^\circ$ クランプ、Lerp補間）。
  - `RcTankGeoRenderer` で `Axis.XP.rotationDegrees(pitch)` および `Axis.ZP.rotationDegrees(roll)` を適用し、斜面や段差でリアルに車体が傾斜。
- [x] **カスタム主砲発射音の統合**:
  - ユーザー提供の `uploaded_media_1793471151842.ogg` を `src/main/resources/assets/microdrone/sounds/tank_cannon_fire.ogg` として統合。
  - `ModSounds.TANK_CANNON_FIRE` を登録し、主砲発射時に再生。
- [x] **3段階カメラ視点切り替えサイクル（F5キー）**:
  1. **地上プロポ視点 (`GROUND_RC`)**:
     - プロポで戦車をバインドした瞬間の初期視点。
     - 手元にプロポが見え、足元の戦車を見下ろす実機RC視点。
     - カメラ角度のみを戦車（Look-At）に向け、プレイヤーのマウス入力を打ち消さないことで、**地上プロポ視点のままマウスで戦車の砲塔が144Hz遅延ゼロで旋回**。
  2. **車載チェイス視点 (`CHASE_CAR`)**:
     - `mc.setCameraEntity(tank)` ＋ `CameraType.THIRD_PERSON_BACK` により、**戦車モデルが100%確実に消えずに表示され、戦車の背後から完全自動追従**。
     - マウス操作で戦車の周囲を360度自由に見回し可能。
     - `moveCameraSafe`（リフレクション）により、バニラ4mから2.2mの迫力ある見下ろしクォータービューに調整。
  3. **ガンナー照準視点 (`GUNNER_AIM`)**:
     - `mc.setCameraEntity(tank)` ＋ `CameraType.FIRST_PERSON` により、戦車内部ポリゴンを隠して前方視界をクリアに保ち、砲塔同軸FPSを実現。
- [x] **高機能ミリタリーHUDオーバーレイ（チェイス視点 ＆ ガンナー視点）**:
  - **砲身仰角インジケーター**: `+35°`（最大仰角）〜 `0°`（水平）〜 `-15°`（最大俯角）の垂直スケールバーとリアルタイムデジタル数値（例: `ELEV 仰角: +12.5°`）。
  - **上部デュアルコンパス**: `車体方位 (HULL: 045° 北東)`、`主砲方位 (GUN: 090° 東)`、`砲塔相対旋回角 (旋回: +45°)` を同時表示。
  - **車体＆砲身の向きミニレーダー**: 画面左上に北基準のミニマップを配置し、車体の向き（四角アイコン）と主砲の向き（グリーン照射ライン）をグラフィカルに回転表示。
  - **その他**: 主砲次弾装填カウントダウン＆プログレスバー、バッテリー残量、車速（km/h）、通信距離（m）、電波強度ピクト。
- [x] **電波距離監視（250m）＆ チャンク配信（`DroneChunkManager`）**:
  - 戦車が250m離れた場合のフェイルセーフ自動停止、および遠距離走行時のチャンクアンロード防止。
- [x] **言語ファイルの JSON 構文修復**:
  - `ja_jp.json` と `en_us.json` に混入していた `` `r`n `` を完全除去し、日本語が正常に表示されるように修復。

---

## 3. 重要な技術仕様・特記事項（次回AIへの引き継ぎ要点）

### ⚠️ Minecraft 1.21.1 のカメラ・描画・サウンドの落とし穴

1. **カメラエンティティとモデル描画（不可視化問題の真相）**:
   - Minecraft の `LevelRenderer` は、**一人称視点（`CameraType.FIRST_PERSON`）の時、`mc.getCameraEntity()` に設定されたエンティティを「自分自身」と判定して描画をスキップ（非表示）** する。
   - そのため、チェイス視点で戦車モデルを表示しながら追従させるには、**必ず `mc.options.setCameraType(CameraType.THIRD_PERSON_BACK)` をセットする**必要がある。
   - `onClientTick` で一括して `FIRST_PERSON` に戻してしまうとチェイス視点が壊れるため、以下のようにモード別に維持すること：
     ```java
     CameraType expected = (isGroundVehiclePiloting() && carCameraMode == RcCameraMode.CHASE_CAR)
             ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
     if (mc.options.getCameraType() != expected) mc.options.setCameraType(expected);
     ```

2. **地上プロポ視点でのマウス砲塔旋回**:
   - `ViewportEvent.ComputeCameraAngles` で `event.setYaw(targetYaw)` と `event.setPitch(targetPitch)` を設定すれば、画面の描画向きのみが戦車（Look-At）を捉える。
   - ここで **`mc.player.setYRot(targetYaw)` を呼んではならない**。呼んでしまうとプレイヤーのマウス入力が毎フレーム消去され、マウスで砲塔が回せなくなる。
   - `tank.clientTurretYaw` と `clientCannonPitch` を直接更新することで、クライアント側の GeckoLib レンダラー（`RcTankGeoModel`）へ遅延ゼロ（144Hz）で回転を伝達する。

---

- [x] **広域サウンド伝播・視点別音響両立（可聴半径256m化）**:
  - `RcTankEntity.fireCannon()` の発砲音音量を `volume = 16.0f`（可聴半径256m）に拡張。
  - 電波限界（250m）のどこに戦車がいようと、サーバーから操縦者へパケットが確実に到達。
  - **チェイス/ガンナー視点**: リスナーが戦車至近（0〜2m）にあるため、距離減衰せず常に100%の大迫力至近距離音で再生。
  - **地上プロポ視点**: リスナーがプレイヤーの頭にあるため、OpenALにより戦車との距離に応じて自然に減衰（遠くなら小さく、手前ならド迫力）。
  - 二重再生（エコー）を起こさず、バニラサウンドエンジンの仕様を完璧に活用して両立を達成。
  - RCカー（`RcCarEntity`）のドリフト音・ミニターボ音も同様に可聴範囲を拡張（192m）。

---

## 5. 関連ファイル一覧
- `src/main/java/com/microdrone/mod/entity/RcTankEntity.java` (戦車エンティティ、物理、射撃、接地傾斜)
- `src/main/java/com/microdrone/mod/client/input/ClientDroneInputHandler.java` (視点管理、マウス入力、カメラアングル)
- `src/main/java/com/microdrone/mod/client/gui/TankGunnerHudOverlay.java` (チェイス＆ガンナーHUD、仰角・方位・レーダー)
- `src/main/java/com/microdrone/mod/client/renderer/RcTankGeoRenderer.java` (GeckoLib レンダラー、ピッチ・ロール姿勢反映)
- `src/main/java/com/microdrone/mod/client/model/RcTankGeoModel.java` (GeckoLib モデル、砲塔旋回・履帯回転)
- `src/main/java/com/microdrone/mod/sound/ModSounds.java` (カスタム発砲音登録)
- `src/main/resources/assets/microdrone/sounds.json` (サウンド定義)
