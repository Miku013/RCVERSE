package com.microdrone.mod.client.model;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.entity.StartBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * スタートブロックのGeckoLib 3Dモデル
 * カウントダウン状態に応じて、各LED（赤1〜3、緑全灯、消灯時レンズ）の表示・非表示をリアルタイム制御
 */
public class StartBlockModel extends GeoModel<StartBlockEntity> {
    private static final ResourceLocation MODEL_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/start_block.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/block/start_block.png");

    @Override
    public ResourceLocation getModelResource(StartBlockEntity animatable) {
        return MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(StartBlockEntity animatable) {
        return TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(StartBlockEntity animatable) {
        return null;
    }

    @Override
    public void setCustomAnimations(StartBlockEntity animatable, long instanceId, AnimationState<StartBlockEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        int state = animatable.getLightState();
        // 0: OFF, 1: RED1, 2: RED2, 3: RED3, 4: GREEN

        GeoBone off1 = getAnimationProcessor().getBone("led_off_1");
        GeoBone off2 = getAnimationProcessor().getBone("led_off_2");
        GeoBone off3 = getAnimationProcessor().getBone("led_off_3");

        GeoBone red1 = getAnimationProcessor().getBone("led_red_1");
        GeoBone red2 = getAnimationProcessor().getBone("led_red_2");
        GeoBone red3 = getAnimationProcessor().getBone("led_red_3");

        GeoBone green = getAnimationProcessor().getBone("led_green");

        boolean isRed1 = (state >= 1 && state <= 3);
        boolean isRed2 = (state >= 2 && state <= 3);
        boolean isRed3 = (state == 3);
        boolean isGreen = (state == 4);

        // 赤LED
        if (red1 != null) red1.setHidden(!isRed1);
        if (red2 != null) red2.setHidden(!isRed2);
        if (red3 != null) red3.setHidden(!isRed3);

        // 消灯時レンズ（赤または緑が点灯している箇所は非表示にして発光レンズと切り替え）
        if (off1 != null) off1.setHidden(isRed1 || isGreen);
        if (off2 != null) off2.setHidden(isRed2 || isGreen);
        if (off3 != null) off3.setHidden(isRed3 || isGreen);

        // 緑LED（全灯）
        if (green != null) green.setHidden(!isGreen);
    }
}
