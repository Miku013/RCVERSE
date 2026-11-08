package com.microdrone.mod.block.road;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ワールド内の生成済みスプライン道路を追跡・キャッシュし、車両タイヤ物理への超高速判定を提供するマネージャー
 */
public class RoadSplineManager {

    /**
     * キャッシュされた1本の道路データ
     */
    public static class CachedRoad {
        public final BlockPos startPos;
        public final BlockPos endPos;
        public final float roadWidth;
        public final AABB boundingBox;
        public final List<RoadSplineGenerator.SplineSample> samples;

        public CachedRoad(BlockPos startPos, BlockPos endPos, float roadWidth, AABB boundingBox, List<RoadSplineGenerator.SplineSample> samples) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.roadWidth = roadWidth;
            this.boundingBox = boundingBox;
            this.samples = samples;
        }
    }

    // サーバー用キャッシュ (Dimension ResourceKey -> Map)
    private static final Map<String, Map<BlockPos, CachedRoad>> ACTIVE_ROADS = new ConcurrentHashMap<>();

    private static String getLevelKey(Level level) {
        return (level.isClientSide() ? "client_" : "server_") + level.dimension().location().toString();
    }

    /**
     * 道路を登録またはキャッシュ更新
     */
    public static void registerRoad(Level level, RoadStartBlockEntity startBe) {
        if (level == null || startBe == null) return;
        BlockPos endPos = startBe.getLinkedEndPos();
        if (endPos == null) return;

        RoadSplineData data = startBe.getSplineData();
        // プレビュー中または生成済みの道路を登録
        if (!data.isPreview() && !data.isGenerated()) {
            unregisterRoad(level, startBe.getBlockPos());
            return;
        }

        var startFacing = startBe.getBlockState().getValue(RoadStartBlock.FACING);
        var endFacing = net.minecraft.core.Direction.SOUTH;
        if (level.getBlockState(endPos).getBlock() instanceof RoadEndBlock) {
            endFacing = level.getBlockState(endPos).getValue(RoadEndBlock.FACING);
        }

        List<RoadSplineGenerator.SplineSample> samples = RoadSplineGenerator.generateSpline(
                startBe.getBlockPos(), startFacing,
                endPos, endFacing,
                data
        );

        AABB aabb = startBe.getRoadBoundingBox();
        CachedRoad road = new CachedRoad(startBe.getBlockPos(), endPos, data.getRoadWidth(), aabb, samples);

        String key = getLevelKey(level);
        ACTIVE_ROADS.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(startBe.getBlockPos(), road);
    }

    /**
     * 道路の登録解除
     */
    public static void unregisterRoad(Level level, BlockPos startPos) {
        if (level == null || startPos == null) return;
        String key = getLevelKey(level);
        Map<BlockPos, CachedRoad> map = ACTIVE_ROADS.get(key);
        if (map != null) {
            map.remove(startPos);
        }
    }

    /**
     * 車両位置 (x, y, z) の地面サンプリング (全登録道路から判定)
     */
    public static RoadSplineGenerator.RoadHitResult sampleRoad(Level level, double x, double y, double z) {
        if (level == null) return RoadSplineGenerator.RoadHitResult.MISS;
        String key = getLevelKey(level);
        Map<BlockPos, CachedRoad> map = ACTIVE_ROADS.get(key);
        if (map == null || map.isEmpty()) return RoadSplineGenerator.RoadHitResult.MISS;

        RoadSplineGenerator.RoadHitResult bestHit = RoadSplineGenerator.RoadHitResult.MISS;
        double bestDistY = Double.MAX_VALUE;

        for (CachedRoad road : map.values()) {
            // AABB で大まかにフィルタ (計算負荷を大幅削減)
            if (road.boundingBox.inflate(2.0).contains(x, y, z)) {
                RoadSplineGenerator.RoadHitResult hit = RoadSplineGenerator.sampleRoadSurface(road.samples, road.roadWidth, x, y, z);
                if (hit.hit) {
                    double dy = Math.abs(y - hit.groundY);
                    if (dy < bestDistY) {
                        bestDistY = dy;
                        bestHit = hit;
                    }
                }
            }
        }

        return bestHit;
    }
}

