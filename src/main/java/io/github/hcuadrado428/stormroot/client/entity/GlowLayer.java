package io.github.hcuadrado428.stormroot.client.entity;

import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.Identifier;

/**
 * Capa que brilla a oscuras, como los ojos de la araña vanilla ({@code SpiderEyesLayer}): la textura se dibuja con
 * luz completa encima del mob. Los píxeles transparentes no se ven.
 */
//? if >=1.21.2 {
public class GlowLayer<S extends net.minecraft.client.renderer.entity.state.EntityRenderState, M extends net.minecraft.client.model.EntityModel<S>> extends EyesLayer<S, M> {
    private final net.minecraft.client.renderer.rendertype.RenderType type;

    public GlowLayer(RenderLayerParent<S, M> parent, Identifier texture) {
        super(parent);
        this.type = net.minecraft.client.renderer.rendertype.RenderTypes.eyes(texture);
    }

    @Override
    public net.minecraft.client.renderer.rendertype.RenderType renderType() {
        return type;
    }
}
//?} else {
/*public class GlowLayer<T extends net.minecraft.world.entity.Entity, M extends net.minecraft.client.model.EntityModel<T>> extends EyesLayer<T, M> {
    private final net.minecraft.client.renderer.RenderType type;

    public GlowLayer(RenderLayerParent<T, M> parent, Identifier texture) {
        super(parent);
        this.type = net.minecraft.client.renderer.RenderType.eyes(texture);
    }

    @Override
    public net.minecraft.client.renderer.RenderType renderType() {
        return type;
    }
}
*///?}
