package com.microdrone.mod.client.gui;

import com.microdrone.mod.inventory.RcWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * RC工作台のGUIスクリーン
 * 3x3クラフトグリッド + 完成品 + 天板展示スロット
 */
public class RcWorkbenchScreen extends AbstractContainerScreen<RcWorkbenchMenu> {
    private static final ResourceLocation CRAFTING_TABLE_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    public RcWorkbenchScreen(RcWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 29;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // 1. バニラ作業台の背景テクスチャ
        graphics.blit(CRAFTING_TABLE_LOCATION, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // 2. 右側天板展示スロット (x=152, y=35) の枠とスロット背景
        int slotX = x + 151;
        int slotY = y + 34;

        graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF373737);
        graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);

        // 展示ラベル
        graphics.drawString(this.font, Component.literal("展示"), slotX + 1, slotY - 10, 0x404040, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
