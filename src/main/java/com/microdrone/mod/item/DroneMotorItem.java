package com.microdrone.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class DroneMotorItem extends Item {
    private final int tier;
    private final double maxHorizontalSpeed;
    private final double acceleration;
    private final double maxVerticalSpeed;
    private final int motorColor; // ARGB
    private final String tierName;

    public DroneMotorItem(Properties properties, int tier, double maxHorizontalSpeed, double acceleration, double maxVerticalSpeed, int motorColor, String tierName) {
        super(properties);
        this.tier = tier;
        this.maxHorizontalSpeed = maxHorizontalSpeed;
        this.acceleration = acceleration;
        this.maxVerticalSpeed = maxVerticalSpeed;
        this.motorColor = motorColor;
        this.tierName = tierName;
    }

    public int getTier() {
        return this.tier;
    }

    public double getMaxHorizontalSpeed() {
        return this.maxHorizontalSpeed;
    }

    public double getAcceleration() {
        return this.acceleration;
    }

    public double getMaxVerticalSpeed() {
        return this.maxVerticalSpeed;
    }

    public int getMotorColor() {
        return this.motorColor;
    }

    public String getTierName() {
        return this.tierName;
    }

    /**
     * 最高速度の概算 (m/s 表示用)
     */
    public double getDisplaySpeedMps() {
        return this.maxHorizontalSpeed * 20.0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        ChatFormatting colorFormat;
        switch (this.tier) {
            case 2 -> colorFormat = ChatFormatting.AQUA;
            case 3 -> colorFormat = ChatFormatting.GOLD;
            case 4 -> colorFormat = ChatFormatting.RED;
            default -> colorFormat = ChatFormatting.GRAY;
        }

        tooltipComponents.add(Component.translatable("tooltip.microdrone.motor_tier", this.tier, this.tierName).withStyle(colorFormat));
        tooltipComponents.add(Component.translatable("tooltip.microdrone.motor_speed", String.format("%.1f", getDisplaySpeedMps())).withStyle(ChatFormatting.WHITE));
        tooltipComponents.add(Component.translatable("tooltip.microdrone.motor_accel", String.format("%.2f", this.acceleration * 20.0)).withStyle(ChatFormatting.WHITE));
        tooltipComponents.add(Component.translatable("tooltip.microdrone.motor_install_hint").withStyle(ChatFormatting.DARK_GRAY));

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

