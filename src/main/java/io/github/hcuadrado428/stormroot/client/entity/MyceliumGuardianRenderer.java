package io.github.hcuadrado428.stormroot.client.entity;

import io.github.hcuadrado428.stormroot.ModIds;
import io.github.hcuadrado428.stormroot.entity.MyceliumGuardian;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Dibuja el guardián de micelio con {@link MyceliumGuardianModel}. */
//? if >=1.21.2 {
public class MyceliumGuardianRenderer extends MobRenderer<MyceliumGuardian, net.minecraft.client.renderer.entity.state.LivingEntityRenderState, MyceliumGuardianModel> {
//?} else {
/*public class MyceliumGuardianRenderer extends MobRenderer<MyceliumGuardian, MyceliumGuardianModel> {
*///?}
    private static final Identifier TEXTURE = ModIds.id("textures/entity/mycelium_guardian.png");

    public MyceliumGuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new MyceliumGuardianModel(context.bakeLayer(MyceliumGuardianModel.LAYER)), 1.0F);
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
    public Identifier getTextureLocation(MyceliumGuardian entity) {
        return TEXTURE;
    }
    *///?}
}
