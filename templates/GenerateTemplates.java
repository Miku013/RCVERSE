import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateTemplates {
    public static void main(String[] args) throws Exception {
        String outDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/templates/";
        String brainDir = "C:/Users/mikum/.gemini/antigravity/brain/4cc19972-ca6e-49f1-8e74-16862cb81cc8/";

        // 1. 実寸 32x32 完全透明テンプレート
        BufferedImage blank32 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        save(blank32, outDir + "template_32x32_blank.png", brainDir + "template_32x32_blank.png");

        // 2. 実寸 32x32 ガイド線入り（透明ベースに薄い半透明のセンター線と外枠）
        BufferedImage guide32 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = guide32.createGraphics();
        // 外枠 1px (マージン注意ライン: 半透明シアン #00E5FF, alpha=60)
        g2.setColor(new Color(0, 229, 255, 60));
        g2.drawRect(0, 0, 31, 31);
        // 推奨描画セーフゾーン (28x28: x=2~29, y=2~29: alpha=40)
        g2.setColor(new Color(0, 255, 128, 40));
        g2.drawRect(2, 2, 27, 27);
        // センター十字線 (x=15, 16 / y=15, 16: 半透明イエロー/オレンジ #FFB300, alpha=80)
        g2.setColor(new Color(255, 179, 0, 80));
        g2.drawLine(15, 2, 15, 29);
        g2.drawLine(16, 2, 16, 29);
        g2.drawLine(2, 15, 29, 15);
        g2.drawLine(2, 16, 29, 16);
        g2.dispose();
        save(guide32, outDir + "template_32x32_guide.png", brainDir + "template_32x32_guide.png");

        // 3. 編集・ドット打ち用 拡大グリッドテンプレート (512x512: 32x32マス, 1マス=16x16px)
        int cellSize = 16;
        int size = 32 * cellSize; // 512
        BufferedImage grid512 = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gGrid = grid512.createGraphics();
        gGrid.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // チェッカー背景 (透過の市松模様)
        Color c1 = new Color(245, 245, 245, 255);
        Color c2 = new Color(225, 225, 225, 255);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                gGrid.setColor(((x + y) % 2 == 0) ? c1 : c2);
                gGrid.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
            }
        }

        // 外枠マージン注意領域（端の1マス = 16px）を薄く着色
        gGrid.setColor(new Color(255, 200, 200, 70));
        gGrid.fillRect(0, 0, size, cellSize); // 上
        gGrid.fillRect(0, size - cellSize, size, cellSize); // 下
        gGrid.fillRect(0, 0, cellSize, size); // 左
        gGrid.fillRect(size - cellSize, 0, cellSize, size); // 右

        // セーフゾーン（内側 28x28 マス）枠線
        gGrid.setColor(new Color(0, 200, 100, 150));
        gGrid.setStroke(new BasicStroke(2));
        gGrid.drawRect(2 * cellSize, 2 * cellSize, 28 * cellSize, 28 * cellSize);

        // 1ピクセル単位の細いグリッド線
        gGrid.setColor(new Color(180, 180, 180, 160));
        gGrid.setStroke(new BasicStroke(1));
        for (int i = 0; i <= 32; i++) {
            gGrid.drawLine(i * cellSize, 0, i * cellSize, size);
            gGrid.drawLine(0, i * cellSize, size, i * cellSize);
        }

        // 8マスごとの太いブロック区切り線
        gGrid.setColor(new Color(100, 140, 220, 200));
        gGrid.setStroke(new BasicStroke(2));
        for (int i = 0; i <= 32; i += 8) {
            gGrid.drawLine(i * cellSize, 0, i * cellSize, size);
            gGrid.drawLine(0, i * cellSize, size, i * cellSize);
        }

        // センター十字線 (16マスの境界)
        gGrid.setColor(new Color(255, 60, 60, 220));
        gGrid.setStroke(new BasicStroke(2));
        gGrid.drawLine(16 * cellSize, 0, 16 * cellSize, size);
        gGrid.drawLine(0, 16 * cellSize, size, 16 * cellSize);

        // 最外枠
        gGrid.setColor(new Color(60, 60, 60, 255));
        gGrid.setStroke(new BasicStroke(2));
        gGrid.drawRect(0, 0, size - 1, size - 1);

        gGrid.dispose();
        save(grid512, outDir + "template_32x32_grid_512.png", brainDir + "template_32x32_grid_512.png");

        System.out.println("TEMPLATES_GENERATED_SUCCESSFULLY");
    }

    private static void save(BufferedImage img, String path1, String path2) throws Exception {
        ImageIO.write(img, "PNG", new File(path1));
        ImageIO.write(img, "PNG", new File(path2));
        System.out.println("Saved: " + path1);
    }
}

