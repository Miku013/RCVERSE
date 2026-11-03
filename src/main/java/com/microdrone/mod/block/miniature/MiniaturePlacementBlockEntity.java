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

        // autoAlign 有効時は周囲の仲間の角度も再計算して滑らかに連結
        if (type != null && type.isAutoAlign()) {
            updateNeighborAutoAlign(slotId, miniatureId);
        }

        invalidateShape();
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    /**
     * 周囲の同種小物を探索し、接続角度（Yaw）を自動算出
     */
    public Float calculateAutoAlignYaw(int slotId, String miniatureId) {
        int curCol = slotId % MiniaturePlacementData.GRID_SIZE;
        int curRow = slotId / MiniaturePlacementData.GRID_SIZE;

        // 同じブロック内の同種小物を距離順に収集
        java.util.List<double[]> neighbors = new java.util.ArrayList<>();
        for (MiniaturePlacementData other : slots.values()) {
            if (other.getSlotId() == slotId) continue;
            if (!other.getMiniatureId().equals(miniatureId)) continue;

            int oCol = other.getSlotId() % MiniaturePlacementData.GRID_SIZE;
            int oRow = other.getSlotId() / MiniaturePlacementData.GRID_SIZE;
            double dx = oCol - curCol;
            double dz = oRow - curRow;
            double distSq = dx * dx + dz * dz;

            // 探索範囲: 距離4マス以内 (1マス間隔や桂馬飛びも余裕でカバー)
            if (distSq <= 16.0) {
                neighbors.add(new double[]{dx, dz, distSq});
            }
        }

        if (neighbors.isEmpty()) {
            return null;
        }

        // 距離が近い順にソート
        neighbors.sort(java.util.Comparator.comparingDouble(a -> a[2]));

        MiniatureType type = MiniatureType.getById(miniatureId);
        float offset = type != null ? type.getYawOffset() : 0.0f;

        float finalYaw;
        if (neighbors.size() == 1) {
            // 仲間が1つの場合: その仲間との接続方向へ長さを揃える
            double dx = neighbors.get(0)[0];
            double dz = neighbors.get(0)[1];
            double angleDeg = Math.toDegrees(Math.atan2(dz, dx));
            finalYaw = (float) (angleDeg + offset);
        } else {
            // 仲間が2つ以上の場合: 前後の2点からカーブ接線（平均進行ベクトル）を算出
            double dx1 = neighbors.get(0)[0];
            double dz1 = neighbors.get(0)[1];
            double dx2 = neighbors.get(1)[0];
            double dz2 = neighbors.get(1)[1];

            // 2つの仲間を結ぶ線分（進行方向接線）
            double tangentX = dx2 - dx1;
            double tangentZ = dz2 - dz1;

            // ほぼ同一直線上の逆向きでない場合のフォールバック
            if (Math.abs(tangentX) < 0.001 && Math.abs(tangentZ) < 0.001) {
                tangentX = dx1;
                tangentZ = dz1;
            }

            double angleDeg = Math.toDegrees(Math.atan2(tangentZ, tangentX));
            finalYaw = (float) (angleDeg + offset);
        }
        return finalYaw;
    }

    /**
     * 新しく小物が置かれた際、近隣の同種小物の角度も再計算して自然な曲線に更新
     */
    private void updateNeighborAutoAlign(int placedSlotId, String miniatureId) {
        int curCol = placedSlotId % MiniaturePlacementData.GRID_SIZE;
        int curRow = placedSlotId / MiniaturePlacementData.GRID_SIZE;

        for (MiniaturePlacementData other : slots.values()) {
            if (other.getSlotId() == placedSlotId) continue;
            if (!other.getMiniatureId().equals(miniatureId)) continue;

            int oCol = other.getSlotId() % MiniaturePlacementData.GRID_SIZE;
            int oRow = other.getSlotId() / MiniaturePlacementData.GRID_SIZE;
            double distSq = (oCol - curCol) * (oCol - curCol) + (oRow - curRow) * (oRow - curRow);

            if (distSq <= 9.0) { // 半径3マス以内
                Float newYaw = calculateAutoAlignYaw(other.getSlotId(), miniatureId);
                if (newYaw != null) {
                    other.setYaw(newYaw);
                }
            }
        }
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

