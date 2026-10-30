# Micro Drone MOD - 開発引き継ぎノート (DEVELOPER_NOTES.md)

最終更新日: 2026-10-28
対象環境: Minecraft 1.21.1 / NeoForge 21.1.244 / GeckoLib 4.7.7
対象ディレクトリ: `micro_drone_mod`

---

## 1. プロジェクト概要
Minecraft 1.21.1（NeoForge）向けの超小型FPVマイクロドローン MOD。
プレイヤーは手持ちのコントローラーを使ってドローンを遠隔操縦し、ドローン視点（FPVカメラ）でワールドを滑らかに飛行・探索できる。
今後のアップデートでRCカー、RCボート、RCプレーン等の多様なラジコン機種を追加可能にする拡張設計を採用。

### 設計方針の決定（重要）
- **パターンA（アバター分離＋初期位置テレポート復元方式）** を採用。
  - 操縦開始時：プレイヤー初期位置に身代わりダミー人形（`ArmorStand`）を配置し、初期位置チャンクを保護。
  - 操縦中：ドローンを飛行させ、サーバー側の `ServerPlayer` をドローン座標へ毎tick追従移動（バニラのチャンク配信・エンティティ追跡を維持）。
  - 操縦終了時：初期位置へ `player.teleportTo` で完全復元し、身代わりダミーを消去。
- ※パターンC（完全ダミー実体移動）はチャンク境界で切断・描画破綻を起こしたため**廃止済み**。必ずパターンAを維持すること。

---

## 2. 現在の進捗状況（動作確認・実装済み機能）

### 物理・描画・操作系
- [x] **滑らかなFPV物理飛行**: 前後移動、左右平行移動（ストライフ）、垂直昇降、ホバリングゆらぎ、慣性減衰。
- [x] **144Hz+ 超滑らか視線追従**: `ViewportEvent.ComputeCameraAngles` により、マウス操作による視線旋回が毎フレーム補間され、カクつき（ジッター）ゼロで動作。
- [x] **視界・描画の最適化**:
  - ドローン視点中の手持ちアイテム・プレイヤー体は `RenderHandEvent` / `RenderPlayerEvent.Pre` で完全非表示化。
  - 前後識別LED（前:緑、後:赤）および機首カメラをドローンモデルに搭載。
  - 移動時の自然な前傾/後傾・A/Dバンク傾きロール（`DroneRenderer`）。
- [x] **操縦中の誤干渉・キックの完全遮断**:
  - `InputEvent.InteractionKeyMappingTriggered`、`PlayerInteractEvent`、`AttackEntityEvent` を操縦中すべてキャンセル。ブロック破壊・設置、ドア開閉、自プレイヤー/ダミー人形への誤攻撃によるサーバーキックを100%防止。
- [x] **右クリック終了の条件判定**:
  - 手に **「ドローンコントローラー（`DroneControllerItem`）」を持っている時のみ** 右クリックで操縦終了（他アイテム所持時は誤終了を防止）。

### 遠距離飛行・アバター復元系
- [x] **遠距離（300m+）飛行とチャンク描画の完全安定**:
  - サーバー側 `ServerPlayer` がドローン座標へ追従移動（`pilot.absMoveTo` / `getChunkSource().move`）するため、バニラのエンティティ追跡範囲（128m）によるドローン消失（`ENTITY_LEAVE_LEVEL`）が完全に解消。
  - バニラ本来のチャンク配信ストリーミングが自然に機能。
- [x] **身代わりダミー人形（ArmorStand）の見た目と消去フェイルセーフ**:
  - 操縦者のスキン頭部（Player Head）、防具、手持ちコントローラー、自然な腕の構えポーズ。
  - **4重消去フェイルセーフ**: 遠距離飛行中、初期位置チャンクのエンティティが非アクティブ化されるバニラ仕様に対応し、**テレポート直後（初期位置がアクティブ化した瞬間）に再度UUID検索＆足元範囲検索でダミーを100%確実に消去**。
- [x] **被弾フェイルセーフ**: ダミー人形または操縦中プレイヤーが被弾した際、自動で操縦を安全緊急終了。

### 計器・電波制限・HUD・効果音系（ステップ 4 完了）
- [x] **電波距離制限（Signal Range: 最大250m）＆ 通信途絶フェイルセーフ**:
  - 操縦開始位置 `(startX, startY, startZ)` との3D直線距離を毎tick監視。
  - 250m超（電波ロスト）時は操作入力をカットし、緩やかな降下（自動安全着陸姿勢）へ移行。3秒間継続または着地で自動安全操縦終了。
  - `DronePilotStatePayload` により初期位置座標をクライアントと完全同期。
- [x] **FPVフライトHUDオーバーレイ（`DroneHudOverlay`）**:
  - 画面中央: フライトクロスヘア（視界を遮らない半透明シアンの十字・ドット・控えめなFPVフレーム）。
  - 画面上部中央: 機首方位コンパス（例: `[ 方位: 北 180° ]`、日本語8方位）。
  - 画面下部左側: 飛行計器（`速度  XX.X m/s`、`高度  XX.X m`、`昇降  +/-X.X m/s`、`電池  XX% (XXXXm)`）。
  - 画面下部右側: 電波計器（`距離  XXX m`、`電波  XX%`、4段階アンテナピクト、`限界  250 m`、`通信  良好/減衰/限界/切断`）。
  - 画面中央下: 日本語距離＆バッテリー警告（電波警告、残量20%以下注意、10%以下危険点滅、0mで自動着陸中表示）。
  - 画面下部中央: 操作ガイド `[右クリック: 操縦終了]`。
- [x] **飛行効果音＆電波警告オーディオ（`DroneSoundManager`, `DroneFlightSound`）**:
  - **モーター回転音**: スロットル・速度に応じたピッチ動的変化（ホバリング時1.3F $\to$ 全開加速時2.0Fの高周波キーン音）。
  - **接続・切断チャイム**: 操縦開始時「ピロリン♪」、操縦終了時「ポン」。
  - **電波警告ビープ音**: 175m超で注意ビープ、225m超で危険連続ビープ、250m超で緊急アラート。コントローラーの手元音としてダイレクト再生。

### バッテリーシステム（ステップ 5 完了）
- [x] **4段階航続距離バッテリーアイテム（`DroneBatteryItem`）**:
  - Tier 1: 最大航続距離 **3,000** ブロック (`battery_tier_1`)
  - Tier 2: 最大航続距離 **4,000** ブロック (`battery_tier_2`)
  - Tier 3: 最大航続距離 **5,000** ブロック (`battery_tier_3`)
  - Tier 4: 最大航続距離 **6,000** ブロック (`battery_tier_4`)
  - バニラ耐久値バーによる残量可視化 ＆ ツールチップ表示（残量ブロック数とパーセント）。
- [x] **前後の移動距離に応じたバッテリー消費ロジック**:
  - ドローンが前後に移動した実走行距離（ブロック数）を毎tick積算し、1ブロック移動ごとにバッテリー残量を1消費。
- [x] **バッテリー着脱（取り付け・取り外し）システム**:
  - **取り付け**: バッテリーアイテムを持ってドローンを右クリック（装着音 ＋ 残量メッセージ通知）。
  - **取り外し**: スニーク右クリック時、バッテリー装着済みならバッテリーを取り外してインベントリ回収（または足元ドロップ）。未装着ならドローン本体を回収。
  - **アイテム消失防止**: ドローン本体回収時・ドローン破壊時も、装着バッテリーを確実に同時ドロップ。
- [x] **未装着・残量切れ防止 & オートランディング**:
  - バッテリー未装着または残量0のドローンは操縦開始を遮断。
  - 飛行中に0ブロックになった場合、操作入力を無効化して緩やかに自動降下着陸（着地時に安全操縦終了）。
- [x] **ワールド保存対応**: NBT（`InstalledBattery`, `BatteryAccumulatedDist`）によりチャンクアンロードやワールド再ログイン後もバッテリー状態を完全維持。

### 専用急速充電ステーション ＆ GeckoLib 4 実装完了（今回完了）
- [x] **GeckoLib 4.7.7（NeoForge 1.21.1）依存関係有効化**:
  - `build.gradle` に `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.7.7` を導入しコンパイル成功。
- [x] **専用充電ステーションブロック（`DroneChargerBlock` & `DroneChargerBlockEntity`）**:
  - 方向性（`FACING`）および充電状態（`CHARGING`）を持つ充電台。
  - **3Dモデル (`drone_charger.geo.json`)**: ユーザー作成の斜めスロット・スタンド形状を完全適用。
  - **バッテリーのセット**: バッテリーを持って右クリックでスロットにカチッと装着（既存バッテリーがあれば取り出して交換）。
  - **バッテリーの取り出し**: 素手または非バッテリーで右クリックして回収。
  - **安全ドロップ**: ブロック破壊時にセット中のバッテリーを安全にドロップ（消失防止）。
  - **急速充電ロジック**: 毎tick 15ブロック分（1秒あたり 300m）急速充電。充電中は低音動作音と電気スパークパーティクルが発生し、満充電完了時にチャイムSEとハッピーパーティクルでお知らせ。
  - **3Dスロット差し込み描画 (`DroneChargerBatteryLayer`)**:
    - `battery_slot` ボーン（Position: `[0, 7.3, 0.4]`, Rotation: `[0, -90, 90]`）を定義し、GeckoLib 準拠の `GeoRenderLayer` を導入。
    - レンダラー側で不要な独自回転を行わず、GeckoLib のボーン階層・向き（FACING）・回転（Z→Y→X）自動追従により、Blockbenchで指定した通りの位置・角度（X: 0, Y: -90, Z: 90）でスロットにカチャッと刺さる描画を実現。
- [x] **ドローン本体のGeckoLib化 (`DroneEntity`, `DroneGeoModel`, `DroneGeoRenderer`)**:
  - ユーザー作成モデル (`drone.geo.json`) を完全適用。
  - **プロペラ高速回転**: 飛行中・操縦中、`propeller_fl`, `propeller_fr`, `propeller_rl`, `propeller_rr` が毎フレーム滑らかに高速スピン（着地・停止時は自然に静止）。
  - **バッテリー装着レイヤー (`DroneBatteryLayer`)**: ドローンのお腹/背中の `battery_slot` ボーンに、装着されているバッテリーアイテムを `ItemDisplayContext.FIXED` でリアルタイム3D描画。
  - **自然な姿勢制御**: 移動速度・左右ストライフに応じた前傾/後傾・バンク傾きロール（`Mth.clamp`）を完全継承。
- [x] **コントローラー＆バッテリーのGeckoLib 3Dアイテム化 (`GeoItem`)**:
  - `DroneControllerItem` (`drone_controller.geo.json` / `drone_controller.png`): アンテナ・スティック・モニター画面付きのリアルな3Dプロポ。
  - `DroneBatteryItem` (`drone_battery.geo.json` / `drone_battery.png`): 端子・インジケーター付きの3Dバッテリーパック。
  - `models/item/*.json` の親を `minecraft:builtin/entity` に設定し、`RegisterClientExtensionsEvent` で `GeoItemRenderer` を登録してインベントリ・手持ちともに完全3D化。
- [x] **サバイバル向けクラフトレシピの全追加**:
  - `drone_charger`: 銅インゴット、レッドストーン、避雷針、鉄インゴット
  - `drone_item`: 鉄塊、エンダーパール、鉄インゴット、レッドストーンブロック、銅インゴット
  - `drone_controller`: 避雷針、鉄インゴット、レッドストーン、レバー、ガラス板
  - `battery_tier_1` 〜 `battery_tier_4`: 銅・鉄・金・ネザライトによる段階的アップグレードクラフト

---

## 3. 【必読】GeckoLib 3Dモデリング・テクスチャ・アニメーション運用仕様書
同一環境内の `vehicle_mod` および `dtx_rhythm_mod` で確立された実績に基づく、Blockbench / GeckoLib 4 の完全モデリングガイドです。

### 3-1. Blockbench 基本設定
- **エクスポート形式**: **`GeckoLib Animated Model`**（`.geo.json`）
- **正面の向き**: モデル全体の正面は、Blockbench上の **「北」方向（Z軸のマイナス方向、Z-）** に向けて作成すること。
- **原点（0, 0, 0）**: **「モデルの水平中心」かつ「接地面（一番底）」** に合わせること。原点がズレていると当たり判定や配置時の浮き沈みの原因になります。
- **ボーン命名規則**: 半角英数字とアンダースコア（`_`）のみを使用。

### 3-2. アセット配置パス一覧
すべてのファイルは `src/main/resources/assets/microdrone/` 以下の所定ディレクトリに配置します。

| アセット種別 | 配置先ディレクトリ | 命名例 |
|---|---|---|
| **ジオメトリ (Model)** | `assets/microdrone/geo/` | `drone.geo.json`, `drone_charger.geo.json` |
| **アニメーション (Anim)** | `assets/microdrone/animations/` | `drone.animation.json`, `drone_charger.animation.json` |
| **エンティティテクスチャ** | `assets/microdrone/textures/entity/` | `drone.png` |
| **ブロックテクスチャ** | `assets/microdrone/textures/block/` | `drone_charger.png` |
| **アイテムテクスチャ** | `assets/microdrone/textures/item/` | `drone_controller.png`, `battery_tier_1.png` |

### 3-3. 機種別・アイテム別ボーン構成ガイド

#### ① ドローン本体 (`DroneEntity` / 将来のラジコン機体)
```text
<📂root> (中心 [0, 0, 0])
  ├<📂chassis> (メインフレーム・ボディ)
  │   ├<📂camera> (機首FPVカメラ)
  │   ├<📂led_front> (前方LED: 緑)
  │   ├<📂led_rear> (後方LED: 赤)
  │   └<📂battery_slot> (バッテリー装填用の空ボーン。アイテム装着描画用)
  ├<📂propeller_fl> (左前プロペラ。ピボットはプロペラ回転軸のド真ん中！)
  ├<📂propeller_fr> (右前プロペラ。ピボットはプロペラ回転軸のド真ん中！)
  ├<📂propeller_rl> (左後プロペラ。ピボットはプロペラ回転軸のド真ん中！)
  └<📂propeller_rr> (右後プロペラ。ピボットはプロペラ回転軸のド真ん中！)
```
- **注意**: コード側で回転させるパーツ（プロペラなど）は、**ピボットを中心軸に厳密に設定**し、Blockbench側で回転キーフレームを打たないこと（コード制御と競合してブレるのを防ぐため）。

#### ② ドローンコントローラー (`DroneControllerItem`)
```text
<📂root>
  ├<📂body> (コントローラー本体)
  ├<📂antenna> (電波アンテナ)
  ├<📂stick_left> (左スティック: スロットル/ヨー)
  ├<📂stick_right> (右スティック: ピッチ/ロール)
  └<📂screen> (FPVモニター画面)
```

#### ③ バッテリー (`DroneBatteryItem`)
```text
<📂root>
  ├<📂battery_case> (バッテリー外装ケース)
  ├<📂connector> (接続コネクタ/端子)
  └<📂indicator> (残量確認用窓/LED)
```

#### ④ 充電ステーション (`DroneChargerBlock`)
```text
<📂root>
  ├<📂base> (充電ステーション台座)
  ├<📂slot> (バッテリー装填ドック)
  ├<📂led> (充電インジケーターLED)
  └<📂battery_slot> (バッテリーアイテム描画用ピボット。pivot: [0, 4, 0])
```

### 3-4. GeckoLib ボーンへのアイテム装着描画（超重要ルール）
充電ステーションやドローンの `battery_slot` ボーンにバッテリーアイテムを描画する際は、**必ず `ItemDisplayContext.FIXED` を使用し、`GeoRenderLayer`（`renderForBone`）経由で描画してください**。
1. **なぜ `GeoRenderLayer` を使うべきか？**:
   - `GeoBlockRenderer` や `GeoEntityRenderer` は、ボーン階層・ブロック設置向き（FACING）・回転（Z→Y→X）を自動計算して PoseStack に適用します。手動で `poseStack.translate` や `Axis.YP` を計算すると、向き（NORTH/SOUTH）の反転やオイラー角の乗算順序の差異により、簡単に画面外や地中へ飛んでしまいます。
2. **`renderForBone` 内の正確な描画ルール**:
   - `RenderUtil.translateAndRotateMatrixForBone(poseStack, bone)`: GeckoLib標準のピボット移動および回転を適用します。
   - **注意**: `translate(-0.5, -0.51, -0.5)` などの手動相殺は**絶対に行わないこと**（大幅に手前下へ吹き飛ぶ原因になります）。
   - **端子の向き補正**: 充電器スロットに刺す際、端子をスロット奥底（充電コネクタ）に向けるため、`poseStack.mulPose(Axis.YP.rotationDegrees(180.0F))` を適用します（インジケーター面は上を向いたまま、端子のみが下向きに反転）。
   - **ドローン本体**: `poseStack.scale(0.55F, 0.55F, 0.55F)` でお腹のスロットにジャストフィットします。
3. `FIXED` は Blockbenchのボーン座標系（Y=UP）と一致し、アイテムの中心がボーンにジャストフィットします。`NONE` や `GROUND` を使うと原点座標が大きくズレ、上下の回転方向が狂います。

### 3-5. アニメーションキーの命名規則
GeckoLib 4 では、`.animation.json` 側の文字列キー（例: `idle`, `flying`, `charging`）と Java コード側の `RawAnimation.begin().thenLoop("flying")` を**完全一致**させる必要があります。余計なプレフィックスを付けないこと。
- 万が一アニメーションファイル内に指定キーが存在しないと、コンソールに毎フレーム `Unable to find animation: xxx` が出力されてパフォーマンスが低下するため、必ずダミーキーを用意しておくこと。

---

## 4. 次回AIへの重要注意事項・設計上の落とし穴（必読）

### ① サーバー側プレイヤーの追従移動（`ServerPlayer.absMoveTo`）を絶対に外さないこと
バニラではスペクテイターモード時しか `setCamera` 追従しないため、サバイバルでは操縦中 `ServerPlayer` が初期位置に釘付けになります。
`DroneEntity.java` の `tickFlightPhysics` で毎tick `pilot.absMoveTo(this.getX(), this.getY(), this.getZ(), ...)` を行っていることで、
「ドローンとプレイヤーの距離 = 常に0m」となり、128m以上の遠距離飛行でもドローンが消滅せず、チャンクが正常に表示されます。これを変更してはいけません。

### ② マウス旋回は `ComputeCameraAngles` で行うこと
20Hzの Tick（`onClientTick`）内で `player.setYRot(drone.getYRot())` 等を呼んでプレイヤー角度を上書きすると、マウス入力が打ち消されて視線が動かなくなったりカクつきます。
マウス角度はバニラの `MouseHandler` に任せ、`ViewportEvent.ComputeCameraAngles` で描画フレームごとにカメラとドローンに適用するのが正解です。

### ③ ダミー人形の消去タイミング
遠距離飛行中は初期位置チャンクのエンティティが非アクティブになるため、テレポート前に `serverLevel.getEntity(dummyUUID)` を呼ぶと `null` になります。
必ず **`player.teleportTo` でプレイヤーが初期位置に戻った直後にダミーを消去** してください。

### ④ 操縦中イベントキャンセル
操縦中のブロック・エンティティ・攻撃の誤クリックは `ClientDroneInputHandler` のイベント群で100%キャンセルされています。これを解除すると、自プレイヤーのヒットボックスをクリックしてバニラサーバーから「無効なエンティティを攻撃しようとしました」でキックされます。

---

## 5. 今後の拡張ロードマップ（RCシリーズ展開）
1. **ドローン本体・コントローラー・バッテリーの GeckoLib 3Dモデル化**:
   - 本ドキュメントのボーン構成に従って `.geo.json` を配置し、`DroneEntity` を `GeoEntity` 対応へ。
2. **多機種ラジコンの追加展開**:
   - **RCカー (陸上走行ラジコン)**: サスペンション、ホイールスピン、左右ステアリング連動。
   - **RCボート (水上走行ラジコン)**: 水面浮力物理、スクリュー回転、水しぶきパーティクル。
   - **RC飛行機 (固定翼機)**: 揚力物理、プロペラ、エルロン/ラダー動翼連動。
   - 共通の急速充電ステーションでどの機種のバッテリーも充電可能！
