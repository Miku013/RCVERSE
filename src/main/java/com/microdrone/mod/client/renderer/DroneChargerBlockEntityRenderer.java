package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.entity.DroneChargerBlockEntity;
import com.microdrone.mod.client.model.DroneChargerModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class DroneChargerBlockEntityRenderer extends GeoBlockRenderer<DroneChargerBlockEntity> {
    public DroneChargerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new DroneChargerModel());

        // GeckoLib 準拠のバッテリー描画レイヤーを登録（battery_slot ボーンに自動吸着描画）
        this.addRenderLayer(new DroneChargerBatteryLayer(this));
    }
}
