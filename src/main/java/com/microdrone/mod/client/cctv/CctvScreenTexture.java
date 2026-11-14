package com.microdrone.mod.client.cctv;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * CCTV オフスクリーン FBO の OpenGL テクスチャを
 * Minecraft のテクスチャ管理システム (ResourceLocation) に繋ぐアダプターテクスチャ
 */
public class CctvScreenTexture extends AbstractTexture {

    @Override
    public int getId() {
        int texId = CctvRenderPipeline.getTextureId();
        this.id = texId;
        return texId;
    }

    @Override
    public void bind() {
        int texId = getId();
        if (texId <= 0) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> GlStateManager._bindTexture(texId));
        } else {
            GlStateManager._bindTexture(texId);
        }
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // FBO経由で動的に生成されるため、リソースファイルからの読み込みは不要
    }

    @Override
    public void close() {
        // FBO側の破棄ライフサイクルで管理するため、ここでは何もしない
    }
}
