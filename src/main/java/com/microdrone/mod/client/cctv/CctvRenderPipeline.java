package com.microdrone.mod.client.cctv;

import com.microdrone.mod.block.RaceDisplayBlock;
import com.microdrone.mod.block.entity.RaceDisplayBlockEntity;
import com.microdrone.mod.block.entity.TracksideCameraBlockEntity;
import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 256x144 (16:9) / 30fps オフスクリーン CCTV レンダーパイプライン
 * 
 * 【カメラ相対座標系（Camera-Relative Coordinates）による完全描画】:
 * - カメラを原点 (0, 0, 0) とし、注視ベクトルに対する純粋な回転行列を lookAt で生成
 * - 車両およびコース地面をカメラからの相対オフセット (dx, dy, dz) で直接配置
 * - 座標の桁落ちやワールド座標変換の競合を 100% 排除し、車を画面中央に確実に捉える
 * - RenderSystem.applyModelViewMatrix() と lightTexture().turnOnLightLayer() の適用
 */
public class CctvRenderPipeline {

    public static final int FBO_WIDTH = 256;
    public static final int FBO_HEIGHT = 144; // 16:9
    public static final ResourceLocation CCTV_TEXTURE_LOCATION = ResourceLocation.fromNamespaceAndPath("microdrone", "cctv_frame");

    private static RenderTarget cctvTarget = null;
    private static ByteBufferBuilder cctvByteBuffer = null;
    private static MultiBufferSource.BufferSource cctvBufferSource = null;

    private static long lastRenderTimeMs = 0;
    private static int targetCarId = -1;
    private static BlockPos activeCameraPos = null;
    private static int cameraModeIndex = 0; // 0: Trackside定点, 1: Chase後方, 2: Bumper前方, 3: Side真横
    private static int switchTimerTicks = 0;
    private static String activeCameraLabel = "CAM-01 [TRACK]";
    private static boolean isInitialized = false;

    // 滑らかなカメラ位置補間
    private static double smoothCamX = 0, smoothCamY = 0, smoothCamZ = 0;

    public static void init() {
        if (cctvTarget != null) {
            cctvTarget.destroyBuffers();
        }
        cctvTarget = new TextureTarget(FBO_WIDTH, FBO_HEIGHT, true, Minecraft.ON_OSX);
        cctvTarget.setClearColor(0.40f, 0.65f, 0.92f, 1.0f); // 爽やかなスカイブルー

        if (cctvByteBuffer == null) {
            cctvByteBuffer = new ByteBufferBuilder(256 * 1024);
            cctvBufferSource = MultiBufferSource.immediate(cctvByteBuffer);
        }

        try {
            Minecraft.getInstance().getTextureManager().register(CCTV_TEXTURE_LOCATION, new CctvScreenTexture());
        } catch (Exception ignored) {}

        isInitialized = true;
    }

    public static int getTextureId() {
        if (!isInitialized || cctvTarget == null) {
            init();
        }
        return cctvTarget != null ? cctvTarget.getColorTextureId() : -1;
    }

    public static String getActiveCameraLabel() {
        return activeCameraLabel;
    }

    public static int getTargetCarId() {
        return targetCarId;
    }

    /**
     * カリング判定: プレイヤーがディスプレイを見ているかチェック
     */
    public static boolean isDisplayVisibleToPlayer(Minecraft mc, RaceDisplayBlockEntity display) {
        if (mc.player == null) return false;

        Vec3 playerEye = mc.player.getEyePosition();
        Vec3 displayCenter = Vec3.atCenterOf(display.getBlockPos());

        // 1. 距離判定 (36ブロック以内)
        double distSq = playerEye.distanceToSqr(displayCenter);
        if (distSq > 36.0 * 36.0) {
            return false;
        }

        // 2. 視線（向き）判定: プレイヤーが背を向けているか
        Vec3 lookVec = mc.player.getViewVector(1.0f).normalize();
        Vec3 toDisplay = displayCenter.subtract(playerEye).normalize();
        double dot = lookVec.dot(toDisplay);

        if (dot < 0.15) {
            return false;
        }

        // 3. ディスプレイの表面（向き）判定: プレイヤーがディスプレイの裏側にいるか
        Direction facing = display.getBlockState().getValue(RaceDisplayBlock.FACING);
        Vec3 screenNormal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 fromScreen = playerEye.subtract(displayCenter).normalize();
        if (screenNormal.dot(fromScreen) < 0.0) {
            return false;
        }

        return true;
    }

    /**
     * 30fps オフスクリーン FBO レンダリング実行
     */
    public static void renderFrameIfVisible(Minecraft mc, RaceDisplayBlockEntity display, float partialTick) {
        if (mc.level == null || mc.player == null) return;

        // 【カリング処理】
        if (!isDisplayVisibleToPlayer(mc, display)) {
            return;
        }

        // 【30 FPS レートリミッター】
        long now = System.currentTimeMillis();
        if (now - lastRenderTimeMs < 33) {
            return;
        }
        lastRenderTimeMs = now;

        if (!isInitialized || cctvTarget == null) {
            init();
        }

        // カメラローテーション管理
        tickCameraRotation(mc);

        // FBO へのオフスクリーンレンダリング実行
        renderToFbo(mc, partialTick);
    }

    /**
     * 4秒ごとのカメラ・アングル切り替えロジック
     */
    private static void tickCameraRotation(Minecraft mc) {
        switchTimerTicks++;

        Entity target = targetCarId >= 0 ? mc.level.getEntity(targetCarId) : null;
        if (!(target instanceof RcCarEntity car) || !car.isAlive()) {
            findTargetCar(mc);
            target = targetCarId >= 0 ? mc.level.getEntity(targetCarId) : null;
        }

        if (!(target instanceof RcCarEntity car)) {
            activeCameraLabel = "STANDBY - NO RC SIGNAL";
            return;
        }

        // 4秒 (80 ticks) ごとに切り替え
        if (switchTimerTicks >= 80) {
            switchTimerTicks = 0;

            // 周囲の中継カメラを収集
            List<TracksideCameraBlockEntity> cams = new ArrayList<>();
            for (TracksideCameraBlockEntity cam : TracksideCameraBlockEntity.ACTIVE_CAMERAS) {
                if (cam.getLevel() == mc.level && !cam.isRemoved()) {
                    if (car.position().distanceTo(Vec3.atCenterOf(cam.getBlockPos())) <= 64.0) {
                        cams.add(cam);
                    }
                }
            }

            if (!cams.isEmpty() && (cameraModeIndex != 0 || activeCameraPos == null)) {
                // 中継カメラ定点モード
                int idx = (int)(mc.level.getGameTime() / 80) % cams.size();
                TracksideCameraBlockEntity cam = cams.get(idx);
                activeCameraPos = cam.getBlockPos();
                cameraModeIndex = 0;
                activeCameraLabel = cam.getCameraName() + " [TRACK]";
            } else {
                // 車載カメラモード (Chase -> Bumper -> Side)
                activeCameraPos = null;
                cameraModeIndex = (cameraModeIndex % 3) + 1;
                activeCameraLabel = switch (cameraModeIndex) {
                    case 2 -> "ONBOARD [BUMPER]";
                    case 3 -> "TRACK [WHEEL]";
                    default -> "ONBOARD [CHASE]";
                };
            }
        }
    }

    private static void findTargetCar(Minecraft mc) {
        AABB scan = new AABB(mc.player.blockPosition()).inflate(128.0);
        List<RcCarEntity> cars = mc.level.getEntitiesOfClass(RcCarEntity.class, scan);
        if (!cars.isEmpty()) {
            targetCarId = cars.get(0).getId();
        } else {
            targetCarId = -1;
        }
    }

    /**
     * メモリ上の 256x144 FBO へワールド風景＆ラジコンを安全にオフスクリーン描画
     */
    private static void renderToFbo(Minecraft mc, float partialTick) {
        RenderTarget mainTarget = mc.getMainRenderTarget();
        if (mainTarget == null || cctvTarget == null) return;

        Entity target = targetCarId >= 0 ? mc.level.getEntity(targetCarId) : null;
        RcCarEntity car = (target instanceof RcCarEntity c && c.isAlive()) ? c : null;

        // 【安全対策1】メイン画面の行列＆解像度を厳格に退避
        Matrix4f originalProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        int mainWidth = mc.getWindow().getWidth();
        int mainHeight = mc.getWindow().getHeight();
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();

        try {
            // 昼夜に応じた空色クリアカラーを設定
            float skyR = 0.45f, skyG = 0.70f, skyB = 0.95f;
            if (mc.level != null) {
                float sun = mc.level.getTimeOfDay(partialTick);
                if (sun > 0.25f && sun < 0.75f) {
                    skyR = 0.05f; skyG = 0.08f; skyB = 0.16f; // 夜空
                }
            }
            cctvTarget.setClearColor(skyR, skyG, skyB, 1.0f);

            // 1. FBO バッファに切り替えてクリア
            cctvTarget.bindWrite(true);
            GlStateManager._viewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
            cctvTarget.clear(Minecraft.ON_OSX);

            if (car != null) {
                // 車両の現在座標（部分ティック補間）
                double carX = Mth.lerp(partialTick, car.xOld, car.getX());
                double carY = Mth.lerp(partialTick, car.yOld, car.getY());
                double carZ = Mth.lerp(partialTick, car.zOld, car.getZ());

                // カメラの目標位置を計算
                updateCameraPosition(car, carX, carY, carZ);

                // カメラから見た車の相対ベクトル (dx, dy, dz)
                double dx = carX - smoothCamX;
                double dy = (carY + 0.25) - smoothCamY;
                double dz = carZ - smoothCamZ;

                // 16:9 透視投影プロジェクション行列 (FOV 60°)
                Matrix4f projectionMatrix = new Matrix4f().perspective(
                        (float) Math.toRadians(60.0),
                        (float) FBO_WIDTH / (float) FBO_HEIGHT,
                        0.05f,
                        128.0f
                );
                RenderSystem.setProjectionMatrix(projectionMatrix, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

                // 【安全対策2】ModelViewStack を identity にリセット＆シェーダーへ即時適用
                mvStack.pushMatrix();
                mvStack.identity();
                RenderSystem.applyModelViewMatrix();

                // 【カメラ相対 lookAt】原点 (0,0,0) から相対ベクトル (dx, dy, dz) を正確に見つめる純粋な回転行列
                Matrix4f viewMatrix = new Matrix4f().lookAt(
                        0.0f, 0.0f, 0.0f,
                        (float) dx, (float) dy, (float) dz,
                        0.0f, 1.0f, 0.0f
                );

                PoseStack poseStack = new PoseStack();
                poseStack.last().pose().set(viewMatrix);

                // レンダリングステート設定（カリング無効化・深度テスト有効化）
                RenderSystem.disableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);

                // アスファルト地面の描画 (カメラ相対オフセット dx, dy, dz の足元)
                renderRelativeTrackGround(poseStack, (float) dx, (float)(carY - smoothCamY), (float) dz);

                // 車両の描画 (ライトマップ有効化)
                if (cctvBufferSource != null) {
                    mc.gameRenderer.lightTexture().turnOnLightLayer();

                    poseStack.pushPose();
                    // カメラ相対位置 (dx, carY - smoothCamY, dz) へ移動
                    poseStack.translate(dx, carY - smoothCamY, dz);

                    try {
                        @SuppressWarnings("unchecked")
                        EntityRenderer<RcCarEntity> renderer = (EntityRenderer<RcCarEntity>) mc.getEntityRenderDispatcher().getRenderer(car);
                        if (renderer != null) {
                            renderer.render(
                                    car,
                                    car.getYRot(),
                                    partialTick,
                                    poseStack,
                                    cctvBufferSource,
                                    LightTexture.FULL_BRIGHT
                            );
                        }
                    } catch (Exception ignored) {}

                    poseStack.popPose();
                    cctvBufferSource.endBatch();

                    mc.gameRenderer.lightTexture().turnOffLightLayer();
                }

                RenderSystem.enableCull();

                mvStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }

            // CCTV CRT 走査線エフェクト
            renderScanlines();

        } finally {
            // 【安全対策3】メインレンダーターゲット、ビューポート、プロジェクション行列を 100% 確実に復元！
            mainTarget.bindWrite(true);
            GlStateManager._viewport(0, 0, mainWidth, mainHeight);
            RenderSystem.setProjectionMatrix(originalProj, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    /**
     * カメラ位置の計算と滑らかな補間
     */
    private static void updateCameraPosition(RcCarEntity car, double carX, double carY, double carZ) {
        float yawRad = car.getYRot() * Mth.DEG_TO_RAD;
        double destCamX, destCamY, destCamZ;

        if (cameraModeIndex == 0 && activeCameraPos != null) {
            // 1. 中継カメラ定点モード (TracksideCameraBlock の位置)
            Vec3 camPos = Vec3.atCenterOf(activeCameraPos).add(0, 0.45, 0);
            destCamX = camPos.x;
            destCamY = camPos.y;
            destCamZ = camPos.z;
        } else if (cameraModeIndex == 2) {
            // 2. フロントバンパー視点 (ボンネット先端)
            destCamX = carX - Math.sin(yawRad) * 0.45;
            destCamY = carY + 0.28;
            destCamZ = carZ + Math.cos(yawRad) * 0.45;
        } else if (cameraModeIndex == 3) {
            // 3. サイドホイール視点 (車の斜め横)
            destCamX = carX - Math.cos(yawRad) * 1.8 - Math.sin(yawRad) * 0.5;
            destCamY = carY + 0.45;
            destCamZ = carZ - Math.sin(yawRad) * 1.8 + Math.cos(yawRad) * 0.5;
        } else {
            // 4. 車載チェイスカメラ (車の真後ろ2.6m、高さ1.25m)
            destCamX = carX + Math.sin(yawRad) * 2.6;
            destCamY = carY + 1.25;
            destCamZ = carZ - Math.cos(yawRad) * 2.6;
        }

        if (smoothCamX == 0 && smoothCamY == 0) {
            smoothCamX = destCamX; smoothCamY = destCamY; smoothCamZ = destCamZ;
        } else {
            smoothCamX += (destCamX - smoothCamX) * 0.35;
            smoothCamY += (destCamY - smoothCamY) * 0.35;
            smoothCamZ += (destCamZ - smoothCamZ) * 0.35;
        }
    }

    /**
     * カメラ相対座標系でのアスファルト地面＆コース白線の描画
     */
    private static void renderRelativeTrackGround(PoseStack poseStack, float cx, float cy, float cz) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = poseStack.last().pose();

        float size = 48.0f;
        float y = cy - 0.01f;

        // アスファルト地面 (ダークグレー)
        builder.addVertex(mat, cx - size, y, cz - size).setColor(48, 50, 54, 255);
        builder.addVertex(mat, cx + size, y, cz - size).setColor(48, 50, 54, 255);
        builder.addVertex(mat, cx + size, y, cz + size).setColor(48, 50, 54, 255);
        builder.addVertex(mat, cx - size, y, cz + size).setColor(48, 50, 54, 255);

        // コース白線
        builder.addVertex(mat, cx - 1.2f, y + 0.005f, cz - size).setColor(235, 235, 235, 255);
        builder.addVertex(mat, cx - 0.9f, y + 0.005f, cz - size).setColor(235, 235, 235, 255);
        builder.addVertex(mat, cx - 0.9f, y + 0.005f, cz + size).setColor(235, 235, 235, 255);
        builder.addVertex(mat, cx - 1.2f, y + 0.005f, cz + size).setColor(235, 235, 235, 255);

        builder.addVertex(mat, cx + 0.9f, y + 0.005f, cz - size).setColor(235, 235, 235, 255);
        builder.addVertex(mat, cx + 1.2f, y + 0.005f, cz - size).setColor(235, 235, 235, 255);
        builder.addVertex(mat, cx + 1.2f, y + 0.005f, cz + size).setColor(235, 235, 235, 255);
        builder.addVertex(mat, cx + 0.9f, y + 0.005f, cz + size).setColor(235, 235, 235, 255);

        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * CCTV CRT モニター風の走査線（スキャンライン）エフェクト
     */
    private static void renderScanlines() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Matrix4f ortho = new Matrix4f().setOrtho(0, FBO_WIDTH, FBO_HEIGHT, 0, -1, 1);
        RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();
        RenderSystem.applyModelViewMatrix();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int y = 0; y < FBO_HEIGHT; y += 3) {
            builder.addVertex(0, y, 0).setColor(0, 0, 0, 35);
            builder.addVertex(FBO_WIDTH, y, 0).setColor(0, 0, 0, 35);
            builder.addVertex(FBO_WIDTH, y + 1, 0).setColor(0, 0, 0, 35);
            builder.addVertex(0, y + 1, 0).setColor(0, 0, 0, 35);
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());

        mvStack.popMatrix();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}
