# Micro Drone MOD - 開発引き継ぎノート (DEVELOPER_NOTES.md)

最終更新日: 2026-10-31
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

### 3-4. GeckoLib ボーンへのアイテム装着描画（超重要ルール・二重回転バグ解消手法）
充電ステーションやドローンの `battery_slot` ボーンにバッテリーアイテムを描画する際は、**必ず以下の「マトリクスポップ＆1回適用方式」を使用し、`ItemDisplayContext.FIXED` で描画してください**。

#### ① GeckoLib本体の二重回転バグとその完全解消コード
GeckoLib本体の `GeoBlockRenderer` / `GeoEntityRenderer` は、レイヤーを呼び出す直前にすでに `RenderUtil.prepMatrixForBone(poseStack, bone)` でボーンの回転を適用しています。
そのため、レイヤー内でそのまま `RenderUtil.translateAndRotateMatrixForBone` を呼ぶと、**回転が二重（2回）掛かり、角度が2倍になったり激しいねじれが発生します**。

これを完全に解消するため、以下のパターンを確立・適用しました：
```java
// 1. GeckoLib本体が prepMatrixForBone で適用した回転とオフセットを pop してリセット
//    （これにより二重回転バグを根本から完全に解消し、親ボーンの基準空間に戻す）
poseStack.popPose();

// 2. バッテリー描画用のクリーンなマトリクスをプッシュ
poseStack.pushPose();

// 3. battery_slot ボーンのピボット座標と回転を【正確に1回だけ】適用！
RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);

// 4. ItemDisplayContext.FIXED を指定してスロットにジャストフィット描画
this.itemRenderer.renderStatic(
        battery,
        ItemDisplayContext.FIXED,
        packedLight,
        packedOverlay,
        poseStack,
        bufferSource,
        animatable.getLevel(),
        (int) animatable.getBlockPos().asLong()
);

// 5. バッテリー描画用マトリクスをポップ
poseStack.popPose();

// 6. 呼び出し元（GeckoLib renderRecursively）の popPose と整合性を保つため再プッシュ
poseStack.pushPose();
```

#### ② GeckoLibの回転規約とBlockbench変換仕様
GeckoLibのモデルローダー（`BakedModelFactory.java`）は、`.geo.json` から回転角を読み込む際に内部で以下の変換を行っています：
- **X軸:** `Math.toRadians(-rotation.x)` （**マイナス符号が付く！**）
- **Y軸:** `Math.toRadians(-rotation.y)` （**マイナス符号が付く！**）
- **Z軸:** `Math.toRadians(rotation.z)` （**符号はそのまま！**）
- **乗算順序:** `Z → Y → X` の順序で回転行列が合成されます。

そのため、Blockbench上で見た通りの回転をマイクラ内で再現するには、`.geo.json` の `rotation` に **XとYにあらかじめマイナスを掛け、Zはそのまま** で出力する必要があります。

#### ③ 専用Blockbenchプラグイン (`minecraft_coords_mode.js` v1.0.5)
- 保存場所: `micro_drone_mod/plugins/minecraft_coords_mode.js` および `Downloads/minecraft MOD開発/minecraft_coords_mode.js`
- 機能: Blockbenchの右側サイドバーに常駐し、選択中ボーンの正確な `pivot` と、GeckoLib向けにX/Y反転済みの `rotation` をリアルタイム表示。
- 「GeckoLib ボーンJSONをコピー」（`Ctrl + Shift + C`）を押すだけで、そのまま `.geo.json` に貼り付け可能なJSONスニペットを生成。

#### ④ 高速開発ビルド環境
- **`Ctrl + Shift + B`**: VSCode（Antigravity IDE）の標準ビルドタスク。約0.5秒でアセット更新・再コンパイルが完了。
- **`quick_build.bat`**: プロジェクト直下のダブルクリック実行用バッチファイル。
- **ファイル自動監視モード**: `./gradlew processResources classes --continuous` により、`.geo.json` 保存時に自動で0.3秒ビルド。

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

### ⑤ 小型ラジコンの遠距離描画拡大（shouldRenderAtSqrDistance）
Minecraftバニラの描画エンジンは「エンティティのヒットボックス（サイズ）に比例して描画カリング（非表示）距離を決定する」仕様になっています。
そのため、サイズ0.5mの小型ドローンやRCカー、戦車は、わずか32m離れただけで画面から消えて見えなくなってしまいます。
DroneEntity.java（および今後のRCビークル共通）で shouldRenderAtSqrDistance をオーバーライドし、判定距離を Math.max(256.0, ...) に拡大したことで、電波限界（250m）まで空の彼方や地上を走る機体が米粒サイズでしっかり描画され続けるように改善しました。
今後追加するRCカーや戦車等の陸上モデルにも同様のオーバーライドを適用すること。

---
### ⑥ 多機種共通RCビークルフレームワーク（RcControllable / RcVehicleManager）
プロポ（送信機: DroneControllerItem）でドローン・RCカー・戦車・ボート等の複数機種をシームレスに操作（方式A: ワンクリックスマートバインド）できるように設計を共通化。
- RcControllable: 全RCビークルが実装する共通操縦インターフェース（isPiloted(), startPiloting(), stopPiloting() 等）。
- RcVehicleManager: 操縦中プレイヤーや身代わり人形と機体のマッピングを一元管理。
- プロポ操作時、周囲32ブロック以内の利用可能な機体（ドローンや車）を自動検出してスマートバインド。操縦中の右クリック解除も全ビークル共通で動作。

---

### RCカー（陸上走行ラジコン）の導入完了（ステップ 9 完了）
- [x] **GeckoLib 3Dモデル & レンダラー (c_car.geo.json, c_car.png)**:
  - ユーザー作成のRCカーモデルを完全適用。
  - **前輪ステアリング連動 (steer_fl, steer_fr)**: A/Dキーのステアリング操作に応じて左右に滑らかに操舵首振り（最大35度）。
  - **4輪タイヤ回転 (wheel_fl, wheel_fr, wheel_rl, wheel_rr)**: 走行速度に応じたリアルタイムX軸回転。
- [x] **自動車物理 (RcCarEntity)**:
  - 地上走行、重力、摩擦、前進アクセル、後退・バック、車速に応じた旋回半径。
  - 1ブロックの段差乗り越え（maxUpStep）。
- [x] **同一プロポでのスマートバインド操作**:
  - プロポ（DroneControllerItem）を持ってRCカーを右クリックするだけで即座にバインド＆操縦開始。
  - プロポを持ったまま右クリックでいつでも安全に操縦終了＆初期位置復元。
- [x] **バッテリー & モーター換装システム共通化**:
  - ドローンと同じ4段階バッテリーおよびブラシレスモーター（Tier 1〜4）を車体右クリックで装着・交換可能。
  - モーターTierによって車の最高速度（12m/s 〜 32m/s）と加速力が大幅変化。


### ⑦ エンティティへの右クリック判定（isPickable）の必須仕様
Minecraftバニラでは、Entity クラスの isPickable() はデフォルトで alse を返します。
これをオーバーライドして eturn !this.isRemoved(); にしないと、プレイヤーのクロスヘアがエンティティに合わず、右クリック（interact）や左クリック（ttack）が背後の地面をクリックした扱いになり、アイテム装着や操縦開始が一切反応しなくなります。新ビークル作成時は必ず isPickable を実装すること。

## 5. 今後の拡張ロードマップ（RCシリーズ展開）
1. **ドローン本体・コントローラー・バッテリーの GeckoLib 3Dモデル化**:
   - 本ドキュメントのボーン構成に従って `.geo.json` を配置し、`DroneEntity` を `GeoEntity` 対応へ。
2. **多機種ラジコンの追加展開**:
   - **RCカー (陸上走行ラジコン)**: サスペンション、ホイールスピン、左右ステアリング連動。
   - **RCボート (水上走行ラジコン)**: 水面浮力物理、スクリュー回転、水しぶきパーティクル。
   - **RC飛行機 (固定翼機)**: 揚力物理、プロペラ、エルロン/ラダー動翼連動。
   - 共通の急速充電ステーションでどの機種のバッテリーも充電可能！


### モーター換装・性能カスタマイズ機能（ステップ 6 完了）
- [x] **4段階のブラシレスモーターアイテム (`DroneMotorItem`)**:
  - **Tier 1 (Standard)**: 最高速度 7.0 m/s (0.35), 加速度 0.04 (0.8 m/s²), 昇降 5.0 m/s (0.25), ガンメタル/ブラック (`0xFF333333`)
  - **Tier 2 (Sport)**: 最高速度 10.0 m/s (0.50), 加速度 0.065 (1.3 m/s²), 昇降 7.0 m/s (0.35), スポーツブルー (`0xFF1E88E5`)
  - **Tier 3 (High-Speed)**: 最高速度 14.0 m/s (0.70), 加速度 0.09 (1.8 m/s²), 昇降 9.0 m/s (0.45), ゴールド (`0xFFFFB300`)
  - **Tier 4 (Racing)**: 最高速度 19.0 m/s (0.95), 加速度 0.12 (2.4 m/s²), 昇降 12.0 m/s (0.60), レーシングレッド (`0xFFE53935`)
- [x] **動的飛行物理＆クライアント予測**:
  - ドローン本体が装着中のモーター性能（最高速度・加速度・昇降速度）を動的取得して飛行。
- [x] **GeckoLibモーターボーンカラー同期**:
  - `DroneGeoRenderer` の `renderRecursively` により、四隅のモーターボーン（`motor1`〜`motor4`）が装着中モーターのアルマイトカラーで発色。
- [x] **ワンクリック換装インターフェース**:
  - モーターを手に持ってドローンを右クリックすることで瞬時に換装（元のモーターは手元に返却）。
  - ドローン回収時にもモーターが正常にドロップ（消失防止）。
- [x] **FPV HUDオーバーレイ連携**:
  - HUD下部計器に現在のモーター（`動力  T2 (Sport)` など）がモーターのカラーでリアルタイム表示。
- [x] **クラフトレシピ**:
  - 4段階すべてのモーターに専用のクラフトレシピとピクセルアートテクスチャを登録。


### プロポ＆モーター中間素材パーツ・本格レシピ（ステップ 8 完了）
- [x] **プロポ（送信機）制作用中間素材 (6種)**:
  - **無線アンテナ (`radio_antenna`)**: 2.4GHz高利得アンテナロッド
  - **プロポケース (`controller_case`)**: グリップ付きエルゴノミクス送信機シェル
  - **ジンバルスティック (`gimbal_stick`)**: 繊細な操縦を可能にするアナログスティック
  - **プロポメイン基板 (`controller_board`)**: 高速低遅延MCU搭載の送信機制御基板
  - **精密ばね (`tiny_spring`)**: スティックのスムーズなセンタリングを生むスプリング
  - **液晶ディスプレイ部品 (`lcd_screen`)**: テレメトリ情報を表示する小型LCDモジュール
- [x] **ブラシレスモーター制作用中間素材 (8種)**:
  - **モーターケース Tier 1〜4 (`motor_can_tier_1` 〜 `motor_can_tier_4`)**: 各Tierのアルマイト仕上げアウターローター缶
  - **エナメル銅線 (`magnet_wire`)**: 高密度に巻かれたステータ用電線
  - **モーターシャフト (`motor_shaft`)**: 精密研磨された高強度ステンレス製回転軸
  - **ネオジム磁石 (`neodymium_magnet`)**: 強力な磁場を生成する超強力永久磁石
  - **ステータ端子 (`motor_stator_terminal`)**: 電極端子とコア鉄心
- [x] **本格組み立てクラフトレシピへの刷新**:
  - **プロポ (`drone_controller`)**: アンテナ×2 + ケース + スティック×2 + 液晶 + ばね×2 + メイン基板
  - **モーター Tier 1〜4 (`motor_tier_1` 〜 `motor_tier_4`)**: 軸 + 磁石×2 + 各Tierモーターケース + エナメル銅線 + ステータ端子 + 接続ケーブル
  - **中間素材全14種**: 鉄、銅、金、レッドストーン、クオーツ、ガラス板等からサバイバルで自作可能なレシピを完備。
- [x] **テクスチャ・モデル・翻訳・クリエイティブタブ同期**:
  - 全14種について16x16ピクセルアートテクスチャ、モデルJSON、日英翻訳、ツールチップ、クリエイティブタブ登録を完備し、ビルドテストパス。

