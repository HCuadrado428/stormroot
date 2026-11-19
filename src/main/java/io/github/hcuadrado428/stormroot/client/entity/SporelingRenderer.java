package io.github.hcuadrado428.stormroot.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.entity.Sporeling;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Dibuja el esporín con {@link SporelingModel}. */
//? if >=1.21.2 {
public class SporelingRenderer extends MobRenderer<Sporeling, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, SporelingModel> {
//?} else {
/*public class SporelingRenderer extends MobRenderer<Sporeling, SporelingModel> {
*///?}
    private static final Identifier TEXTURE = ModIds.id("textures/entity/sporeling.png");
    private static final Identifier GLOW = ModIds.id("textures/entity/sporeling_glow.png");
    public static final float SCALE = 0.35F;

    public SporelingRenderer(EntityRendererProvider.Context context) {
        super(context, new SporelingModel(context.bakeLayer(SporelingModel.LAYER)), 0.5F * SCALE);
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
    protected void scale(Sporeling entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(Sporeling entity) {
        return TEXTURE;
    }
    *///?}
}
