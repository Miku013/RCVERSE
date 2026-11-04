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

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 1ブロック内に複数の小物を管理・保存・同期するBlockEntity
 */
public class MiniaturePlacementBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(MiniaturePlacementBlockEntity.class);
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

        LOGGER.info("[MiniaturePlacement] Placed {} at pos {} slotId={} yaw={}", miniatureId, worldPosition, slotId, yaw);

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
     * 周囲の同種小物を探索し、中間ピースの接続角度（Yaw）を自動算出
     * 仲間が2つ以上ある中間ピースのみ滑らかなカーブ接線を自動計算。
     * 始まりと終わりの端っこピースは、設置時の向き（プレイヤーの指定角）をそのまま尊重。
     */
    public Float calculateAutoAlignYaw(int slotId, String miniatureId) {
        return calculateAutoAlignYaw(slotId, miniatureId, null);
    }

    public Float calculateAutoAlignYaw(int slotId, String miniatureId, @Nullable Float referenceYaw) {
        int curCol = slotId % MiniaturePlacementData.GRID_SIZE;
        int curRow = slotId / MiniaturePlacementData.GRID_SIZE;
        int myGridX = (worldPosition != null ? worldPosition.getX() : 0) * MiniaturePlacementData.GRID_SIZE + curCol;
        int myGridZ = (worldPosition != null ? worldPosition.getZ() : 0) * MiniaturePlacementData.GRID_SIZE + curRow;

        // 同一ブロックだけでなく、隣接周囲3x3ブロックも含めて同種小物を距離順に収集
        java.util.List<double[]> neighbors = new java.util.ArrayList<>();

        for (int bdx = -1; bdx <= 1; bdx++) {
            for (int bdz = -1; bdz <= 1; bdz++) {
                BlockPos targetPos = (worldPosition != null) ? worldPosition.offset(bdx, 0, bdz) : null;
                MiniaturePlacementBlockEntity targetEntity;
                if (bdx == 0 && bdz == 0) {
                    targetEntity = this;
                } else if (level != null && targetPos != null && level.getBlockEntity(targetPos) instanceof MiniaturePlacementBlockEntity be) {
                    targetEntity = be;
                } else {
                    continue;
                }

                for (MiniaturePlacementData other : targetEntity.getAllSlots()) {
                    if (bdx == 0 && bdz == 0 && other.getSlotId() == slotId) continue;
                    if (!other.getMiniatureId().equals(miniatureId)) continue;

                    int oCol = other.getSlotId() % MiniaturePlacementData.GRID_SIZE;
                    int oRow = other.getSlotId() / MiniaturePlacementData.GRID_SIZE;
                    int targetBaseX = (targetPos != null ? targetPos.getX() : 0) * MiniaturePlacementData.GRID_SIZE;
                    int targetBaseZ = (targetPos != null ? targetPos.getZ() : 0) * MiniaturePlacementData.GRID_SIZE;
                    int otherGridX = targetBaseX + oCol;
                    int otherGridZ = targetBaseZ + oRow;

                    double dx = otherGridX - myGridX;
                    double dz = otherGridZ - myGridZ;
                    double distSq = dx * dx + dz * dz;

                    // 探索範囲: 距離4マス以内 (グリッド単位で4マス = 0.8ブロック)
                    if (distSq <= 16.0) {
                        neighbors.add(new double[]{dx, dz, distSq, other.getSlotId(), other.getYaw()});
                    }
                }
            }
        }

        // 仲間が0個なら完全な単独設置 (プレイヤーの設置角をそのまま使用)
        if (neighbors.isEmpty()) {
            return null;
        }

        // 距離が近い順にソート
        neighbors.sort(java.util.Comparator.comparingDouble(n -> n[2]));

        // 仲間が1個だけの場合 (列の2個目、または端っこピース): 手前の仲間の向きを自動継承して直線を保つ
        if (neighbors.size() == 1) {
            return (float) neighbors.get(0)[4];
        }

        // 自分から見て最も反対側（内積が最も負）にある前後ペア (In, Out) を探索
        double[] bestA = null;
        double[] bestB = null;
        double minDot = 0.0; // 負の値のみ探索 (反対側にあるペア)

        for (int i = 0; i < neighbors.size(); i++) {
            double[] a = neighbors.get(i);
            double distA = Math.sqrt(a[2]);
            if (distA < 0.001) continue;

            for (int j = i + 1; j < neighbors.size(); j++) {
                double[] b = neighbors.get(j);
                double distB = Math.sqrt(b[2]);
                if (distB < 0.001) continue;

                // 正規化内積 (cosθ)
                double cosTheta = (a[0] * b[0] + a[1] * b[1]) / (distA * distB);
                if (cosTheta < minDot) {
                    minDot = cosTheta;
                    bestA = a;
                    bestB = b;
                }
            }
        }

        // 反対側にある仲間ペアが見つからない場合 (片側にしか仲間がいない＝端っこピース):
        // 手動回転指定 (referenceYaw) があればそれを尊重し、新規設置なら一番近い手前の仲間のYawを継承！
        if (bestA == null || bestB == null || minDot > -0.2) {
            return referenceYaw != null ? referenceYaw : (float) neighbors.get(0)[4];
        }

        MiniatureType type = MiniatureType.getById(miniatureId);
        float offset = type != null ? type.getYawOffset() : 0.0f;

        // 手前(A)から自分を通って奥(B)へ抜ける滑らかなカーブ接線ベクトル
        double tangentX = bestB[0] - bestA[0];
        double tangentZ = bestB[1] - bestA[1];

        if (Math.abs(tangentX) < 0.001 && Math.abs(tangentZ) < 0.001) {
            tangentX = bestB[0];
            tangentZ = bestB[1];
        }

        double angleDeg = Math.toDegrees(Math.atan2(tangentZ, tangentX));
        float finalYaw = (float) (angleDeg + offset);

        // 表裏の統一: 基準向き (手前の仲間のYawまたは設置時Yaw) に表裏を揃える
        float baseYaw = referenceYaw != null ? referenceYaw : (float) bestA[4];
        float diff = finalYaw - baseYaw;
        while (diff > 90.0f) { finalYaw -= 180.0f; diff -= 180.0f; }
        while (diff < -90.0f) { finalYaw += 180.0f; diff += 180.0f; }

        return finalYaw;
    }

    /**
     * 新しく小物が置かれた際、近隣（周囲3x3ブロックを含む）の同種小物の角度も再計算して自然な曲線に更新
     */
    private void updateNeighborAutoAlign(int placedSlotId, String miniatureId) {
        int curCol = placedSlotId % MiniaturePlacementData.GRID_SIZE;
        int curRow = placedSlotId / MiniaturePlacementData.GRID_SIZE;
        int myGridX = (worldPosition != null ? worldPosition.getX() : 0) * MiniaturePlacementData.GRID_SIZE + curCol;
        int myGridZ = (worldPosition != null ? worldPosition.getZ() : 0) * MiniaturePlacementData.GRID_SIZE + curRow;

        for (int bdx = -1; bdx <= 1; bdx++) {
            for (int bdz = -1; bdz <= 1; bdz++) {
                BlockPos targetPos = (worldPosition != null) ? worldPosition.offset(bdx, 0, bdz) : null;
                MiniaturePlacementBlockEntity targetEntity;
                if (bdx == 0 && bdz == 0) {
                    targetEntity = this;
                } else if (level != null && targetPos != null && level.getBlockEntity(targetPos) instanceof MiniaturePlacementBlockEntity be) {
                    targetEntity = be;
                } else {
                    continue;
                }

                boolean changed = false;
                for (MiniaturePlacementData other : targetEntity.getAllSlots()) {
                    if (bdx == 0 && bdz == 0 && other.getSlotId() == placedSlotId) continue;
                    if (!other.getMiniatureId().equals(miniatureId)) continue;

                    int oCol = other.getSlotId() % MiniaturePlacementData.GRID_SIZE;
                    int oRow = other.getSlotId() / MiniaturePlacementData.GRID_SIZE;
                    int targetBaseX = (targetPos != null ? targetPos.getX() : 0) * MiniaturePlacementData.GRID_SIZE;
                    int targetBaseZ = (targetPos != null ? targetPos.getZ() : 0) * MiniaturePlacementData.GRID_SIZE;
                    int otherGridX = targetBaseX + oCol;
                    int otherGridZ = targetBaseZ + oRow;

                    double dx = otherGridX - myGridX;
                    double dz = otherGridZ - myGridZ;
                    double distSq = dx * dx + dz * dz;

                    if (distSq <= 16.0) { // 半径4マス以内
                        Float newYaw = targetEntity.calculateAutoAlignYaw(other.getSlotId(), miniatureId, other.getYaw());
                        if (newYaw != null) {
                            other.setYaw(newYaw);
                            changed = true;
                        }
                    }
                }

                if (changed && targetEntity != this) {
                    targetEntity.setChanged();
                    if (level != null && !level.isClientSide && targetPos != null) {
                        level.sendBlockUpdated(targetPos, targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
                    }
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
                    double h = getMiniatureOutlineHeightPixels(type.getId());
                    double halfW = getMiniatureRadiusPixels(type.getId());
                    VoxelShape box = Block.box(
                            Math.max(0, cx - halfW), 0.0, Math.max(0, cz - halfW),
                            Math.min(16, cx + halfW), h, Math.min(16, cz + halfW)
                    );
                    shape = Shapes.or(shape, box);
                }
            }
            cachedOutlineShape = shape.optimize();
        }
        return cachedOutlineShape;
    }

    /**
     * 物理衝突判定用（RCカー走行用）の形状を取得
     * 縁石(racing_curb)は高さ0.6px (約0.038m)。
     * 車体の物理AABBが跳ね上がることなく、4点仮想サスペンションが綺麗に沈み込んで心地よいロールを実現。
     * ガードレールやコーンは車体が飛び出さない壁として機能。
     */
    public VoxelShape getCollisionShape() {
        if (cachedCollisionShape == null) {
            VoxelShape shape = Shapes.empty();
            for (MiniaturePlacementData data : slots.values()) {
                MiniatureType type = MiniatureType.getById(data.getMiniatureId());
                if (type != null) {
                    float cx = data.getXOffset() * 16.0f;
                    float cz = data.getZOffset() * 16.0f;
                    double h = getMiniatureCollisionHeightPixels(type.getId());
                    double halfW = getMiniatureRadiusPixels(type.getId());
                    VoxelShape box = Block.box(
                            Math.max(0, cx - halfW), 0.0, Math.max(0, cz - halfW),
                            Math.min(16, cx + halfW), h, Math.min(16, cz + halfW)
                    );
                    shape = Shapes.or(shape, box);
                }
            }
            cachedCollisionShape = shape.optimize();
        }
        return cachedCollisionShape;
    }

    private double getMiniatureOutlineHeightPixels(String id) {
        if ("goal_gate".equals(id)) return 16.0; // プレイヤーが視線で選択しやすい高さ
        if ("checkpoint_plate".equals(id)) return 1.0; // 薄型プレートの選択枠
        if ("racing_curb".equals(id)) return 1.2; // プレイヤーが視線で選択しやすい高さ
        if ("curb_stone".equals(id)) return 14.0; // 縁石ガード壁
        if ("guardrail".equals(id)) return 16.0;  // ガードレール壁 (1.0ブロック高)
        if ("tire_barrier".equals(id)) return 16.0;// タイヤバリア (1.0ブロック高)
        return 6.0; // traffic_cone
    }

    private double getMiniatureCollisionHeightPixels(String id) {
        if ("goal_gate".equals(id)) return 0.0;   // ゴールゲート下はRCカーがくぐり抜け可能
        if ("checkpoint_plate".equals(id)) return 0.0; // チェックポイントプレート上は完全すり抜け
        if ("racing_curb".equals(id)) return 0.3; // レーシング縁石は極薄0.3px (約0.019m): 物理的な引っ掛かりをゼロにしつつサスが美しく沈む
        if ("curb_stone".equals(id)) return 14.0; // 縁石ガード壁 (maxUpStep 9.6pxを確実に超えて激突停止)
        if ("guardrail".equals(id)) return 16.0;  // ガードレール壁 (1.0ブロック高の鉄壁として激突停止)
        if ("tire_barrier".equals(id)) return 16.0;// タイヤバリア壁 (1.0ブロック高で激突停止)
        return 6.0; // traffic_cone
    }

    private double getMiniatureRadiusPixels(String id) {
        if ("goal_gate".equals(id)) return 1.6;   // ゴールゲート幅 (3.2px)
        if ("checkpoint_plate".equals(id)) return 1.6; // プレート幅 (3.2px)
        if ("racing_curb".equals(id)) return 1.6; // 縁石の幅 (3.2px)
        if ("guardrail".equals(id)) return 1.6;   // ガードレール幅 (3.2px)
        if ("curb_stone".equals(id)) return 1.6;  // カーブガード幅 (3.2px)
        if ("tire_barrier".equals(id)) return 1.6;// タイヤバリア幅 (3.2px)
        return 1.4; // コーン
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

