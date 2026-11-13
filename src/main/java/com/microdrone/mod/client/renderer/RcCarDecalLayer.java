package com.microdrone.mod.client.renderer;

import com.microdrone.mod.entity.RcCarEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * RCカーの車体表面に透過PNGステッカーを描画するGeckoLibレンダーレイヤー（RcVehicleDecalLayerのRCカー専用サブクラス）
 */
public class RcCarDecalLayer extends RcVehicleDecalLayer<RcCarEntity> {

    public RcCarDecalLayer(GeoEntityRenderer<RcCarEntity> entityRenderer) {
        super(entityRenderer);
    }
}
