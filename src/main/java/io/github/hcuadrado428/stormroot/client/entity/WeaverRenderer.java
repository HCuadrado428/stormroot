package io.github.hcuadrado428.stormroot.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.entity.Weaver;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Dibuja la tejedora con {@link WeaverModel}, encogida a 0,7 como la araña de cueva, y sus ojos que brillan. */
//? if >=1.21.2 {
public class WeaverRenderer extends MobRenderer<Weaver, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, WeaverModel> {
//?} else {
/*public class WeaverRenderer extends MobRenderer<Weaver, WeaverModel> {
*///?}
    private static final Identifier TEXTURE = ModIds.id("textures/entity/weaver.png");
    private static final Identifier GLOW = ModIds.id("textures/entity/weaver_glow.png");
    public static final float SCALE = 0.7F;

    public WeaverRenderer(EntityRendererProvider.Context context) {
        super(context, new WeaverModel(context.bakeLayer(WeaverModel.LAYER)), 0.8F * SCALE);
        this.addLayer(new GlowLayer<>(this, GLOW));
    }

    //? if >=1.21.2 {
    @Override
    protected void scale(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, PoseStack pose) {
        pose.scale(SCALE, SCALE, SCALE);
    }

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
    protected void scale(Weaver entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(Weaver entity) {
        return TEXTURE;
    }
    *///?}
}
