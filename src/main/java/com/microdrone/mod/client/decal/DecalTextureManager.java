package com.microdrone.mod.client.decal;

import com.microdrone.mod.MicroDroneMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * .minecraft/microdrone/stickers/ フォルダ内の透過PNGステッカー画像を
 * 動的テクスチャ（DynamicTexture）としてメモリにロード・管理するクライアントマネージャー
 */
public class DecalTextureManager {

    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    private static final Map<String, Long> FILE_TIMESTAMPS = new HashMap<>();
    private static final Map<String, Float> ASPECT_CACHE = new HashMap<>();
    private static Path stickersDir = null;

    /**
     * ステッカー格納フォルダの取得・初期化
     */
    public static Path getStickersDirectory() {
        if (stickersDir == null) {
            stickersDir = Minecraft.getInstance().gameDirectory.toPath().resolve("microdrone").resolve("stickers");
            try {
                if (!Files.exists(stickersDir)) {
                    Files.createDirectories(stickersDir);
                    generateSampleSticker(stickersDir.resolve("sample_racing_43.png"));
                }
            } catch (Exception e) {
                MicroDroneMod.LOGGER.error("[Decal] Failed to initialize stickers directory: {}", e.getMessage());
            }
        }
        return stickersDir;
    }

    /**
     * フォルダ内のすべての透過PNG画像ファイル名一覧を取得
     */
    public static List<String> getAvailableStickerFiles() {
        Path dir = getStickersDirectory();
        List<String> list = new ArrayList<>();
        if (dir != null && Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(list::add);
            } catch (Exception e) {
                MicroDroneMod.LOGGER.error("[Decal] Failed to list sticker files: {}", e.getMessage());
            }
        }
        return list;
    }

    /**
     * 指定されたステッカー画像名に対応する ResourceLocation を取得（変更検知で自動リロード）
     */
    public static ResourceLocation getStickerTexture(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        Path dir = getStickersDirectory();
        if (dir == null) return null;
        Path filePath = dir.resolve(fileName);
        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            Long cachedTime = FILE_TIMESTAMPS.get(fileName);

            if (TEXTURE_CACHE.containsKey(fileName) && cachedTime != null && cachedTime == lastModified) {
                return TEXTURE_CACHE.get(fileName);
            }

            // 新規読み込みまたはファイル更新時
            try (InputStream in = Files.newInputStream(filePath)) {
                NativeImage nativeImage = NativeImage.read(in);
                float aspect = nativeImage.getHeight() > 0 ? (float) nativeImage.getWidth() / (float) nativeImage.getHeight() : 1.0f;
                ASPECT_CACHE.put(fileName, aspect);

                DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);

                String sanitized = fileName.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9_.-]", "_");
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath("microdrone", "dynamic/stickers/" + sanitized);

                Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);

                TEXTURE_CACHE.put(fileName, location);
                FILE_TIMESTAMPS.put(fileName, lastModified);
                MicroDroneMod.LOGGER.info("[Decal] Loaded sticker texture: {} (aspect={}) -> {}", fileName, aspect, location);
                return location;
            }
        } catch (Exception e) {
            MicroDroneMod.LOGGER.error("[Decal] Failed to load sticker image {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * 画像の元のアスペクト比 (幅 / 高さ) を取得 (未ロード時はロードを試行、不明時は 1.0f)
     */
    public static float getImageAspectRatio(String fileName) {
        if (fileName == null || fileName.isBlank()) return 1.0f;
        if (!ASPECT_CACHE.containsKey(fileName)) {
            getStickerTexture(fileName); // キャッシュ生成
        }
        return ASPECT_CACHE.getOrDefault(fileName, 1.0f);
    }

    /**
     * 初回起動時用のサンプルステッカー（レーシングナンバー「43」）の生成
     */
    private static void generateSampleSticker(Path targetPath) {
        if (Files.exists(targetPath)) return;
        try {
            // 64x64 の透過サークル＋レーシングナンバー「43」を NativeImage で描画
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, 64, 64, false);
            int red = 0xFF2222EE;   // 赤
            int white = 0xFFFFFFFF; // 白
            int black = 0xFF111111; // 影・黒

            // 背景は完全透明 (0x00000000)
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    image.setPixelRGBA(x, y, 0);
                }
            }

            // 円形ベース (半径 28)
            int cx = 32, cy = 32;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int distSq = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                    if (distSq <= 28 * 28) {
                        image.setPixelRGBA(x, y, white);
                    }
                    if (distSq <= 28 * 28 && distSq >= 24 * 24) {
                        image.setPixelRGBA(x, y, red);
                    }
                }
            }

            // 簡単なナンバー「43」のドット描画
            drawDigit4(image, 16, 18, black);
            drawDigit3(image, 34, 18, black);

            image.writeToFile(targetPath);
            image.close();
            MicroDroneMod.LOGGER.info("[Decal] Generated sample sticker at {}", targetPath);
        } catch (Exception e) {
            MicroDroneMod.LOGGER.warn("[Decal] Could not generate sample sticker: {}", e.getMessage());
        }
    }

    private static void drawDigit4(NativeImage img, int ox, int oy, int col) {
        // '4'
        for (int y = 0; y < 16; y++) drawThickPixel(img, ox + 10, oy + y, col);
        for (int y = 0; y < 10; y++) drawThickPixel(img, ox + 2, oy + y, col);
        for (int x = 2; x < 14; x++) drawThickPixel(img, ox + x, oy + 10, col);
    }

    private static void drawDigit3(NativeImage img, int ox, int oy, int col) {
        // '3'
        for (int x = 2; x < 12; x++) drawThickPixel(img, ox + x, oy, col);
        for (int x = 4; x < 12; x++) drawThickPixel(img, ox + x, oy + 8, col);
        for (int x = 2; x < 12; x++) drawThickPixel(img, ox + x, oy + 16, col);
        for (int y = 0; y < 17; y++) drawThickPixel(img, ox + 11, oy + y, col);
    }

    private static void drawThickPixel(NativeImage img, int x, int y, int col) {
        if (x >= 0 && x < 63 && y >= 0 && y < 63) {
            img.setPixelRGBA(x, y, col);
            img.setPixelRGBA(x + 1, y, col);
            img.setPixelRGBA(x, y + 1, col);
            img.setPixelRGBA(x + 1, y + 1, col);
        }
    }
}

