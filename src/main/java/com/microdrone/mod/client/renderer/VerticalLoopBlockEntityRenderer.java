package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.entity.VerticalLoopBlockEntity;
import com.microdrone.mod.client.model.VerticalLoopModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class VerticalLoopBlockEntityRenderer extends GeoBlockRenderer<VerticalLoopBlockEntity> {
    public VerticalLoopBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new VerticalLoopModel());
    }
}

