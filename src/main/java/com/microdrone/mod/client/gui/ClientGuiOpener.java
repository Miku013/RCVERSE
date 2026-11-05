package com.microdrone.mod.client.gui;

import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.client.Minecraft;

public class ClientGuiOpener {
    public static void openRcCarTuningScreen(RcCarEntity car) {
        Minecraft.getInstance().setScreen(new RcCarTuningScreen(car));
    }
}

