# Git コミット履歴・重要マイルストーン・設計参考資料 (GIT MILESTONES)

本ドキュメントでは、本MODの主要な実装コミット履歴と、アーキテクチャの核となる設計パターン・参考コードを整理します。

---

## 1. 主要 Git コミット履歴 (Milestone Commits)

| コミットハッシュ | カテゴリ | 概要・変更点 |
| :--- | :--- | :--- |
| **`880d327`** | `feat(bicycle)` | 村人自転車のペダリング振幅拡大（30°）、膝屈伸強化、上半身・頭のボビング＆体重移動アクション |
| **`1768eaf`** | `fix(bicycle)` | 自転車前輪（`wheel_f`）の横回転バグ修正（親子Z軸逆回転の相殺を検知しローカルX軸縦回転化） |
| **`ddcc88b`** | `feat(bicycle)` | 村人自転車モデル、テクスチャ、外部コンフィグ、ハンドル操舵・クランク連動基盤の導入 |
| **`71cd308`** | `feat(car_config)` | `CarConfig` への `retractableAngle` 追加および `F3 + T` によるコンフィグの即時ホットリロード対応 |
| **`5bcb432`** | `feat(rc_car)` | 夜間用ヘッドライト機能、リトラクタブル開閉アニメーション、実照明（Level 15）追従、キーバインド登録 |
| **`6a4e34c`** | `feat(jump_pad)` | ジャンプパッドの進入段差コライダー完全撤廃（`Shapes.empty()`）、頂点水平吐き出し（Apex Expel）2段階物理 |
| **`04c8b84`** | `fix(jump_pad)` | サーバー・クライアント間のステート同期保証、プレイヤー用ジャンプの削除、3段階パワーネオン強化 |
| **`5ebd699`** | `feat(block)` | ネオンジャンプパッド（3段階パワー可変式）の新規作成とRCカー打ち上げ物理の実装 |
| **`b6b5e50`** | `feat(item)` | サーキット装飾アイテム（縁石、レース看板、チェックプレート、タイヤバリア、ゴールゲート）のアイコン刷新 |
| **`96b6cb1`** | `feat(item)` | LiPoバッテリー Tier 1〜4 の高解像度ピクセルアートアイコンおよび3Dモデルの適用 |
| **`5a43aac`** | `feat(car)` | `CarConfig` へのロール強度（`rollMultiplier`）および最大ロール角（`maxRollAngle`）追加 |
| **`827ddb5`** | `fix(car)` | タイヤボーンの初期回転軸（直立 vs 横倒し）を自動判別し、86等の直立モデリングでも正常に転がるよう修正 |
| **`702e679`** | `feat(road)` | スプライン道路生成システム（Catmull-Rom曲線、バンク角、ポリゴンメッシュ生成）の実装 |
| **`1479054`** | `feat` | 峠スロープ・無限モジュラースロープ動的メッシュ描画・ハイブリッドズーム視点・物理落下修正 |
| **`e48cd1d`** | `feat` | レーシングスロープとブーストランプのフェンス型動的マルチパート接続 |
| **`35cec22`** | `feat(sync)` | レスキュー位置復帰（遠距離アンリンク時の点滅無敵復帰）システムの実装 |
| **`7ae2549`** | `fix` | 階段状VoxelShapeをバイパスし、三角形メッシュ表面へのサスペンション吸着物理によりスロープ沈み込みを根絶 |

---

## 2. 実装にあたって参考・活用した主要アーキテクチャ

### ① GeckoLib 4 骨格アニメーション基盤
- **クラス**: `software.bernie.geckolib.model.GeoModel`, `software.bernie.geckolib.renderer.GeoEntityRenderer`
- **設計ポイント**:
  - `setCustomAnimations()` 内で、MinecraftのTickと描画フレーム（PartialTick）を補間しながらボーンをリアルタイム操作。
  - `renderRecursively()` をオーバーライドし、ヘッドライト点灯時に特定のボーン名（`retract`, `light`, `headlight` 等）の `packedLight` を `LightTexture.FULL_BRIGHT` に書き換えることで、シェーダー不要の自発光ランプを実現。

### ② NeoForge 1.21.1 ネットワークパケットシステム
- **インターフェース**: `net.minecraft.network.protocol.common.custom.CustomPacketPayload`
- **登録**: `net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent`
- **設計ポイント**:
  - Java 21 の `record` を用いたイミュータブルなパケットデータ構造。
  - `StreamCodec.composite()` または手動コーデックによる超軽量シリアライズ。
  - クライアント側操作（キー入力・プロポ操作）をサーバー側 `IPayloadContext.enqueueWork()` で安全に処理。

### ③ スプライン曲線（Catmull-Rom / Hermite）補間
- **クラス**: `RoadSplineGenerator`
- **設計ポイント**:
  - 始点ベクトル・向きと終点ベクトル・向きから、中間の3次元ベジェ/エルミート曲線を算出。
  - 各サンプリング点での接線ベクトルと法線ベクトルから幅・厚み・バンク角を持った頂点配列（Quadメッシュ）を構築。

### ④ 実光源追従配置（Light Level 15）
- **クラス**: `net.minecraft.world.level.block.Blocks.LIGHT`, `LightBlock.LEVEL`
- **設計ポイント**:
  - クライアント側ではなくサーバー側（`RcCarEntity.updateHeadlightLighting`）で、車両の視線ベクトル（`getLookAngle()`）前方1.8ブロック先へ不可視の `LIGHT` ブロックを配置。
  - 車両移動時に古い座標のブロックを自動消去し、最新座標へ移動させることで、夜間バニラ光源と完全互換の前照灯を実現。
