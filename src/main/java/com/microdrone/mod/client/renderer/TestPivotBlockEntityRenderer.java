package com.microdrone.mod.client.renderer;

import com.microdrone.mod.block.entity.TestPivotBlockEntity;
import com.microdrone.mod.client.model.TestPivotModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TestPivotBlockEntityRenderer extends GeoBlockRenderer<TestPivotBlockEntity> {
    public TestPivotBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new TestPivotModel());
    }
}

