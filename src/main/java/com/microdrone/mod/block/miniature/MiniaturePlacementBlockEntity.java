package com.microdrone.mod.block.miniature;

import com.microdrone.mod.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 1ブロック内に複数の小物を管理・保存・同期するBlockEntity
 */
public class MiniaturePlacementBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<Integer, MiniaturePlacementData> slots = new HashMap<>();

    // 動的当たり判定キャッシュ
    private VoxelShape cachedOutlineShape = null;
    private VoxelShape cachedCollisionShape = null;

    // 視線選択用（RayTrace用）の極薄シート形状 (厚さ 0.05ピクセル: ほぼフラット)
    public static final VoxelShape BASE_PLATE_OUTLINE_SHAPE = Block.box(0, 0, 0, 16, 0.05, 16);

    public MiniaturePlacementBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINIATURE_PLACEMENT_BE.get(), pos, state);
    }

    public boolean hasSlot(int slotId) {
        return slots.containsKey(slotId);
    }

    public MiniaturePlacementData getSlot(int slotId) {
        return slots.get(slotId);
    }

    public Collection<MiniaturePlacementData> getAllSlots() {
        return slots.values();
    }

    public int getSlotCount() {
        return slots.size();
    }

    /**
     * 指定スロットに小物を配置
     */
    public boolean placeMiniature(int slotId, String miniatureId, float yaw) {
        if (slotId < 0 || slotId >= MiniaturePlacementData.TOTAL_SLOTS) return false;
        if (slots.containsKey(slotId)) return false; // すでに配置済み

        MiniatureType type = MiniatureType.getById(miniatureId);
        float scale = type != null ? type.getDefaultScale() : MiniaturePlacementData.DEFAULT_CONE_SCALE;

        MiniaturePlacementData data = new MiniaturePlacementData(slotId, miniatureId, yaw, scale);
        slots.put(slotId, data);
        invalidateShape();
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    /**
     * 指定スロットの小物を撤去
     */
    public MiniaturePlacementData removeMiniature(int slotId) {
        MiniaturePlacementData removed = slots.remove(slotId);
        if (removed != null) {
            invalidateShape();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return removed;
    }

    /**
     * 指定スロットの小物を回転（+45度）
     */
    public boolean rotateMiniature(int slotId, float angleDelta) {
        MiniaturePlacementData data = slots.get(slotId);
        if (data != null) {
            float newYaw = (data.getYaw() + angleDelta) % 360f;
            if (newYaw < 0) newYaw += 360f;
            data.setYaw(newYaw);
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return true;
        }
        return false;
    }

    /**
     * 視線選択用（RayTrace用）の枠形状を取得
     */
    public VoxelShape getOutlineShape() {
        if (cachedOutlineShape == null) {
            VoxelShape shape = BASE_PLATE_OUTLINE_SHAPE;
            for (MiniaturePlacementData data : slots.values()) {
                MiniatureType type = MiniatureType.getById(data.getMiniatureId());
                if (type != null) {
                    float cx = data.getXOffset() * 16.0f; // 0〜16ピクセル単位
                    float cz = data.getZOffset() * 16.0f;
                    // 地面接地(Y=0.0)のコーン当たり判定: 幅約2.8px (半径1.4px)、高さ4px (Y=0.0〜4.0)
                    VoxelShape coneBox = Block.box(
                            Math.max(0, cx - 1.4), 0.0, Math.max(0, cz - 1.4),
                            Math.min(16, cx + 1.4), 4.0, Math.min(16, cz + 1.4)
                    );
                    shape = Shapes.or(shape, coneBox);
                }
            }
            cachedOutlineShape = shape.optimize();
        }
        return cachedOutlineShape;
    }

    /**
     * 物理衝突判定用（RCカー走行用）の形状を取得
     * ベースプレートの物理厚みは完全ゼロ（段差なし）で、コーン部分のみ衝突判定を持つ
     */
    public VoxelShape getCollisionShape() {
        if (cachedCollisionShape == null) {
            VoxelShape shape = Shapes.empty();
            for (MiniaturePlacementData data : slots.values()) {
                MiniatureType type = MiniatureType.getById(data.getMiniatureId());
                if (type != null) {
                    float cx = data.getXOffset() * 16.0f;
                    float cz = data.getZOffset() * 16.0f;
                    // 地面接地(Y=0.0)のコーン物理当たり判定
                    VoxelShape coneBox = Block.box(
                            Math.max(0, cx - 1.4), 0.0, Math.max(0, cz - 1.4),
                            Math.min(16, cx + 1.4), 4.0, Math.min(16, cz + 1.4)
                    );
                    shape = Shapes.or(shape, coneBox);
                }
            }
            cachedCollisionShape = shape.optimize();
        }
        return cachedCollisionShape;
    }

    private void invalidateShape() {
        this.cachedOutlineShape = null;
        this.cachedCollisionShape = null;
    }

    // === NBT 保存 & 同期 ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (MiniaturePlacementData data : slots.values()) {
            list.add(data.save());
        }
        tag.put("Miniatures", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        slots.clear();
        if (tag.contains("Miniatures", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Miniatures", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                MiniaturePlacementData data = MiniaturePlacementData.load(list.getCompound(i));
                slots.put(data.getSlotId(), data);
            }
        }
        invalidateShape();
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

    // === GeckoLib 4 ===

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}

