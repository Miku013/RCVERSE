package com.microdrone.mod.block.entity;

import com.microdrone.mod.block.StartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * スタートブロックのBlockEntity
 * カウントダウンタイマー、LED点灯状態の管理、サウンド・プレイヤー通知を担当
 */
public class StartBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public static final int STATE_IDLE = 0;   // 全消灯 (led_off_1~3 表示, 他非表示)
    public static final int STATE_RED_1 = 1;  // 赤1点灯 (led_red_1 表示, led_off_1 非表示, led_off_2~3 表示)
    public static final int STATE_RED_2 = 2;  // 赤2点灯 (led_red_1~2 表示, led_off_1~2 非表示, led_off_3 表示)
    public static final int STATE_RED_3 = 3;  // 赤3点灯 (led_red_1~3 表示, led_off_1~3 非表示)
    public static final int STATE_GREEN = 4;  // 緑全灯 (led_green 表示, 赤全非表示, off全非表示)

    private int lightState = STATE_IDLE;
    private int countdownTicks = 0; // シーケンス進行カウンタ (0: 待機中)

    public StartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.START_BLOCK_BE.get(), pos, state);
    }

    public int getLightState() {
        return this.lightState;
    }

    public boolean triggerCountdown(Player player) {
        if (this.countdownTicks > 0) {
            return false; // すでにカウントダウン進行中
        }
        this.countdownTicks = 1; // スタート
        applyState(STATE_RED_1);
        playBeep(1.0f);
        notifyPlayers(Component.literal("§c§l[START SIGNAL] §e3..."));
        return true;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;

        if (this.countdownTicks > 0) {
            this.countdownTicks++;

            // 20 ticks = 1.0秒間隔
            if (this.countdownTicks == 20) {
                applyState(STATE_RED_2);
                playBeep(1.0f);
                notifyPlayers(Component.literal("§c§l[START SIGNAL] §e2..."));
            } else if (this.countdownTicks == 40) {
                applyState(STATE_RED_3);
                playBeep(1.0f);
                notifyPlayers(Component.literal("§c§l[START SIGNAL] §e1..."));
            } else if (this.countdownTicks == 60) {
                applyState(STATE_GREEN);
                playGoSound();
                notifyPlayers(Component.literal("§a§l[START SIGNAL] §2§lGO!!!!!"));
            } else if (this.countdownTicks >= 100) { // GREEN点灯後2秒（計5秒）で待機状態に復帰
                applyState(STATE_IDLE);
                this.countdownTicks = 0;
            }
        }
    }

    private void applyState(int newState) {
        this.lightState = newState;
        this.setChanged();
        if (this.level != null) {
            BlockState curState = this.getBlockState();
            boolean isLit = newState > 0;
            if (curState.hasProperty(StartBlock.LIT) && curState.getValue(StartBlock.LIT) != isLit) {
                this.level.setBlock(this.worldPosition, curState.setValue(StartBlock.LIT, isLit), 3);
            } else {
                this.level.sendBlockUpdated(this.worldPosition, curState, curState, 3);
            }
        }
    }

    private void playBeep(float pitch) {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, pitch);
        }
    }

    private void playGoSound() {
        if (this.level != null) {
            this.level.playSound(null, this.worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2f, 2.0f);
            this.level.playSound(null, this.worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.5f);
        }
    }

    private void notifyPlayers(Component message) {
        if (this.level != null) {
            this.level.players().stream()
                    .filter(p -> p.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) < 900.0)
                    .forEach(p -> p.displayClientMessage(message, true));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("LightState", this.lightState);
        tag.putInt("CountdownTicks", this.countdownTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.lightState = tag.getInt("LightState");
        this.countdownTicks = tag.getInt("CountdownTicks");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
