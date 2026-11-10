import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class GenerateCarTemplates {
    public static void main(String[] args) throws Exception {
        String itemDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/src/main/resources/assets/microdrone/textures/item/";
        String outDir = "c:/Users/mikum/.gemini/antigravity-ide/scratch/micro_drone_mod/templates/";
        String brainDir = "C:/Users/mikum/.gemini/antigravity/brain/4cc19972-ca6e-49f1-8e74-16862cb81cc8/";

        File file86 = new File(itemDir + "rc_86_item.png");
        File fileCar = new File(itemDir + "rc_car_item.png");

        if (file86.exists()) {
            BufferedImage orig86 = ImageIO.read(file86);
            generateCarTemplateSet("rc_86", orig86, outDir, brainDir);
        }

        if (fileCar.exists()) {
            BufferedImage origCar = ImageIO.read(fileCar);
            generateCarTemplateSet("rc_car", origCar, outDir, brainDir);
        }

        System.out.println("ALL_CAR_TEMPLATES_GENERATED_SUCCESSFULLY");
    }

    private static void generateCarTemplateSet(String prefix, BufferedImage orig, String outDir, String brainDir) throws Exception {
        int targetW = 32;
        int targetH = 32;

        // 1. スケーリングして 32x32 のベースドット絵を生成 (高品質バイリニア縮小)
        // 少し余白を整えるため、中央 28x28 程度に収まるように配置
        BufferedImage scaled32 = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gScale = scaled32.createGraphics();
        gScale.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gScale.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gScale.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 元画像のバウンディングボックスを取得
        int minX = orig.getWidth(), maxX = 0, minY = orig.getHeight(), maxY = 0;
        for (int y = 0; y < orig.getHeight(); y++) {
            for (int x = 0; x < orig.getWidth(); x++) {
                int a = (orig.getRGB(x, y) >> 24) & 0xFF;
                if (a > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        int boxW = Math.max(1, maxX - minX);
        int boxH = Math.max(1, maxY - minY);
        
        // 32x32 の内側 (マージン1~2px、30x30程度) にアスペクト比を維持して描画
        double scale = Math.min(28.0 / boxW, 26.0 / boxH);
        int drawW = (int) Math.round(boxW * scale);
        int drawH = (int) Math.round(boxH * scale);
        int drawX = (targetW - drawW) / 2;
        int drawY = (targetH - drawH) / 2 + 1; // やや下寄せ（地面感）

        gScale.drawImage(orig, drawX, drawY, drawX + drawW, drawY + drawH, minX, minY, maxX, maxY, null);
        gScale.dispose();

        // 1-A: ベースドット絵 (そのまま使える 32x32)
        save(scaled32, outDir + prefix + "_template_base_32x32.png", brainDir + prefix + "_template_base_32x32.png");

        // 2. 輪郭線・枠線のみのテンプレート (32x32 Line Art / Outline)
        BufferedImage outline32 = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        // 不透明度・エッジ判定
        int[][] alphaMap = new int[targetW][targetH];
        int[][] lumMap = new int[targetW][targetH];
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int argb = scaled32.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                alphaMap[x][y] = a;
                lumMap[x][y] = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            }
        }

        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                if (alphaMap[x][y] > 40) {
                    boolean isOuterEdge = false;
                    // 上下左右に透明ピクセルがあるか
                    int[] dx = {0, 0, -1, 1};
                    int[] dy = {-1, 1, 0, 0};
                    for (int d = 0; d < 4; d++) {
                        int nx = x + dx[d];
                        int ny = y + dy[d];
                        if (nx < 0 || nx >= targetW || ny < 0 || ny >= targetH || alphaMap[nx][ny] <= 40) {
                            isOuterEdge = true;
                            break;
                        }
                    }

                    // 内部の明暗差（エッジ）
                    boolean isInnerEdge = false;
                    for (int d = 0; d < 4; d++) {
                        int nx = x + dx[d];
                        int ny = y + dy[d];
                        if (nx >= 0 && nx < targetW && ny >= 0 && ny < targetH && alphaMap[nx][ny] > 40) {
                            if (Math.abs(lumMap[x][y] - lumMap[nx][ny]) > 60) {
                                isInnerEdge = true;
                                break;
                            }
                        }
                    }

                    if (isOuterEdge) {
                        // 外枠はくっきりした黒 (ARGB: 0xFF202020)
                        outline32.setRGB(x, y, 0xFF222222);
                    } else if (isInnerEdge) {
                        // 内部の境界線は少し落ち着いた濃いグレー (ARGB: 0xFF444444)
                        outline32.setRGB(x, y, 0xFF555555);
                    }
                }
            }
        }
        save(outline32, outDir + prefix + "_template_outline_32x32.png", brainDir + prefix + "_template_outline_32x32.png");

        // 3. 塗り絵用白地ブランクテンプレート (枠線 + 白ボディ + 黒タイヤ)
        BufferedImage coloring32 = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                if (alphaMap[x][y] > 40) {
                    int outlinePixel = outline32.getRGB(x, y);
                    if (((outlinePixel >> 24) & 0xFF) > 0) {
                        coloring32.setRGB(x, y, outlinePixel);
                    } else {
                        // 内部ピクセル
                        if (lumMap[x][y] < 60) {
                            // 暗い部分（タイヤ、ホイールハウス、窓など）
                            coloring32.setRGB(x, y, 0xFF3A3A3C);
                        } else if (lumMap[x][y] < 110) {
                            // 窓ガラス
                            coloring32.setRGB(x, y, 0xFF88A0B8);
                        } else {
                            // ボディ（白：自由に塗り替え可能）
                            coloring32.setRGB(x, y, 0xFFFFFFFF);
                        }
                    }
                }
            }
        }
        save(coloring32, outDir + prefix + "_template_coloring_32x32.png", brainDir + prefix + "_template_coloring_32x32.png");

        // 4. 作業・ドット打ち用 拡大グリッドテンプレート (512x512)
        int cellSize = 16;
        int size = targetW * cellSize;
        BufferedImage grid512 = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g512 = grid512.createGraphics();

        // 背景チェッカー
        Color c1 = new Color(245, 245, 245, 255);
        Color c2 = new Color(225, 225, 225, 255);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                g512.setColor(((x + y) % 2 == 0) ? c1 : c2);
                g512.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
            }
        }

        // 塗り絵テンプレートをセル単位で描画
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int rgb = coloring32.getRGB(x, y);
                if (((rgb >> 24) & 0xFF) > 30) {
                    g512.setColor(new Color(rgb, true));
                    g512.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
                }
            }
        }

        // 1pxグリッド線
        g512.setColor(new Color(180, 180, 180, 140));
        g512.setStroke(new BasicStroke(1));
        for (int i = 0; i <= targetW; i++) {
            g512.drawLine(i * cellSize, 0, i * cellSize, size);
            g512.drawLine(0, i * cellSize, size, i * cellSize);
        }

        // 8pxブロック区切り
        g512.setColor(new Color(100, 140, 220, 180));
        g512.setStroke(new BasicStroke(2));
        for (int i = 0; i <= targetW; i += 8) {
            g512.drawLine(i * cellSize, 0, i * cellSize, size);
            g512.drawLine(0, i * cellSize, size, i * cellSize);
        }

        // センター十字線
        g512.setColor(new Color(255, 60, 60, 200));
        g512.setStroke(new BasicStroke(2));
        g512.drawLine(16 * cellSize, 0, 16 * cellSize, size);
        g512.drawLine(0, 16 * cellSize, size, 16 * cellSize);

        // 外周枠
        g512.setColor(new Color(50, 50, 50, 255));
        g512.setStroke(new BasicStroke(2));
        g512.drawRect(0, 0, size - 1, size - 1);

        g512.dispose();
        save(grid512, outDir + prefix + "_template_grid_512x512.png", brainDir + prefix + "_template_grid_512x512.png");
    }

    private static void save(BufferedImage img, String path1, String path2) throws Exception {
        ImageIO.write(img, "PNG", new File(path1));
        ImageIO.write(img, "PNG", new File(path2));
        System.out.println("Saved: " + path1);
    }
}

