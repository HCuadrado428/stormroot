package io.github.hcuadrado428.stormroot.client.entity;

import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.entity.Weaver;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Dibuja el tejedor con {@link WeaverModel}. */
//? if >=1.21.2 {
public class WeaverRenderer extends MobRenderer<Weaver, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, WeaverModel> {
//?} else {
/*public class WeaverRenderer extends MobRenderer<Weaver, WeaverModel> {
*///?}
    private static final Identifier TEXTURE = ModIds.id("textures/entity/weaver.png");

    public WeaverRenderer(EntityRendererProvider.Context context) {
        super(context, new WeaverModel(context.bakeLayer(WeaverModel.LAYER)), 0.5F);
    }

    //? if >=1.21.2 {
    @Override
    public net.minecraft.client.renderer.entity.state.LivingEntityRenderState createRenderState() {
        return new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        return TEXTURE;
    }
    //?} else {
    /*@Override
    public Identifier getTextureLocation(Weaver entity) {
        return TEXTURE;
    }
    *///?}
}
