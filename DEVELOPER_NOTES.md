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

## 4. 次回実装タスク: 「視点別サウンド両立方式」の完全設計

### 🎯 課題と背景
- **現状の症状**: 操作を開始した位置（プレイヤーの身体の立ち位置）から戦車がある程度離れると、主砲発射音などの動作音が聞こえなくなる。
- **原因**: 
  - ラジコン操作中、プレイヤーの身体は `startX, startY, startZ` に留まっている。
  - Minecraft サーバーの `ServerLevel.playSound` は、音の発生地点から「プレイヤーの身体の座標」までの距離で可聴判定を行う（デフォルト `16m × volume`）。
  - そのため、戦車が20m以上離れると、サーバーが「プレイヤーは音の届かない範囲にいる」と判定してサウンドパケットの送信をカットしてしまう。
  - チェイス視点やガンナー視点でカメラが戦車の真後ろにあっても、パケット自体が届かないため無音になる。

### 🛠️ 次回実装する「視点別サウンド両立方式」の具体策

1. **車載チェイス視点 (`CHASE_CAR`) ＆ ガンナー視点 (`GUNNER_AIM`)**:
   - カメラは戦車のすぐ後ろ（2m）や砲塔内部にある。
   - 戦車が初期位置から何百メートル離れていても、操縦者には **「車載マイク（プロポFPV）直接再生方式」** で常に大迫力の至近距離サウンド（音量100%）を鳴らす。
   - 実装方法: クライアント側（`locallyControlled`）の射撃トリガー時、または専用パケット受信時に `Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(...))` や戦車位置でのローカルサウンド再生を行う。

2. **地上プロポ視点 (`GROUND_RC`)**:
   - プレイヤーは地上から遠くの戦車を見守っている。
   - 本物のラジコン同様、**「可聴範囲拡大による距離減衰方式」**（`volume = 4.0f` 〜 `8.0f`）を適用。
   - プレイヤーから戦車までの距離に応じて、遠い時は「パシュッ…（遠くの乾いた音）」、近い時は「ドォン！」と自然にフェードアウト・減衰するリアルな音響にする。

3. **周囲の他プレイヤー・マルチプレイ対応**:
   - サーバー側の `serverLevel.playSound` の音量を `volume = 4.0f`（可聴半径 64m）に設定し、近くにいる別プレイヤーにも迫力ある銃声が届くようにする。

---

## 5. 関連ファイル一覧
- `src/main/java/com/microdrone/mod/entity/RcTankEntity.java` (戦車エンティティ、物理、射撃、接地傾斜)
- `src/main/java/com/microdrone/mod/client/input/ClientDroneInputHandler.java` (視点管理、マウス入力、カメラアングル)
- `src/main/java/com/microdrone/mod/client/gui/TankGunnerHudOverlay.java` (チェイス＆ガンナーHUD、仰角・方位・レーダー)
- `src/main/java/com/microdrone/mod/client/renderer/RcTankGeoRenderer.java` (GeckoLib レンダラー、ピッチ・ロール姿勢反映)
- `src/main/java/com/microdrone/mod/client/model/RcTankGeoModel.java` (GeckoLib モデル、砲塔旋回・履帯回転)
- `src/main/java/com/microdrone/mod/sound/ModSounds.java` (カスタム発砲音登録)
- `src/main/resources/assets/microdrone/sounds.json` (サウンド定義)
