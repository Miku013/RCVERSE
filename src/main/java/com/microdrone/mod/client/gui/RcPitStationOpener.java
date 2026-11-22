package com.microdrone.mod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RcPitStationOpener {
    public static void open(BlockPos pos, ItemStack vehicleStack) {
        Minecraft.getInstance().setScreen(new RcPitStationScreen(pos, vehicleStack));
    }
}
