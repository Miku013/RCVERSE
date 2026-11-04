package com.microdrone.mod.block.miniature;

import com.microdrone.mod.MicroDroneMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * コース小物（ミニチュア）の定義情報
 */
public class MiniatureType {
    private static final Map<String, MiniatureType> REGISTRY = new HashMap<>();

    public static final MiniatureType TRAFFIC_CONE = register(new MiniatureType(
            "traffic_cone",
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/traffic_cone.geo.json"),
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/traffic_cone.png"),
            MiniaturePlacementData.DEFAULT_CONE_SCALE, // 1/2サイズ (0.165f: 幅約2.6px、高さ約3.5px)
            Shapes.box(0.4, 0.0, 0.4, 0.6, 0.25, 0.6), // スロット中心基準の当たり判定
            false,
            0.0f
    ));

    public static final MiniatureType RACING_CURB = register(new MiniatureType(
            "racing_curb",
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/racing_curb.geo.json"),
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/racing_curb.png"),
            0.30f, // 斜め配置時(対角4.5px)でも隙間なく美しく重なり合うスケール (約4.8px)
            Shapes.box(0.35, 0.0, 0.35, 0.65, 0.06, 0.65), // サスペンションが乗り上げられる薄いスロープ判定
            true, // 直線・カーブ自動角度追従
            0.0f // 進行方向に沿って赤白ストライプが交互に並ぶ基準角
    ));

    public static final MiniatureType CURB_STONE = register(new MiniatureType(
            "curb_stone",
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/curb_stone.geo.json"),
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/curb.png"),
            0.22f, // 縦型縁石ブロック
            Shapes.box(0.35, 0.0, 0.35, 0.65, 0.15, 0.65),
            true, // 進行方向に合わせて自動接続
            0.0f
    ));

    public static final MiniatureType GUARDRAIL = register(new MiniatureType(
            "guardrail",
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/guardrail.geo.json"),
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/guardrail.png"),
            0.24f, // 1マス幅でレール同士が隙間なく連結するスケール
            Shapes.box(0.35, 0.0, 0.35, 0.65, 0.28, 0.65), // 車体がぶつかる壁判定
            true, // レール方向に自動追従
            0.0f
    ));

    public static final MiniatureType TIRE_BARRIER = register(new MiniatureType(
            "tire_barrier",
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "geo/tire_barrier.geo.json"),
            ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "textures/entity/tire.png"),
            0.20f, // 1マスに収まるタイヤ積み重ね
            Shapes.box(0.35, 0.0, 0.35, 0.65, 0.20, 0.65),
            false,
            0.0f
    ));

    private final String id;
    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private final float defaultScale;
    private final VoxelShape baseCollisionShape;
    private final boolean autoAlign;
    private final float yawOffset;

    public MiniatureType(String id, ResourceLocation modelLocation, ResourceLocation textureLocation, float defaultScale, VoxelShape baseCollisionShape) {
        this(id, modelLocation, textureLocation, defaultScale, baseCollisionShape, false, 0.0f);
    }

    public MiniatureType(String id, ResourceLocation modelLocation, ResourceLocation textureLocation, float defaultScale, VoxelShape baseCollisionShape, boolean autoAlign, float yawOffset) {
        this.id = id;
        this.modelLocation = modelLocation;
        this.textureLocation = textureLocation;
        this.defaultScale = defaultScale;
        this.baseCollisionShape = baseCollisionShape;
        this.autoAlign = autoAlign;
        this.yawOffset = yawOffset;
    }

    public static MiniatureType register(MiniatureType type) {
        REGISTRY.put(type.getId(), type);
        return type;
    }

    @Nullable
    public static MiniatureType getById(String id) {
        return REGISTRY.get(id);
    }

    public String getId() {
        return id;
    }

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    public float getDefaultScale() {
        return defaultScale;
    }

    public VoxelShape getBaseCollisionShape() {
        return baseCollisionShape;
    }

    public boolean isAutoAlign() {
        return autoAlign;
    }

    public float getYawOffset() {
        return yawOffset;
    }
}

