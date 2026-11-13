package com.microdrone.mod.client.gui;

import com.microdrone.mod.block.entity.DecalTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DecalEditorOpener {
    public static void open(BlockPos pos) {
        open(pos, null, false);
    }

    public static void open(BlockPos pos, net.minecraft.world.item.ItemStack carStack) {
        open(pos, carStack, false);
    }

    public static void open(BlockPos pos, net.minecraft.world.item.ItemStack carStack, boolean openedFromHand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof DecalTableBlockEntity table) {
                mc.setScreen(new DecalEditorScreen(table, carStack, openedFromHand));
            }
        }
    }
}

