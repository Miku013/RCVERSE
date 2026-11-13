package com.microdrone.mod.decal;

import net.minecraft.nbt.CompoundTag;

/**
 * RCカーの車体に貼り付けるステッカー（デカール）の配置・形状データ
 */
public class CarDecalData {
    private String textureName = "sample.png"; // microdrone/stickers/ 内の画像ファイル名
    private String targetBone = "body";        // 貼り付け対象のボーン名
    private float posX = 0.0f;                 // X座標オフセット (Blockbench unit: 1/16 block)
    private float posY = 20.0f;                // Y座標オフセット
    private float posZ = 0.0f;                 // Z座標オフセット
    private float rotX = 0.0f;                 // X軸回転 (degrees)
    private float rotY = 0.0f;                 // Y軸回転 (degrees)
    private float rotZ = 0.0f;                 // Z軸回転 (degrees)
    private float scale = 6.0f;                // サイズ倍率 (Blockbench unit基準)
    private float aspect = 1.0f;               // 横幅比率 (幅 / 高さ)
    private boolean mirrorX = false;           // 左右反転フラグ

    public CarDecalData() {
    }

    public CarDecalData(String textureName, String targetBone, float posX, float posY, float posZ, float rotX, float rotY, float rotZ, float scale) {
        this(textureName, targetBone, posX, posY, posZ, rotX, rotY, rotZ, scale, 1.0f);
    }

    public CarDecalData(String textureName, String targetBone, float posX, float posY, float posZ, float rotX, float rotY, float rotZ, float scale, float aspect) {
        this.textureName = textureName;
        this.targetBone = targetBone;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.rotX = rotX;
        this.rotY = rotY;
        this.rotZ = rotZ;
        this.scale = scale;
        this.aspect = aspect;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Texture", this.textureName);
        tag.putString("TargetBone", this.targetBone);
        tag.putFloat("PosX", this.posX);
        tag.putFloat("PosY", this.posY);
        tag.putFloat("PosZ", this.posZ);
        tag.putFloat("RotX", this.rotX);
        tag.putFloat("RotY", this.rotY);
        tag.putFloat("RotZ", this.rotZ);
        tag.putFloat("Scale", this.scale);
        tag.putFloat("Aspect", this.aspect);
        tag.putBoolean("MirrorX", this.mirrorX);
        return tag;
    }

    public static CarDecalData fromTag(CompoundTag tag) {
        CarDecalData decal = new CarDecalData();
        if (tag.contains("Texture")) decal.textureName = tag.getString("Texture");
        if (tag.contains("TargetBone")) decal.targetBone = tag.getString("TargetBone");
        if (tag.contains("PosX")) decal.posX = tag.getFloat("PosX");
        if (tag.contains("PosY")) decal.posY = tag.getFloat("PosY");
        if (tag.contains("PosZ")) decal.posZ = tag.getFloat("PosZ");
        if (tag.contains("RotX")) decal.rotX = tag.getFloat("RotX");
        if (tag.contains("RotY")) decal.rotY = tag.getFloat("RotY");
        if (tag.contains("RotZ")) decal.rotZ = tag.getFloat("RotZ");
        if (tag.contains("Scale")) decal.scale = tag.getFloat("Scale");
        if (tag.contains("Aspect")) decal.aspect = tag.getFloat("Aspect");
        if (tag.contains("MirrorX")) decal.mirrorX = tag.getBoolean("MirrorX");
        return decal;
    }

    public CarDecalData copy() {
        CarDecalData copy = new CarDecalData(this.textureName, this.targetBone, this.posX, this.posY, this.posZ, this.rotX, this.rotY, this.rotZ, this.scale, this.aspect);
        copy.mirrorX = this.mirrorX;
        return copy;
    }

    // --- Getters / Setters ---
    public String getTextureName() { return textureName; }
    public void setTextureName(String textureName) { this.textureName = textureName; }

    public String getTargetBone() { return targetBone; }
    public void setTargetBone(String targetBone) { this.targetBone = targetBone; }

    public float getPosX() { return posX; }
    public void setPosX(float posX) { this.posX = posX; }

    public float getPosY() { return posY; }
    public void setPosY(float posY) { this.posY = posY; }

    public float getPosZ() { return posZ; }
    public void setPosZ(float posZ) { this.posZ = posZ; }

    public float getRotX() { return rotX; }
    public void setRotX(float rotX) { this.rotX = rotX; }

    public float getRotY() { return rotY; }
    public void setRotY(float rotY) { this.rotY = rotY; }

    public float getRotZ() { return rotZ; }
    public void setRotZ(float rotZ) { this.rotZ = rotZ; }

    public float getScale() { return scale; }
    public void setScale(float scale) { this.scale = scale; }

    public float getAspect() { return aspect; }
    public void setAspect(float aspect) { this.aspect = aspect; }

    public boolean isMirrorX() { return mirrorX; }
    public void setMirrorX(boolean mirrorX) { this.mirrorX = mirrorX; }

    /**
     * デカールリストを CompoundTag へシリアライズ
     */
    public static CompoundTag serializeDecals(java.util.List<CarDecalData> list) {
        CompoundTag root = new CompoundTag();
        net.minecraft.nbt.ListTag listTag = new net.minecraft.nbt.ListTag();
        if (list != null) {
            for (CarDecalData d : list) {
                if (d != null) listTag.add(d.toTag());
            }
        }
        root.put("List", listTag);
        return root;
    }

    /**
     * CompoundTag からデカールリストを復元
     */
    public static java.util.List<CarDecalData> deserializeDecals(CompoundTag root) {
        java.util.List<CarDecalData> list = new java.util.ArrayList<>();
        if (root != null && root.contains("List", 9)) {
            net.minecraft.nbt.ListTag listTag = root.getList("List", 10);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(CarDecalData.fromTag(listTag.getCompound(i)));
            }
        }
        return list;
    }
}

