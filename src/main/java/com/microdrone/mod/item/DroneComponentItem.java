package com.microdrone.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class DroneComponentItem extends Item {
    private final String descriptionKey;

    public DroneComponentItem(Properties properties, String descriptionKey) {
        super(properties);
        this.descriptionKey = descriptionKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.component_tag").withStyle(ChatFormatting.DARK_AQUA));
        if (this.descriptionKey != null && !this.descriptionKey.isEmpty()) {
            tooltipComponents.add(Component.translatable(this.descriptionKey).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}

