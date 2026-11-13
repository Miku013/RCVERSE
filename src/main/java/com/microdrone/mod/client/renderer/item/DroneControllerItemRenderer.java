package com.microdrone.mod.client.renderer.item;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.model.DroneControllerModel;
import com.microdrone.mod.item.DroneControllerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DroneControllerItemRenderer extends GeoItemRenderer<DroneControllerItem> {
    public static final ModelResourceLocation GUI_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "item/drone_controller_gui")
    );

    public DroneControllerItemRenderer() {
        super(new DroneControllerModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // インベントリやホットバーなどのGUI表示では16x16の2Dドット絵アイコンを描画
        if (transformType == ItemDisplayContext.GUI) {
            BakedModel guiModel = Minecraft.getInstance().getModelManager().getModel(GUI_MODEL);
            if (guiModel != null && guiModel != Minecraft.getInstance().getModelManager().getMissingModel()) {
                for (BakedModel pass : guiModel.getRenderPasses(stack, true)) {
                    for (RenderType renderType : pass.getRenderTypes(stack, true)) {
                        VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
                        Minecraft.getInstance().getItemRenderer().renderModelLists(pass, stack, packedLight, packedOverlay, poseStack, vertexConsumer);
                    }
                }
                return;
            }
        }

        // 一人称・三人称・手持ち・地面設置等では従来のリアルなGeckoLib 3Dモデルを描画
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    }
}
