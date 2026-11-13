package com.microdrone.mod.block.entity;

import com.microdrone.mod.inventory.RcWorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * RC工作台のBlockEntity
 * 天板上に展示するアイテムの保存・同期と、3x3組み立てメニューの提供を担当
 */
public class RcWorkbenchBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 作業台天板の上に展示されているアイテム (RCマシン、送信機、パーツ等)
    private ItemStack displayedItem = ItemStack.EMPTY;

    public RcWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RC_WORKBENCH_BE.get(), pos, state);
    }

    public ItemStack getDisplayedItem() {
        return this.displayedItem;
    }

    public void setDisplayedItem(ItemStack item) {
        this.displayedItem = item != null ? item : ItemStack.EMPTY;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public net.minecraft.world.Container getDisplayContainer() {
        return new net.minecraft.world.Container() {
            @Override
            public int getContainerSize() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return displayedItem.isEmpty();
            }

            @Override
            public ItemStack getItem(int slot) {
                return displayedItem;
            }

            @Override
            public ItemStack removeItem(int slot, int amount) {
                ItemStack split = displayedItem.split(amount);
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return split;
            }

            @Override
            public ItemStack removeItemNoUpdate(int slot) {
                ItemStack old = displayedItem;
                displayedItem = ItemStack.EMPTY;
                return old;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                setDisplayedItem(stack);
            }

            @Override
            public void setChanged() {
                RcWorkbenchBlockEntity.this.setChanged();
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clearContent() {
                displayedItem = ItemStack.EMPTY;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.microdrone.rc_workbench");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RcWorkbenchMenu(containerId, playerInventory, ContainerLevelAccess.create(this.level, this.worldPosition), this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.displayedItem.isEmpty()) {
            tag.put("DisplayedItem", this.displayedItem.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("DisplayedItem", 10)) {
            this.displayedItem = ItemStack.parse(registries, tag.getCompound("DisplayedItem")).orElse(ItemStack.EMPTY);
        } else {
            this.displayedItem = ItemStack.EMPTY;
        }
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
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        } else {
            this.displayedItem = ItemStack.EMPTY;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
