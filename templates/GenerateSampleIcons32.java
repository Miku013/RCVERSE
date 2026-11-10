import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class GenerateSampleIcons32 {
    static final int S = 32;

    public static void main(String[] args) throws Exception {
        String outDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/templates/sample_icons/";
        String brainDir = "C:/Users/mikum/.gemini/antigravity/brain/4cc19972-ca6e-49f1-8e74-16862cb81cc8/";
        new File(outDir).mkdirs();

        Map<String, BufferedImage> icons = new HashMap<>();

        // 1. drone_item (マイクロドローン)
        icons.put("drone_item", createDroneIcon());
        // 2. drone_controller (プロポ / 送信機)
        icons.put("drone_controller", createControllerIcon());
        // 3. rc_car_item (RCスポーツカー)
        icons.put("rc_car_item", createCarIcon());
        // 4. rc_tank_item (RC戦車)
        icons.put("rc_tank_item", createTankIcon());
        // 5. rc_plane_item (RC飛行機)
        icons.put("rc_plane_item", createPlaneIcon());
        // 6. rc_boat_item (RCボート)
        icons.put("rc_boat_item", createBoatIcon());
        // 7. battery_tier_4 (Tier 4 バッテリー)
        icons.put("battery_tier_4", createBatteryIcon());
        // 8. motor_tier_4 (Tier 4 モーター)
        icons.put("motor_tier_4", createMotorIcon());
        // 9. traffic_cone (カラーコーン)
        icons.put("traffic_cone", createConeIcon());
        // 10. boost_ramp (ブーストジャンプ台)
        icons.put("boost_ramp", createRampIcon());

        // 各アイコンの保存 (実寸 32x32)
        for (Map.Entry<String, BufferedImage> entry : icons.entrySet()) {
            ImageIO.write(entry.getValue(), "PNG", new File(outDir + entry.getKey() + "_32x32.png"));
            ImageIO.write(entry.getValue(), "PNG", new File(brainDir + entry.getKey() + "_32x32.png"));
        }

        // カタログ一覧シート (拡大グリッド付き展示ボード)
        BufferedImage catalog = createCatalogSheet(icons);
        ImageIO.write(catalog, "PNG", new File(outDir + "sample_icons_showcase.png"));
        ImageIO.write(catalog, "PNG", new File(brainDir + "sample_icons_showcase.png"));

        System.out.println("ALL_SAMPLES_CREATED");
    }

    // 1. マイクロドローン: X字カーボンアーム + 4つのプロペラ + FPVカメラ
    static BufferedImage createDroneIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 4本のアーム (X字)
        g.setColor(new Color(40, 42, 48));
        g.drawLine(5, 5, 26, 26);
        g.drawLine(6, 5, 27, 26);
        g.drawLine(5, 26, 26, 5);
        g.drawLine(6, 26, 27, 5);

        // 中央胴体 (カーボンプレート)
        g.setColor(new Color(30, 32, 36));
        g.fillRoundRect(11, 10, 10, 12, 4, 4);
        g.setColor(new Color(60, 64, 72));
        g.drawRoundRect(11, 10, 9, 11, 4, 4);

        // FPVカメラ (前部レンズ)
        g.setColor(new Color(20, 20, 22));
        g.fillRect(13, 8, 6, 4);
        g.setColor(new Color(0, 200, 255)); // 青いレンズ反射
        g.fillRect(15, 8, 2, 2);

        // バッテリーストラップ / インジケータ
        g.setColor(new Color(255, 60, 60)); // 赤LED
        g.fillRect(13, 15, 2, 2);
        g.setColor(new Color(60, 255, 60)); // 緑LED
        g.fillRect(17, 15, 2, 2);

        // 4基のブラシレスモーター
        int[][] motors = {{5, 5}, {23, 5}, {5, 23}, {23, 23}};
        for (int[] m : motors) {
            g.setColor(new Color(220, 50, 50)); // 赤アルマイト
            g.fillOval(m[0], m[1], 4, 4);
            g.setColor(new Color(15, 15, 15));
            g.drawOval(m[0], m[1], 4, 4);
            g.setColor(new Color(220, 220, 220)); // シャフト
            g.fillRect(m[0] + 1, m[1] + 1, 2, 2);
        }

        // 4枚の3ブレードプロペラ (半透明シアンの回転感)
        g.setColor(new Color(0, 220, 255, 160));
        g.drawOval(2, 2, 10, 10);
        g.drawOval(20, 2, 10, 10);
        g.drawOval(2, 20, 10, 10);
        g.drawOval(20, 20, 10, 10);

        g.dispose();
        return img;
    }

    // 2. プロポ / コントローラー: アンテナ + 青液晶 + 2本スティック
    static BufferedImage createControllerIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // アンテナ
        g.setColor(new Color(30, 30, 30));
        g.fillRect(15, 2, 2, 6);
        g.setColor(new Color(60, 60, 60));
        g.fillRect(14, 2, 1, 6);

        // 本体シェル (人間工学的グリップ形状)
        g.setColor(new Color(25, 26, 30));
        g.fillRoundRect(6, 8, 20, 21, 6, 6);
        g.setColor(new Color(45, 48, 55));
        g.fillRoundRect(8, 10, 16, 17, 4, 4);

        // グリップラバー (左右)
        g.setColor(new Color(15, 15, 15));
        g.fillRect(5, 14, 2, 12);
        g.fillRect(25, 14, 2, 12);

        // LCDスクリーン (中央・バックライト水色)
        g.setColor(new Color(10, 10, 12));
        g.fillRect(10, 11, 12, 7);
        g.setColor(new Color(14, 165, 233));
        g.fillRect(11, 12, 10, 5);
        g.setColor(new Color(255, 255, 255, 180));
        g.fillRect(12, 13, 3, 1); // テキストライン風
        g.fillRect(16, 13, 4, 1);
        g.fillRect(12, 15, 6, 1);

        // ジンバルスティック (左右)
        // 左スティック
        g.setColor(new Color(20, 20, 20));
        g.fillOval(9, 19, 5, 5);
        g.setColor(new Color(220, 38, 38)); // 赤いアルミトップ
        g.fillRect(11, 21, 2, 2);

        // 右スティック
        g.setColor(new Color(20, 20, 20));
        g.fillOval(18, 19, 5, 5);
        g.setColor(new Color(220, 38, 38));
        g.fillRect(20, 21, 2, 2);

        // 外枠ライン
        g.setColor(new Color(15, 15, 18));
        g.drawRoundRect(6, 8, 19, 20, 6, 6);

        g.dispose();
        return img;
    }

    // 3. RCスポーツカー: レッドボディ + ブラックウィング + ホイール
    static BufferedImage createCarIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 影
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(4, 25, 24, 4);

        // リアウィング
        g.setColor(new Color(30, 30, 32));
        g.fillRect(4, 11, 4, 6);
        g.fillRect(2, 10, 6, 2);

        // メインボディ (赤スポーツカー)
        g.setColor(new Color(220, 38, 38));
        g.fillRoundRect(5, 15, 22, 9, 4, 4);
        g.fillRoundRect(9, 11, 12, 7, 3, 3); // キャビン

        // ハイライト (ボンネット & ルーフ)
        g.setColor(new Color(248, 113, 113));
        g.fillRect(11, 11, 8, 1);
        g.fillRect(19, 16, 6, 1);

        // ウィンドウ (スモークガラス)
        g.setColor(new Color(30, 41, 59));
        g.fillRect(10, 12, 4, 4); // リア窓
        g.setColor(new Color(56, 189, 248)); // 反射
        g.drawLine(14, 12, 17, 15); // フロント窓反射

        // ヘッドライト (イエロー点灯感)
        g.setColor(new Color(254, 240, 138));
        g.fillRect(26, 17, 2, 2);

        // 前後タイヤ (黒ゴム + シルバーホイール)
        drawWheel(g, 7, 20);
        drawWheel(g, 21, 20);

        // 輪郭
        g.setColor(new Color(25, 25, 28));
        g.drawRoundRect(5, 15, 22, 9, 4, 4);

        g.dispose();
        return img;
    }

    static void drawWheel(Graphics2D g, int x, int y) {
        g.setColor(new Color(24, 24, 27)); // タイヤ
        g.fillOval(x, y, 6, 6);
        g.setColor(new Color(203, 213, 225)); // ホイール
        g.fillOval(x + 1, y + 1, 4, 4);
        g.setColor(new Color(71, 85, 105)); // ナット
        g.fillRect(x + 2, y + 2, 2, 2);
    }

    // 4. RC戦車: ミリタリーグリーン + キャタピラ + 砲塔・砲身
    static BufferedImage createTankIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 影
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(3, 25, 26, 4);

        // 砲身
        g.setColor(new Color(65, 78, 60));
        g.fillRect(16, 9, 13, 2);
        g.setColor(new Color(40, 50, 36)); // マズルブレーキ
        g.fillRect(27, 8, 2, 4);

        // 砲塔
        g.setColor(new Color(78, 94, 72));
        g.fillRoundRect(10, 10, 10, 6, 3, 3);
        g.setColor(new Color(110, 130, 100)); // ハイライト
        g.fillRect(11, 10, 8, 1);

        // 車体
        g.setColor(new Color(65, 78, 60));
        g.fillRect(5, 15, 21, 6);

        // キャタピラ (履帯)
        g.setColor(new Color(30, 32, 30));
        g.fillRoundRect(4, 19, 23, 7, 3, 3);
        // 転輪
        g.setColor(new Color(78, 94, 72));
        for (int i = 6; i <= 22; i += 4) {
            g.fillOval(i, 20, 4, 4);
            g.setColor(new Color(20, 20, 20));
            g.drawOval(i, 20, 4, 4);
            g.setColor(new Color(78, 94, 72));
        }

        // 外枠
        g.setColor(new Color(30, 38, 28));
        g.drawRoundRect(4, 19, 23, 7, 3, 3);
        g.drawRoundRect(10, 10, 10, 6, 3, 3);

        g.dispose();
        return img;
    }

    // 5. RC飛行機: 主翼 + プロペラ + 垂直尾翼
    static BufferedImage createPlaneIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 胴体 (流線形)
        g.setColor(new Color(240, 240, 245));
        g.fillRoundRect(4, 14, 23, 5, 3, 3);
        g.setColor(new Color(37, 99, 235)); // 青ストライプ
        g.fillRect(8, 16, 16, 2);

        // 垂直尾翼
        g.setColor(new Color(220, 38, 38)); // 赤い尾翼
        int[] vx = {5, 9, 9};
        int[] vy = {14, 7, 14};
        g.fillPolygon(vx, vy, 3);

        // キャノピー
        g.setColor(new Color(56, 189, 248));
        g.fillRoundRect(17, 12, 6, 3, 2, 2);

        // 主翼 (上反角)
        g.setColor(new Color(220, 225, 230));
        int[] wx = {14, 20, 18, 12};
        int[] wy = {15, 15, 24, 22};
        g.fillPolygon(wx, wy, 4);
        g.setColor(new Color(37, 99, 235));
        g.fillRect(15, 21, 3, 2);

        // 先端プロペラ (ノーズコーン + 回転羽)
        g.setColor(new Color(245, 158, 11)); // 黄色プロペラ
        g.fillRect(27, 10, 2, 13);
        g.setColor(new Color(220, 38, 38)); // 赤スピンナー
        g.fillOval(26, 15, 3, 3);

        g.dispose();
        return img;
    }

    // 6. RCスピードボート: 船体 + キャビン + スクリュー水しぶき
    static BufferedImage createBoatIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 船体 (白 & ターコイズブルー)
        g.setColor(new Color(13, 148, 136));
        int[] hx = {4, 27, 24, 5};
        int[] hy = {18, 16, 24, 24};
        g.fillPolygon(hx, hy, 4);

        // デッキ (白)
        g.setColor(new Color(245, 245, 245));
        g.fillRect(6, 15, 16, 3);

        // キャビン・風防
        g.setColor(new Color(30, 41, 59));
        g.fillRect(10, 11, 8, 4);
        g.setColor(new Color(56, 189, 248)); // ガラス反射
        g.fillRect(13, 11, 4, 3);

        // 船尾エンジン
        g.setColor(new Color(30, 30, 35));
        g.fillRect(2, 16, 3, 7);

        // 水しぶき (波)
        g.setColor(new Color(186, 230, 253, 180));
        g.drawLine(2, 25, 28, 25);
        g.drawLine(4, 26, 22, 26);

        g.dispose();
        return img;
    }

    // 7. Tier 4 バッテリー: ゴールド/カーボンパック + XT60黄色コネクタ + インジケータ
    static BufferedImage createBatteryIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // ケーブル (赤・黒)
        g.setColor(new Color(220, 38, 38));
        g.drawArc(6, 5, 8, 8, 90, 180);
        g.setColor(new Color(30, 30, 30));
        g.drawArc(8, 7, 8, 8, 90, 180);

        // XT60 コネクタ (黄色)
        g.setColor(new Color(234, 179, 8));
        g.fillRoundRect(13, 3, 6, 6, 2, 2);
        g.setColor(new Color(161, 98, 7));
        g.drawRoundRect(13, 3, 6, 6, 2, 2);

        // バッテリー本体 (カーボン調ダーク + ゴールドラベル)
        g.setColor(new Color(24, 24, 27));
        g.fillRoundRect(5, 9, 22, 18, 3, 3);

        // ゴールド帯 (Tier 4の証)
        g.setColor(new Color(234, 179, 8));
        g.fillRect(5, 14, 22, 4);
        g.setColor(new Color(254, 240, 138));
        g.fillRect(7, 15, 18, 1);

        // バランス端子
        g.setColor(new Color(240, 240, 240));
        g.fillRect(20, 7, 4, 3);

        // 4段フルLED残量バー (緑点灯)
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(34, 197, 94));
            g.fillRect(8 + i * 4, 21, 3, 2);
        }

        // 外枠
        g.setColor(new Color(9, 9, 11));
        g.drawRoundRect(5, 9, 21, 18, 3, 3);

        g.dispose();
        return img;
    }

    // 8. Tier 4 ブラシレスモーター: レッドアルマイト缶 + シルバーシャフト + 銅線
    static BufferedImage createMotorIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 3本シリコン配線 (下部: 赤・黄・青)
        g.setColor(new Color(220, 38, 38)); g.fillRect(10, 25, 2, 5);
        g.setColor(new Color(234, 179, 8));  g.fillRect(15, 25, 2, 5);
        g.setColor(new Color(37, 99, 235));  g.fillRect(20, 25, 2, 5);

        // 回転シャフト (上部スチール)
        g.setColor(new Color(203, 213, 225));
        g.fillRect(15, 2, 2, 7);
        g.setColor(new Color(148, 163, 184));
        g.fillRect(16, 2, 1, 7);

        // ローター缶 (赤アルマイト)
        g.setColor(new Color(220, 38, 38));
        g.fillRoundRect(8, 8, 16, 12, 3, 3);

        // ハイライトライン
        g.setColor(new Color(248, 113, 113));
        g.fillRect(10, 9, 12, 2);

        // 放熱スリット（内部の銅線コイルが見える）
        for (int i = 10; i <= 20; i += 3) {
            g.setColor(new Color(30, 10, 10));
            g.fillRect(i, 13, 2, 5);
            g.setColor(new Color(180, 83, 9)); // 銅色
            g.fillRect(i, 14, 2, 3);
        }

        // ベースマウント (黒アルミプレート)
        g.setColor(new Color(39, 39, 42));
        g.fillRect(7, 21, 18, 4);

        // 外枠
        g.setColor(new Color(20, 20, 20));
        g.drawRoundRect(8, 8, 15, 12, 3, 3);

        g.dispose();
        return img;
    }

    // 9. カラーコーン: オレンジ + 白反射帯 + 黒ベース
    static BufferedImage createConeIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // 影
        g.setColor(new Color(0, 0, 0, 70));
        g.fillOval(5, 26, 22, 4);

        // 黒ゴムベース台座
        g.setColor(new Color(24, 24, 27));
        g.fillRoundRect(6, 24, 20, 4, 2, 2);

        // コーン本体 (オレンジポリゴン)
        g.setColor(new Color(234, 88, 12));
        int[] cx = {15, 17, 22, 10};
        int[] cy = {4, 4, 24, 24};
        g.fillPolygon(cx, cy, 4);

        // 白反射テープ 1 (上)
        g.setColor(new Color(245, 245, 250));
        int[] w1x = {13, 19, 19, 13};
        int[] w1y = {9, 9, 13, 13};
        g.fillPolygon(w1x, w1y, 4);

        // 白反射テープ 2 (下)
        int[] w2x = {11, 21, 21, 11};
        int[] w2y = {16, 16, 20, 20};
        g.fillPolygon(w2x, w2y, 4);

        // ハイライト (左側)
        g.setColor(new Color(251, 146, 60));
        g.drawLine(12, 23, 15, 5);

        // 輪郭
        g.setColor(new Color(20, 20, 20));
        g.drawPolygon(cx, cy, 4);
        g.drawRoundRect(6, 24, 20, 4, 2, 2);

        g.dispose();
        return img;
    }

    // 10. ブーストジャンプ台: 黄黒警告ストライプ + 青色ネオン矢印
    static BufferedImage createRampIcon() {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // スロープ台座 (コンクリートグレー)
        g.setColor(new Color(71, 85, 105));
        int[] rx = {5, 27, 27, 5};
        int[] ry = {25, 10, 16, 26};
        g.fillPolygon(rx, ry, 4);

        // 路面 (ダークアスファルト)
        g.setColor(new Color(30, 41, 59));
        int[] fx = {5, 27, 25, 4};
        int[] fy = {25, 10, 8, 23};
        g.fillPolygon(fx, fy, 4);

        // 黄色と黒のシェブロン（斜めストライプ）
        g.setColor(new Color(234, 179, 8));
        g.drawLine(8, 21, 12, 17);
        g.drawLine(14, 16, 18, 12);

        // ブーストネオン矢印 (発光シアン)
        g.setColor(new Color(56, 189, 248));
        g.drawLine(10, 19, 16, 14);
        g.drawLine(16, 14, 22, 10);
        g.setColor(new Color(255, 255, 255)); // コア発光
        g.drawLine(16, 13, 21, 9);

        // 輪郭
        g.setColor(new Color(15, 23, 42));
        g.drawPolygon(rx, ry, 4);

        g.dispose();
        return img;
    }

    // 一覧見本カタログ画像 (800x480)
    static BufferedImage createCatalogSheet(Map<String, BufferedImage> icons) {
        int w = 820;
        int h = 420;
        BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景
        g.setColor(new Color(24, 25, 28));
        g.fillRect(0, 0, w, h);

        // タイトルヘッダー
        g.setColor(new Color(36, 37, 41));
        g.fillRect(0, 0, w, 56);
        g.setColor(new Color(243, 244, 246));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("🎨 Micro Drone Mod - 32x32 Item Icons Showcase", 20, 35);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(156, 163, 175));
        g.drawString("Minecraft Style 32x32 Pixel Art Samples (Scaled 3x with 1x preview)", 490, 35);

        // アイテム定義
        String[][] list = {
            {"drone_item", "マイクロドローン", "FPVカメラ・カーボン・4発プロペラ"},
            {"drone_controller", "プロポ (送信機)", "バックライト液晶・スティック・アンテナ"},
            {"rc_car_item", "RCスポーツカー", "GTウィング・エアロ・深リムホイール"},
            {"rc_tank_item", "RC戦車", "旋回砲塔・長砲身・キャタピラ"},
            {"rc_plane_item", "RC飛行機", "主翼上反角・キャノピー・ノーズペラ"},
            {"rc_boat_item", "RCボート", "V字ハル・キャビン・水しぶき"},
            {"battery_tier_4", "Tier 4 バッテリー", "ゴールドラベル・XT60端子・4連LED"},
            {"motor_tier_4", "Tier 4 モーター", "赤アルマイト缶・銅線・3本配線"},
            {"traffic_cone", "カラーコーン", "高視認性オレンジ・2本反射帯"},
            {"boost_ramp", "ブースト台", "警告シェブロン・ネオン加速矢印"}
        };

        int startX = 20;
        int startY = 70;
        int cardW = 380;
        int cardH = 60;

        for (int i = 0; i < list.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int cx = startX + col * (cardW + 20);
            int cy = startY + row * (cardH + 8);

            String key = list[i][0];
            String name = list[i][1];
            String desc = list[i][2];
            BufferedImage icon = icons.get(key);

            // カード背景
            g.setColor(new Color(32, 33, 37));
            g.fillRoundRect(cx, cy, cardW, cardH, 8, 8);
            g.setColor(new Color(50, 52, 58));
            g.drawRoundRect(cx, cy, cardW, cardH, 8, 8);

            // アイコン枠 (背景市松模様)
            int iconBoxX = cx + 8;
            int iconBoxY = cy + 6;
            g.setColor(new Color(45, 46, 52));
            g.fillRect(iconBoxX, iconBoxY, 48, 48);

            // 拡大アイコン描画 (32x32 -> 48x48 ニアレストネイバー)
            g.drawImage(icon, iconBoxX, iconBoxY, 48, 48, null);

            // 実寸 32x32 プレビュー枠
            int miniBoxX = iconBoxX + 54;
            int miniBoxY = cy + 14;
            g.setColor(new Color(40, 41, 46));
            g.fillRect(miniBoxX, miniBoxY, 32, 32);
            g.drawImage(icon, miniBoxX, miniBoxY, 32, 32, null);

            // テキスト
            g.setColor(new Color(243, 244, 246));
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString(name, miniBoxX + 42, cy + 26);

            g.setColor(new Color(156, 163, 175));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(desc, miniBoxX + 42, cy + 44);
        }

        g.dispose();
        return sheet;
    }
}

