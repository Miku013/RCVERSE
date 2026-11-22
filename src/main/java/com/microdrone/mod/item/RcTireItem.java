package com.microdrone.mod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * RCカー用換装タイヤパーツアイテム
 */
public class RcTireItem extends Item {

    public enum TireType {
        NORMAL("normal", 1.0f, 1.0f, 1.0f, false, false, ChatFormatting.GRAY),
        DRIFT("drift", 0.35f, 0.15f, 0.95f, false, false, ChatFormatting.AQUA),
        GRIP("grip", 2.20f, 1.80f, 1.25f, false, false, ChatFormatting.GOLD),
        OFFROAD("offroad", 1.10f, 0.85f, 1.35f, true, false, ChatFormatting.DARK_GREEN),
        SPIKE("spike", 1.20f, 0.90f, 1.10f, false, true, ChatFormatting.LIGHT_PURPLE);

        private final String id;
        private final float lateralGripMultiplier; // 横グリップ倍率 (スピンしにくさ)
        private final float scrubDragMultiplier;   // ドリフト減速抵抗倍率
        private final float tractionMultiplier;    // 前進トラクション加速倍率
        private final boolean offroadImmunity;     // 不整地（草・土・砂利等）での減速無効
        private final boolean iceImmunity;         // 氷上でのツルツル滑り無効
        private final ChatFormatting color;

        TireType(String id, float lateralGripMultiplier, float scrubDragMultiplier,
                 float tractionMultiplier, boolean offroadImmunity, boolean iceImmunity,
                 ChatFormatting color) {
            this.id = id;
            this.lateralGripMultiplier = lateralGripMultiplier;
            this.scrubDragMultiplier = scrubDragMultiplier;
            this.tractionMultiplier = tractionMultiplier;
            this.offroadImmunity = offroadImmunity;
            this.iceImmunity = iceImmunity;
            this.color = color;
        }

        public String getId() {
            return id;
        }

        public float getLateralGripMultiplier() {
            return lateralGripMultiplier;
        }

        public float getScrubDragMultiplier() {
            return scrubDragMultiplier;
        }

        public float getTractionMultiplier() {
            return tractionMultiplier;
        }

        public boolean isOffroadImmunity() {
            return offroadImmunity;
        }

        public boolean isIceImmunity() {
            return iceImmunity;
        }

        public ChatFormatting getColor() {
            return color;
        }

        public static TireType fromId(String id) {
            for (TireType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return NORMAL;
        }
    }

    private final TireType tireType;

    public RcTireItem(Properties properties, TireType tireType) {
        super(properties);
        this.tireType = tireType;
    }

    public TireType getTireType() {
        return tireType;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.microdrone.tire_type." + this.tireType.getId())
                .withStyle(this.tireType.getColor()));
        tooltipComponents.add(Component.translatable("tooltip.microdrone.tire_desc." + this.tireType.getId())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
