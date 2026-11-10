package com.microdrone.mod.block.entity;

import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * コース中継カメラ (Trackside Camera) BlockEntity
 * - サーキットや峠道のコーナー・ジャンプ台に設置できる中継カメラ
 * - 周囲のラジコンを自動検知・追尾し、CCTVレンダーパイプラインへカメラ視点を提供する
 */
public class TracksideCameraBlockEntity extends BlockEntity {

    public static final Set<TracksideCameraBlockEntity> ACTIVE_CAMERAS = Collections.newSetFromMap(new WeakHashMap<>());
    public static final double DETECTION_RADIUS = 56.0;

    private String cameraName = "";
    private float currentPanYaw = 0.0f;
    private float currentTiltPitch = 15.0f;
    private int nearestCarId = -1;
    private int tickCount = 0;

    public TracksideCameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRACKSIDE_CAMERA_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ACTIVE_CAMERAS.add(this);
    }

    @Override
    public void setRemoved() {
        ACTIVE_CAMERAS.remove(this);
        super.setRemoved();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickCount++;
        if (tickCount % 4 == 0) {
            updateTracking(level, pos);
        }
    }

    private void updateTracking(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(DETECTION_RADIUS);
        List<RcCarEntity> cars = level.getEntitiesOfClass(RcCarEntity.class, area);

        RcCarEntity bestCar = null;
        double bestDistSq = Double.MAX_VALUE;
        Vec3 camPos = Vec3.atCenterOf(pos).add(0, 0.45, 0);

        for (RcCarEntity car : cars) {
            double dSq = car.distanceToSqr(camPos);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestCar = car;
            }
        }

        if (bestCar != null) {
            this.nearestCarId = bestCar.getId();
            Vec3 target = bestCar.position().add(0, 0.2, 0);
            Vec3 diff = target.subtract(camPos);

            double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            float targetYaw = (float) (Mth.atan2(-diff.x, diff.z) * Mth.RAD_TO_DEG);
            float targetPitch = (float) (Mth.atan2(-diff.y, horizDist) * Mth.RAD_TO_DEG);

            this.currentPanYaw += Mth.wrapDegrees(targetYaw - this.currentPanYaw) * 0.45f;
            this.currentTiltPitch += (targetPitch - this.currentTiltPitch) * 0.45f;
        } else {
            this.nearestCarId = -1;
        }
    }

    public String getCameraName() {
        if (cameraName == null || cameraName.isEmpty()) {
            return String.format("CAM-%02d", (Math.abs(worldPosition.hashCode()) % 99) + 1);
        }
        return cameraName;
    }

    public void setCameraName(String name) {
        this.cameraName = name != null ? name.trim() : "";
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public float getPanYaw() {
        return currentPanYaw;
    }

    public float getTiltPitch() {
        return currentTiltPitch;
    }

    public int getNearestCarId() {
        return nearestCarId;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("CameraName", cameraName);
        tag.putFloat("PanYaw", currentPanYaw);
        tag.putFloat("TiltPitch", currentTiltPitch);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.cameraName = tag.getString("CameraName");
        this.currentPanYaw = tag.getFloat("PanYaw");
        this.currentTiltPitch = tag.getFloat("TiltPitch");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

