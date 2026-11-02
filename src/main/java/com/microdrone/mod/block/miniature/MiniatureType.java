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
            Shapes.box(0.4, 0.0, 0.4, 0.6, 0.25, 0.6) // スロット中心基準の当たり判定
    ));

    private final String id;
    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private final float defaultScale;
    private final VoxelShape baseCollisionShape;

    public MiniatureType(String id, ResourceLocation modelLocation, ResourceLocation textureLocation, float defaultScale, VoxelShape baseCollisionShape) {
        this.id = id;
        this.modelLocation = modelLocation;
        this.textureLocation = textureLocation;
        this.defaultScale = defaultScale;
        this.baseCollisionShape = baseCollisionShape;
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
}

