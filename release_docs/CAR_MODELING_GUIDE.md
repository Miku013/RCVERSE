# 外部車両モデリング＆ボーン規約ガイド (CAR MODELING GUIDE)

本ドキュメントでは、**Blockbench** を使用して自作の車両モデルを作成し、本MODへ導入するための「ボーン命名規約」「ピボット設定」「アニメーション連動ルール」を解説します。

---

## 1. 車両追加の全体フロー

1. **Blockbench でモデルを作成**:
   - 形式: **GeckoLib Animated Model** または **Bedrock Model**
   - 保存先: `src/main/resources/assets/microdrone/geo/<車名>.geo.json`
2. **テクスチャの作成**:
   - 保存先: `src/main/resources/assets/microdrone/textures/entity/<車名>.png`
3. **設定ファイル (JSON) の作成**:
   - 保存先: `run/config/microdrone/cars/<車名>.json`
4. **ゲーム内でスポーン**:
   - 金床でRCカーの名前を `<車名>` にリネームして設置、またはコマンドで召喚。
   - 数値変更時はゲーム内で `F3 + T` を押せば即座にホットリロード。

---

## 2. 標準ボーン名一覧（プログラム自動認識）

プログラム側（`RcCarGeoModel` / `RcCarGeoRenderer`）は、以下のボーン名を自動認識して専用のアニメーションや発光シェーダーを適用します。

### ① 4輪RCカー用 (Four-wheel Vehicles)

| ボーン名 | 役割 | 動作仕様 |
| :--- | :--- | :--- |
| **`steer_fl`** | 左前輪ステアリング | A/Dキーのステアリング角度に合わせてY軸旋回 |
| **`steer_fr`** | 右前輪ステアリング | A/Dキーのステアリング角度に合わせてY軸旋回 |
| **`wheel_fl`** | 左前輪 | 走行速度に合わせて前進回転（直立/横倒しを自動判別） |
| **`wheel_fr`** | 右前輪 | 走行速度に合わせて前進回転（直立/横倒しを自動判別） |
| **`wheel_rl`** | 左後輪 | 走行速度に合わせて前進回転 |
| **`wheel_rr`** | 右後輪 | 走行速度に合わせて前進回転 |

### ② 2輪車・自転車・バイク用 (Two-wheel / Bicycles / Bikes)

| ボーン名 | 役割 | 動作仕様 |
| :--- | :--- | :--- |
| **`fork_and_handle`** / **`handle`** | フロントフォーク＆ハンドル | ステアリング角度に合わせて旋回（Z軸-90度モデリングも自動補正） |
| **`wheel_f`** | 前輪 | 走行速度に合わせて縦回転 |
| **`wheel_r`** | 後輪 | 走行速度に合わせて縦回転 |
| **`crank`** | ペダルクランク | 車輪回転と1:1で同期回転 |
| **`leg_r`** / **`leg_l`** | 太もも | クランク回転に合わせて左右交互に上下ピストン（約30度） |
| **`shin_r`** / **`shin_l`** | すね（ひざ下） | 太ももの上下に合わせて深く屈伸 |
| **`villager_body`** | 乗員上半身 | ペダリングのリズムに合わせて左右体重移動＆前傾ピッチ |
| **`villager_head`** | 乗員頭部 | 上半身の動きに合わせて上下ボビング |

### ③ ヘッドライト・電装系 (Lights & Retractable)

| ボーン名 | 役割 | 動作仕様 |
| :--- | :--- | :--- |
| **`retractable_l`** / **`retractable_r`** | 左右独立リトラクタブル | 点灯時に+X軸回転でポップアップ開閉＆フルブライト発光 |
| **`retractable_lights`** / **`retractable`** | 左右一体リトラクタブル | 点灯時に+X軸回転でポップアップ開閉＆フルブライト発光 |
| **`headlight`** / **`headlights`** | 固定式ヘッドライト | 点灯時に暗闇でも最大輝度（フルブライト）で発光 |
| **`taillight`** / **`light`** / **`lamp`** | テールランプ・マーカー | 点灯時に暗闇でも最大輝度（フルブライト）で発光 |

---

## 3. 車両設定ファイル (`<車名>.json`) の記述例

```json
{
  "id": "86",
  "displayName": "86 スーパースポーツ",
  "model": "microdrone:geo/86.geo.json",
  "texture": "microdrone:textures/entity/86.png",
  "animation": "microdrone:animations/rc_car.animation.json",
  "properties": {
    "maxSpeedMultiplier": 1.0,
    "accelMultiplier": 1.0,
    "turnSpeedMultiplier": 1.0,
    "gripMultiplier": 1.0,
    "scale": 1.0,
    "invertSteer": false,
    "rollMultiplier": 0.25,
    "maxRollAngle": 10.0,
    "retractableAngle": 22.0,
    "pedalSwingMultiplier": 1.0
  }
}
```

### パラメータ解説
- **`scale`**: 全体の描画縮尺（標準は `1.0`）
- **`rollMultiplier`**: カーブを曲がるときの車体傾き倍率（4輪車は `0.25`、バイク・自転車は `0.6` 前後を推奨）
- **`maxRollAngle`**: 最大傾斜角（度）
- **`retractableAngle`**: リトラクタブルライトの開き角（度。例: 半目 `12.0`、標準 `22.0`）
- **`pedalSwingMultiplier`**: 自転車ペダリングの足のストローク倍率（標準 `1.0`、激しく漕ぐなら `1.3`〜`1.5`）
