package com.microdrone.mod.decal;

import java.util.List;

/**
 * ステッカー（デカール）を保持・同期可能なRC乗り物エンティティ共通インターフェース
 */
public interface RcDecalHolder {
    List<CarDecalData> getDecals();
    void setDecals(List<CarDecalData> decals);
    void addDecal(CarDecalData decal);
    void clearDecals();
    String getVariantId();
    void setVariantId(String variantId);
}
