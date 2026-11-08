# 動作要件・ライブラリ・前提環境仕様書 (REQUIREMENTS)

本MOD「**Micro Drone & RC Series**」を導入・プレイ・開発ビルドするにあたって必要な環境要件一覧です。

---

## 1. 動作環境 (Runtime Requirements)

| 項目 | 指定バージョン / 推奨値 | 備考 |
| :--- | :--- | :--- |
| **Minecraft** | **`1.21.1`** | 単一バージョン固定 |
| **Mod Loader** | **NeoForge `21.1.244` 以上** | 推奨: `21.1.244+`（1.21.1最新安定版） |
| **Java Runtime (JRE)** | **Java 21 (Temurin / Oracle JDK 21)** | Minecraft 1.20.6 / 1.21+ の必須要件 |
| **環境種別** | **クライアント / サーバー両対応** | 専用サーバー(Dedicated Server)でのマルチプレイ対応 |

---

## 2. 必須前提MOD / ライブラリ (Dependencies)

### ① GeckoLib 4 (必須)
- **Mod名**: [GeckoLib 4](https://curseforge.com/minecraft/mc-mods/geckolib)
- **Maven座標**: `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.7.7`
- **用途**:
  - ドローン、RCカー（86、バギー、村人自転車等）、戦車、航空機、ボート、潜水艦の3Dモデル（`.geo.json`）の読み込み
  - 車輪回転、ステアリング、サスペンション、ペダリング、砲塔旋回等のリアルタイム骨格アニメーション制御
  - プロポ（送信機）の3Dアイテムレンダリング

---

## 3. 推奨MOD (Optional / Recommended)

| MOD名 | 用途 |
| :--- | :--- |
| **Just Enough Items (JEI) / REI / EMI** | バッテリー、モーター、プロポ、コースブロック等のクラフトレシピ確認用 |
| **Iris / Oculus (シェーダー)** | 夜間用ヘッドライト追従実照明（Light Block Level 15）およびテールランプのフルブライト発光演出をよりリアルに描画可能 |
| **Controllable (Controller Mod)** | 実機プロポやゲームパッド（Xbox/DualSense等）によるアナログ操縦（※キーバインド対応済み） |

---

## 4. 開発・ビルド環境 (Build / Dev Environment)

MODのソースコードからJARファイルを生成する場合の要件です。

- **ビルドツール**: Gradle 8.10+ (同梱の `./gradlew` を使用)
- **JDK**: JDK 21 (64-bit)
- **開発プラグイン**: `net.neoforged.moddev` version `2.0.147`
- **Mcp / Parchment マッピング**:
  - Parchment Minecraft: `1.21.1`
  - Parchment Mappings: `2024.11.17`
- **ビルドコマンド**:
  ```bash
  ./gradlew build -x test
  ```
- **生成先**:
  `build/libs/microdrone-1.0.0.jar`
