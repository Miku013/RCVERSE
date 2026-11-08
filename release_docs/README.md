# Micro Drone & RC Series - 公開用総合ドキュメント

ようこそ！「**Micro Drone & RC Series**」は、Minecraft 1.21.1 (NeoForge) 上で動作する、本格的なFPVマイクロドローンおよびRC（ラジコン）ビークル＆サーキットレース総合MODです。

---

## 📚 ドキュメント一覧 (Index)

本フォルダ（`release_docs/`）には、MODの公開・配布・開発に必要な全情報が整理されています。目的に応じて各ファイルをご参照ください。

| ドキュメント | 概要 |
| :--- | :--- |
| **[REQUIREMENTS.md](REQUIREMENTS.md)** | **動作要件・前提MOD・推奨環境・ライブラリ**<br>対応Minecraftバージョン(1.21.1)、NeoForge要件、GeckoLib 4、ビルド環境等 |
| **[SPECIFICATIONS.md](SPECIFICATIONS.md)** | **全機能仕様書**<br>各機体（ドローン/カー/飛行機/船/潜水艦/戦車）の物理特性、スプライン道路生成、ジャンプパッド、レース計測、チューニング等の全仕様 |
| **[DEVELOPMENT_HISTORY.md](DEVELOPMENT_HISTORY.md)** | **開発経緯・開発過程・課題と修正記録**<br>段差引っ掛かり解消、ハイブリッドジャンプ放物線物理、前輪回転修正など、開発中に直面した課題と技術的解決策の完全記録 |
| **[CAR_MODELING_GUIDE.md](CAR_MODELING_GUIDE.md)** | **外部車両モデリング＆ボーン規約ガイド**<br>Blockbenchで自作モデルを作成し、MODへ追加するためのボーン命名規約（ステアリング、ホイール、リトラクタブル、ペダリング連動）とJSON設定方法 |
| **[GIT_MILESTONES.md](GIT_MILESTONES.md)** | **Git コミット履歴・重要マイルストーン・設計参考資料**<br>機能別重要コミット一覧と、GeckoLib 4 / NeoForge パケット / 実光源追従等の参考アーキテクチャ解説 |

---

## 🚀 クイックスタート (配布・導入方法)

### プレイヤー向け
1. Minecraft **1.21.1** に **NeoForge 21.1.244+** を導入します。
2. `mods` フォルダに **GeckoLib 4** (`geckolib-neoforge-1.21.1-4.7.7.jar` 等) を導入します。
3. 本MODのJARファイル（`microdrone-1.0.0.jar`）を `mods` フォルダに入れます。
4. ゲームを起動し、クリエイティブタブ「マイクロドローン / RC」からプロポや機体を取り出して操縦できます！

### ビルド方法 (開発者向け)
```bash
git clone <repository_url>
cd micro_drone_mod
./gradlew build -x test
```
生成物: `build/libs/microdrone-1.0.0.jar`
