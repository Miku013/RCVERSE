package com.microdrone.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * マイクロドローン専用バッテリーアイテム (Step 5)
 * 耐久値（Damage）を利用して、消費・残航続可能距離（ブロック数）を直感的に管理。
 */
public class DroneBatteryItem extends Item {

    private final int maxFlightBlocks;

    public DroneBatteryItem(Properties properties, int maxFlightBlocks) {
        super(properties.durability(maxFlightBlocks));
        this.maxFlightBlocks = maxFlightBlocks;
    }

    public int getMaxFlightBlocks() {
        return this.maxFlightBlocks;
    }

    public int getRemainingBlocks(ItemStack stack) {
        return Math.max(0, this.maxFlightBlocks - stack.getDamageValue());
    }

    public float getRemainingPercentage(ItemStack stack) {
        return (float) getRemainingBlocks(stack) / (float) this.maxFlightBlocks;
    }

    public void consumeFlightDistance(ItemStack stack, int blocks) {
        int currentDamage = stack.getDamageValue();
        stack.setDamageValue(Math.min(this.maxFlightBlocks, currentDamage + blocks));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int remaining = getRemainingBlocks(stack);
        int percent = (int) (getRemainingPercentage(stack) * 100.0f);

        ChatFormatting color = percent > 50 ? ChatFormatting.GREEN : (percent > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);

        tooltipComponents.add(Component.literal("§7航続可能残量: ")
                .append(Component.literal(remaining + " / " + this.maxFlightBlocks + " ブロック (" + percent + "%)").withStyle(color)));
        tooltipComponents.add(Component.translatable("tooltip.microdrone.battery_desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
