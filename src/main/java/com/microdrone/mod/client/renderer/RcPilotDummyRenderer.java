package com.microdrone.mod.client.renderer;

import com.microdrone.mod.entity.RcPilotDummyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * 操縦者プレイヤーの身代わりクローン用レンダラー。
 * プレイヤー本人のスキンテクスチャ（スリム/ワイド対応）、防具、手持ちのプロポ、および操縦ポーズを再現する。
 */
public class RcPilotDummyRenderer extends LivingEntityRenderer<RcPilotDummyEntity, PlayerModel<RcPilotDummyEntity>> {

    private final PlayerModel<RcPilotDummyEntity> wideModel;
    private final PlayerModel<RcPilotDummyEntity> slimModel;

    public RcPilotDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        // 防具レイヤーの追加（操縦者が着ているアーマーを表示）
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));

        // 手持ちアイテムレイヤーの追加（操縦者が持っているプロポを表示）
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(RcPilotDummyEntity entity) {
        GameProfile profile = entity.getOwnerProfile();
        if (profile != null) {
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            return skin.texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    public void render(RcPilotDummyEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        GameProfile profile = entity.getOwnerProfile();
        boolean isSlim = false;
        if (profile != null) {
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            isSlim = (skin.model() == PlayerSkin.Model.SLIM);
        }

        // スリムモデル（Alex系）かワイドモデル（Steve系）かを動的に切り替え
        this.model = isSlim ? this.slimModel : this.wideModel;

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void setupRotations(RcPilotDummyEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);

        // ラジコン操縦ポーズ: 両腕をお腹・胸の前に出してプロポを両手で構える
        this.model.rightArm.xRot = -0.75f;
        this.model.rightArm.yRot = -0.22f;
        this.model.rightArm.zRot = 0.05f;

        this.model.leftArm.xRot = -0.75f;
        this.model.leftArm.yRot = 0.22f;
        this.model.leftArm.zRot = -0.05f;

        // 手持ちアイテム用ボーン（スリーブ）も連動
        this.model.rightSleeve.copyFrom(this.model.rightArm);
        this.model.leftSleeve.copyFrom(this.model.leftArm);
    }
}
