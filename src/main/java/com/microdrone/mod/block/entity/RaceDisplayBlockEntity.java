package com.microdrone.mod.block.entity;

import com.microdrone.mod.block.RaceDisplayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RaceDisplayBlockEntity extends BlockEntity {
    private static final java.util.Set<RaceDisplayBlockEntity> ACTIVE_DISPLAYS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    // === マルチブロック構造データ ===
    private boolean isMaster = true;
    private BlockPos masterPos = null;
    private int multiblockWidth = 1;
    private int multiblockHeight = 1;

    // === 表示するレースデータ ===
    private String pilotName = "---";
    private int currentLap = 0;
    private long lastLapTimeMs = 0;
    private long bestLapTimeMs = 0;
    private long splitTimeMs = 0;
    private int splitCheckpoint = 0;
    private boolean isRacing = false;
    private int updateFlashTicks = 0;

    // === CCTVライブ中継モード ===
    public enum DisplayMode {
        CCTV_LIVE,
        LEADERBOARD
    }
    private DisplayMode displayMode = DisplayMode.CCTV_LIVE;

    public RaceDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RACE_DISPLAY_BE.get(), pos, state);
        this.masterPos = pos;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ACTIVE_DISPLAYS.add(this);
    }

    @Override
    public void setRemoved() {
        ACTIVE_DISPLAYS.remove(this);
        super.setRemoved();
    }

    public static void broadcastRaceUpdate(Level level, net.minecraft.world.phys.Vec3 carPos, double maxDist,
                                           String pilot, int lap, long lastTime, long bestTime, long splitTime, int cp, boolean racing) {
        if (level == null || level.isClientSide) return;
        double maxDistSq = maxDist * maxDist;

        for (RaceDisplayBlockEntity display : ACTIVE_DISPLAYS) {
            if (display.level == level && !display.isRemoved() && display.isMaster()) {
                double distSq = display.getBlockPos().distToCenterSqr(carPos);
                if (distSq <= maxDistSq) {
                    display.updateRaceData(pilot, lap, lastTime, bestTime, splitTime, cp, racing);
                }
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (this.updateFlashTicks > 0) {
            this.updateFlashTicks--;
        }
    }

    public boolean isMaster() {
        return this.isMaster;
    }

    public BlockPos getMasterPos() {
        return this.masterPos != null ? this.masterPos : this.worldPosition;
    }

    public int getMultiblockWidth() {
        return this.multiblockWidth;
    }

    public int getMultiblockHeight() {
        return this.multiblockHeight;
    }

    public String getPilotName() {
        return this.pilotName;
    }

    public int getCurrentLap() {
        return this.currentLap;
    }

    public long getLastLapTimeMs() {
        return this.lastLapTimeMs;
    }

    public long getBestLapTimeMs() {
        return this.bestLapTimeMs;
    }

    public long getSplitTimeMs() {
        return this.splitTimeMs;
    }

    public int getSplitCheckpoint() {
        return this.splitCheckpoint;
    }

    public boolean isRacing() {
        return this.isRacing;
    }

    public boolean isFlashing() {
        return this.updateFlashTicks > 0;
    }

    public DisplayMode getDisplayMode() {
        return this.displayMode;
    }

    public void setDisplayMode(DisplayMode mode) {
        this.displayMode = mode;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void toggleDisplayMode() {
        setDisplayMode(this.displayMode == DisplayMode.CCTV_LIVE ? DisplayMode.LEADERBOARD : DisplayMode.CCTV_LIVE);
    }

    /**
     * 車両からレースデータを受信して更新
     */
    public void updateRaceData(String pilot, int lap, long lastTime, long bestTime, long splitTime, int cp, boolean racing) {
        if (!this.isMaster && this.masterPos != null && this.level != null) {
            BlockEntity be = this.level.getBlockEntity(this.masterPos);
            if (be instanceof RaceDisplayBlockEntity masterBe) {
                masterBe.updateRaceData(pilot, lap, lastTime, bestTime, splitTime, cp, racing);
                return;
            }
        }

        this.pilotName = pilot != null ? pilot : "---";
        this.currentLap = lap;
        this.lastLapTimeMs = lastTime;
        this.bestLapTimeMs = bestTime;
        this.splitTimeMs = splitTime;
        this.splitCheckpoint = cp;
        this.isRacing = racing;
        this.updateFlashTicks = 15; // 0.75秒ピカッと強調演出

        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // === マルチブロック再計算ロジック ===
    public static void recalculateNearbyMultiblocks(Level level, BlockPos origin, Direction facing) {
        Direction leftDir = facing.getClockWise();
        Direction rightDir = facing.getCounterClockWise();

        // 原点周辺の探索範囲 (-10 ～ +10)
        List<BlockPos> scanned = new ArrayList<>();
        for (int y = -8; y <= 8; y++) {
            for (int h = -8; h <= 8; h++) {
                BlockPos p = origin.above(y).relative(rightDir, h);
                BlockState st = level.getBlockState(p);
                if (st.is(com.microdrone.mod.block.ModBlocks.RACE_DISPLAY.get()) && st.getValue(RaceDisplayBlock.FACING) == facing) {
                    scanned.add(p);
                }
            }
        }

        // 各連結クラスタを検出
        for (BlockPos p : scanned) {
            // 一番左下 (leftDir方向、DOWN方向へ行ける限り進む)
            BlockPos bl = p;
            while (true) {
                BlockPos nextLeft = bl.relative(leftDir);
                if (isSameDisplay(level, nextLeft, facing)) {
                    bl = nextLeft;
                } else {
                    break;
                }
            }
            while (true) {
                BlockPos nextDown = bl.below();
                if (isSameDisplay(level, nextDown, facing)) {
                    bl = nextDown;
                } else {
                    break;
                }
            }

            // bl が左下角の候補
            // そこから幅 (rightDir) を測る
            int w = 0;
            while (isSameDisplay(level, bl.relative(rightDir, w), facing) && w < 16) {
                w++;
            }

            // 高さ (UP) を測る（幅 w がすべて揃っている行のみ）
            int h = 0;
            boolean fullRow = true;
            while (fullRow && h < 16) {
                for (int x = 0; x < w; x++) {
                    if (!isSameDisplay(level, bl.above(h).relative(rightDir, x), facing)) {
                        fullRow = false;
                        break;
                    }
                }
                if (fullRow) {
                    h++;
                }
            }

            if (w <= 0 || h <= 0) continue;

            // bl をマスターとして、矩形内の全ブロックに登録
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    BlockPos targetPos = bl.above(dy).relative(rightDir, dx);
                    BlockEntity be = level.getBlockEntity(targetPos);
                    if (be instanceof RaceDisplayBlockEntity rbe) {
                        boolean master = (dx == 0 && dy == 0);
                        rbe.isMaster = master;
                        rbe.masterPos = bl;
                        rbe.multiblockWidth = w;
                        rbe.multiblockHeight = h;
                        rbe.setChanged();
                        level.sendBlockUpdated(targetPos, rbe.getBlockState(), rbe.getBlockState(), 3);
                    }
                }
            }
        }
    }

    private static boolean isSameDisplay(Level level, BlockPos pos, Direction facing) {
        BlockState st = level.getBlockState(pos);
        return st.is(com.microdrone.mod.block.ModBlocks.RACE_DISPLAY.get()) && st.getValue(RaceDisplayBlock.FACING) == facing;
    }

    // === NBT 保存 & 同期 ===
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsMaster", this.isMaster);
        if (this.masterPos != null) {
            tag.putLong("MasterPos", this.masterPos.asLong());
        }
        tag.putInt("Width", this.multiblockWidth);
        tag.putInt("Height", this.multiblockHeight);

        tag.putString("PilotName", this.pilotName);
        tag.putInt("CurrentLap", this.currentLap);
        tag.putLong("LastLapTime", this.lastLapTimeMs);
        tag.putLong("BestLapTime", this.bestLapTimeMs);
        tag.putLong("SplitTime", this.splitTimeMs);
        tag.putInt("SplitCheckpoint", this.splitCheckpoint);
        tag.putBoolean("IsRacing", this.isRacing);
        tag.putString("DisplayMode", this.displayMode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isMaster = tag.getBoolean("IsMaster");
        if (tag.contains("MasterPos")) {
            this.masterPos = BlockPos.of(tag.getLong("MasterPos"));
        } else {
            this.masterPos = this.worldPosition;
        }
        this.multiblockWidth = Math.max(1, tag.getInt("Width"));
        this.multiblockHeight = Math.max(1, tag.getInt("Height"));

        this.pilotName = tag.getString("PilotName");
        this.currentLap = tag.getInt("CurrentLap");
        this.lastLapTimeMs = tag.getLong("LastLapTime");
        this.bestLapTimeMs = tag.getLong("BestLapTime");
        this.splitTimeMs = tag.getLong("SplitTime");
        this.splitCheckpoint = tag.getInt("SplitCheckpoint");
        this.isRacing = tag.getBoolean("IsRacing");
        if (tag.contains("DisplayMode")) {
            try {
                this.displayMode = DisplayMode.valueOf(tag.getString("DisplayMode"));
            } catch (Exception ignored) {}
        }
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
