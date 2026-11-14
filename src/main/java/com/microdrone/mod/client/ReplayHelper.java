package com.microdrone.mod.client;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ReplayMod (ReForgedPlay) や Flashback などのリプレイ再生中を検知するヘルパークラス
 */
public class ReplayHelper {

    private static boolean initialized = false;
    private static Class<?> replayModReplayClass = null;
    private static Field replayModInstanceField = null;
    private static Method getReplayHandlerMethod = null;

    private static Class<?> flashbackClass = null;
    private static Method isInReplayMethod = null;

    private static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        try {
            replayModReplayClass = Class.forName("com.replaymod.replay.ReplayModReplay");
            replayModInstanceField = replayModReplayClass.getField("instance");
            getReplayHandlerMethod = replayModReplayClass.getMethod("getReplayHandler");
            MicroDroneMod.LOGGER.info("[ReplayHelper] ReplayMod detected in classpath.");
        } catch (Throwable ignored) {
        }

        try {
            flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
            isInReplayMethod = flashbackClass.getMethod("isInReplay");
            MicroDroneMod.LOGGER.info("[ReplayHelper] Flashback detected in classpath.");
        } catch (Throwable ignored) {
        }
    }

    /**
     * 現在リプレイ再生中 (Replay Viewer 動作中) かどうかを判定
     */
    public static boolean isReplayActive() {
        ensureInitialized();

        // 1. ReplayMod / ReForgedPlay のアクティブReplayHandlerチェック
        if (replayModInstanceField != null && getReplayHandlerMethod != null) {
            try {
                Object instance = replayModInstanceField.get(null);
                if (instance != null) {
                    Object handler = getReplayHandlerMethod.invoke(instance);
                    if (handler != null) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. ReplayHandler クラスの直接走査（静的インスタンスや別パッケージ対応）
        try {
            Class<?> handlerClass = Class.forName("com.replaymod.replay.ReplayHandler");
            // クラスが存在する場合、フィールドやメソッドからインスタンスを探す
            Field currentHandlerField = null;
            try {
                currentHandlerField = handlerClass.getDeclaredField("currentHandler");
                currentHandlerField.setAccessible(true);
                if (currentHandlerField.get(null) != null) return true;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        // 3. Flashback のチェック
        if (isInReplayMethod != null) {
            try {
                Object res = isInReplayMethod.invoke(null);
                if (Boolean.TRUE.equals(res)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. 汎用フォールバック判定
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            // リプレイ中のプレイヤーは通常 Spectator、もしくは ReplayMod の CameraEntity
            if (mc.player != null) {
                String playerClass = mc.player.getClass().getName().toLowerCase();
                if (playerClass.contains("replay") || playerClass.contains("camera")) {
                    return true;
                }
            }

            // Connection クラス名チェック
            if (mc.getConnection() != null) {
                String connClass = mc.getConnection().getClass().getName().toLowerCase();
                if (connClass.contains("replay")) {
                    return true;
                }
            }

            // Screen クラス名チェック
            if (mc.screen != null) {
                String screenClass = mc.screen.getClass().getName().toLowerCase();
                if (screenClass.contains("replay")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
