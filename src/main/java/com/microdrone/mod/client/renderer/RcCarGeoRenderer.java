package com.microdrone.mod.client.renderer;

import com.microdrone.mod.client.model.RcCarGeoModel;
import com.microdrone.mod.entity.RcCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.Locale;

public class RcCarGeoRenderer extends GeoEntityRenderer<RcCarEntity> {

    // 通常ライト／ブレーキ強発光の加算カラー (ARGB)
    private static final int EMISSIVE_COLOR_FRONT = 0xFFD8D8D8;
    private static final int EMISSIVE_COLOR_TAIL_DIM = 0xFF555555;
    private static final int EMISSIVE_COLOR_BRAKE_PRIMARY = 0xFFFFFFFF;
    private static final int EMISSIVE_COLOR_BRAKE_BOOST = 0xFF808080;

    enum RenderPass {
        BASE,
        GLASS,
        EMISSIVE
    }

    private RenderPass renderPass = RenderPass.BASE;

    private enum LightBoneType {
        NONE,
        RETRACT_BODY,
        FRONT_OR_GENERAL,
        REAR_OR_BRAKE
    }

    public RcCarGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new RcCarGeoModel());
        this.shadowRadius = 0.25F; // 1/5 RCカーの影の半径
        addRenderLayer(new RcCarGlassLayer(this));
        addRenderLayer(new RcCarEmissiveLayer(this));
        addRenderLayer(new RcCarDecalLayer(this));
    }

    void setRenderPass(RenderPass renderPass) {
        this.renderPass = renderPass;
    }

    @Override
    public void render(RcCarEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // レスキュー点滅演出: 点滅タイマー作動中は2tickおきに描画をスキップして高速点滅
        if (entity.getResyncBlinkTicks() > 0) {
            if ((entity.getResyncBlinkTicks() / 2) % 2 == 0) {
                return;
            }
        }

        poseStack.pushPose();

        // 1/5 スケールに縮小（手のひら・卓上マイクロRCカーサイズ）+ 外部コンフィグ倍率適用
        float configScale = com.microdrone.mod.config.car.CarConfigManager.getConfig(entity.getVariantId()).properties.scale;
        poseStack.scale(0.2F * configScale, 0.2F * configScale, 0.2F * configScale);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        // 車体の進行方向の回転（正面合わせ）
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        // サスペンション・路面追従によるロール（横傾き: 符号反転 -roll で持ち上がり/沈み込みを正常化）
        float roll = Mth.lerp(partialTick, entity.prevRollAngle, entity.getRollAngle());
        if (Math.abs(roll) > 0.01f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-roll));
        }

        // サスペンション・路面追従によるピッチ（前後傾き: 符号反転 -pitch で段差前上がり/ノーズダイブを正常化）
        float pitch = Mth.lerp(partialTick, entity.prevPitchAngle, entity.getPitchAngle());
        if (Math.abs(pitch) > 0.01f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, RcCarEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        boolean isGlass = isGlassBone(bone.getName());
        boolean isBody = isBodyBone(bone.getName());
        LightBoneType lightType = classifyLightBone(bone.getName());
        boolean headlightOn = animatable.isHeadlightOn();
        boolean brakeOn = animatable.isBrakeLightOn();

        // 染料による車体塗装色 (未塗装の場合はデフォルトテクスチャ colour)
        int bodyColor = animatable.getBodyColor();
        int effectiveColour = (bodyColor != -1 && isBody) ? (0xFF000000 | (bodyColor & 0x00FFFFFF)) : colour;

        if (this.renderPass == RenderPass.GLASS) {
            int cubeColour = isGlass ? colour : 0;
            renderBoneFiltered(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour, packedLight, cubeColour, 0);
            return;
        }

        if (this.renderPass == RenderPass.EMISSIVE) {
            int primaryColor = 0;
            int boostColor = 0;

            if (lightType == LightBoneType.REAR_OR_BRAKE) {
                if (brakeOn) {
                    primaryColor = EMISSIVE_COLOR_BRAKE_PRIMARY;
                    boostColor = EMISSIVE_COLOR_BRAKE_BOOST;
                } else if (headlightOn) {
                    primaryColor = EMISSIVE_COLOR_TAIL_DIM;
                }
            } else if (lightType == LightBoneType.FRONT_OR_GENERAL && headlightOn) {
                primaryColor = EMISSIVE_COLOR_FRONT;
            }

            renderBoneFiltered(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour, LightTexture.FULL_BRIGHT, primaryColor, boostColor);
            return;
        }

        // BASEパス: ガラスボーンは半透明レイヤー側で描くため、ここではキューブ描画のみスキップして子ボーンへ進む
        if (isGlass) {
            renderBoneFiltered(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour, packedLight, 0, 0);
            return;
        }

        if ((headlightOn && lightType != LightBoneType.NONE)
                || (brakeOn && lightType == LightBoneType.REAR_OR_BRAKE)) {
            packedLight = LightTexture.FULL_BRIGHT;
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, effectiveColour);
    }

    /**
     * ボーンの変換行列と子ボーン再帰を維持したまま、条件を満たすボーンのキューブのみを描画する
     */
    private void renderBoneFiltered(PoseStack poseStack, RcCarEntity animatable, GeoBone bone,
                                    RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                    boolean isReRender, float partialTick, int packedLight, int packedOverlay, int childColour,
                                    int cubeLight, int primaryCubeColour, int boostCubeColour) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        if (primaryCubeColour != 0 && !bone.isHidden()) {
            renderCubesOfBone(poseStack, bone, buffer, cubeLight, packedOverlay, primaryCubeColour);
            if (boostCubeColour != 0) {
                renderCubesOfBone(poseStack, bone, buffer, cubeLight, packedOverlay, boostCubeColour);
            }
        }
        if (!bone.isHidingChildren()) {
            renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, childColour);
        }
        poseStack.popPose();
    }

    private static boolean isGlassBone(String boneName) {
        String name = boneName.toLowerCase(Locale.ROOT);
        return name.contains("glass") || name.contains("window");
    }

    private static boolean isBodyBone(String boneName) {
        String name = boneName.toLowerCase(Locale.ROOT);
        return name.equals("body")
                || name.equals("sidemirro")
                || name.equals("sidemirror")
                || name.startsWith("retractable")
                || name.equals("roof")
                || name.equals("hood")
                || name.equals("door")
                || name.equals("trunk")
                || name.equals("bumper")
                || name.equals("spoiler");
    }

    private static LightBoneType classifyLightBone(String boneName) {
        String name = boneName.toLowerCase(Locale.ROOT);
        if (name.contains("brake")
                || name.contains("taillight")
                || name.contains("tail_light")
                || name.contains("rear_light")
                || name.contains("rearlight")) {
            return LightBoneType.REAR_OR_BRAKE;
        }
        if (name.contains("headlight")
                || name.contains("head_light")
                || name.contains("front_light")
                || name.contains("frontlight")
                || name.contains("light")
                || name.contains("lamp")
                || name.contains("emissive")) {
            return LightBoneType.FRONT_OR_GENERAL;
        }
        if (name.contains("retract")) {
            return LightBoneType.RETRACT_BODY;
        }
        return LightBoneType.NONE;
    }
}
