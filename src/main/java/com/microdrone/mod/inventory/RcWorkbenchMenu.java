package com.microdrone.mod.inventory;

import com.microdrone.mod.block.ModBlocks;
import com.microdrone.mod.block.entity.RcWorkbenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * RC工作台コンテナメニュー
 * 3x3クラフトグリッド + 完成品スロット + 天板展示スロット (Slot 46) + プレイヤーインベントリ
 */
public class RcWorkbenchMenu extends CraftingMenu {
    public static final int DISPLAY_SLOT_INDEX = 46;

    private final ContainerLevelAccess levelAccess;
    private final RcWorkbenchBlockEntity workbenchBe;

    // クライアント側コンストラクタ（ネットワーク同期経由）
    public RcWorkbenchMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    private RcWorkbenchMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, ContainerLevelAccess.create(playerInventory.player.level(), pos),
                playerInventory.player.level().getBlockEntity(pos) instanceof RcWorkbenchBlockEntity be ? be : null);
    }

    // サーバー側コンストラクタ
    public RcWorkbenchMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access, RcWorkbenchBlockEntity blockEntity) {
        super(containerId, playerInventory, access);
        this.levelAccess = access;
        this.workbenchBe = blockEntity;

        // 天板展示スロット (Slot 46) を右側に追加 (x=152, y=35)
        Container displayContainer = blockEntity != null ? blockEntity.getDisplayContainer() : new SimpleContainer(1);
        this.addSlot(new Slot(displayContainer, 0, 152, 35) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (workbenchBe != null) {
                    workbenchBe.setChanged();
                }
            }
        });
    }

    public RcWorkbenchBlockEntity getWorkbenchBlockEntity() {
        return this.workbenchBe;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.levelAccess, player, ModBlocks.RC_WORKBENCH.get());
    }

    @Override
    public MenuType<?> getType() {
        return ModMenus.RC_WORKBENCH_MENU.get();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            // 1. 完成品スロットからの移動
            if (index == 0) {
                this.levelAccess.execute((level, pos) -> itemstack1.getItem().onCraftedBy(itemstack1, level, player));
                if (!this.moveItemStackTo(itemstack1, 10, 46, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(itemstack1, itemstack);
            }
            // 2. クラフトグリッド (1〜9) からの移動
            else if (index >= 1 && index < 10) {
                if (!this.moveItemStackTo(itemstack1, 10, 46, false)) {
                    return ItemStack.EMPTY;
                }
            }
            // 3. 天板展示スロット (46) からの移動
            else if (index == DISPLAY_SLOT_INDEX) {
                if (!this.moveItemStackTo(itemstack1, 10, 46, false)) {
                    return ItemStack.EMPTY;
                }
            }
            // 4. プレイヤーインベントリ (10〜45) からの移動
            else if (index >= 10 && index < 46) {
                // まず空の天板展示スロットへの移動を試みる
                Slot displaySlot = this.slots.get(DISPLAY_SLOT_INDEX);
                if (displaySlot != null && !displaySlot.hasItem()) {
                    ItemStack singleCopy = itemstack1.split(1);
                    displaySlot.set(singleCopy);
                    slot.setChanged();
                    return itemstack;
                }
                // クラフトグリッドへの移動
                if (!this.moveItemStackTo(itemstack1, 1, 10, false)) {
                    if (index < 37) {
                        if (!this.moveItemStackTo(itemstack1, 37, 46, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(itemstack1, 10, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemstack1.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemstack1);
            if (index == 0) {
                player.drop(itemstack1, false);
            }
        }

        return itemstack;
    }
}
