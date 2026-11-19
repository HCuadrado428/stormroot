package io.github.hcuadrado428.stormroot.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.entity.MyceliumGuardian;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Dibuja el guardián de micelio con {@link MyceliumGuardianModel}, a 0,9, con sus vetas y ojos que brillan. */
//? if >=1.21.2 {
public class MyceliumGuardianRenderer extends MobRenderer<MyceliumGuardian, MyceliumGuardianModel.State, MyceliumGuardianModel> {
//?} else {
/*public class MyceliumGuardianRenderer extends MobRenderer<MyceliumGuardian, MyceliumGuardianModel> {
*///?}
    private static final Identifier TEXTURE = ModIds.id("textures/entity/mycelium_guardian.png");
    private static final Identifier GLOW = ModIds.id("textures/entity/mycelium_guardian_glow.png");
    public static final float SCALE = 0.9F;

    public MyceliumGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new MyceliumGuardianModel(context.bakeLayer(MyceliumGuardianModel.LAYER)), 0.7F * SCALE);
        this.addLayer(new GlowLayer<>(this, GLOW));
    }

    //? if >=1.21.2 {
    @Override
    protected void scale(MyceliumGuardianModel.State state, PoseStack pose) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public MyceliumGuardianModel.State createRenderState() {
        return new MyceliumGuardianModel.State();
    }

    @Override
    public void extractRenderState(MyceliumGuardian guardian, MyceliumGuardianModel.State state, float partialTick) {
        super.extractRenderState(guardian, state, partialTick);
        int tick = guardian.getAttackAnimationTick();
        state.attackTicksRemaining = tick > 0 ? tick - partialTick : 0.0F;
    }

    @Override
    public Identifier getTextureLocation(MyceliumGuardianModel.State state) {
        return TEXTURE;
    }
    //?} else {
    /*@Override
    protected void scale(MyceliumGuardian entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(MyceliumGuardian entity) {
        return TEXTURE;
    }
    *///?}
}
