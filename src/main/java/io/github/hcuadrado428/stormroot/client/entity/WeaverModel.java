package io.github.hcuadrado428.stormroot.client.entity;

import io.github.hcuadrado428.stormroot.ModIds;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modelo del tejedor: cuerpo bajo y ancho, cabeza con un sombrero de seta y seis patas largas que se mueven por
 * parejas al andar. Textura 64×32 ({@code textures/entity/weaver.png}).
 */
//? if >=1.21.2 {
public class WeaverModel extends net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> {
//?} else {
/*public class WeaverModel extends net.minecraft.client.model.HierarchicalModel<io.github.hcuadrado428.stormroot.entity.Weaver> {
*///?}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModIds.id("weaver"), "main");
    public static long frames;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart[] left = new ModelPart[3];
    private final ModelPart[] right = new ModelPart[3];

    public WeaverModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.head = root.getChild("head");
        for (int i = 0; i < 3; i++) {
            left[i] = root.getChild("left_leg_" + i);
            right[i] = root.getChild("right_leg_" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        parts.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -4.0F, -4.0F, 8.0F, 4.0F, 8.0F), PartPose.offset(0.0F, 20.0F, 1.0F));
        PartDefinition head = parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 12)
                .addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 4.0F), PartPose.offset(0.0F, 18.0F, -3.0F));
        head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(24, 12)
                .addBox(-4.0F, -4.0F, -5.0F, 8.0F, 2.0F, 6.0F), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            float z = -2.0F + i * 3.0F;
            float spread = (i - 1) * 0.45F;
            parts.addOrReplaceChild("left_leg_" + i, CubeListBuilder.create().texOffs(32, 0)
                    .addBox(0.0F, -0.5F, -0.5F, 7.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(4.0F, 19.0F, z, 0.0F, spread, 0.55F));
            parts.addOrReplaceChild("right_leg_" + i, CubeListBuilder.create().texOffs(32, 0).mirror()
                    .addBox(-7.0F, -0.5F, -0.5F, 7.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, 19.0F, z, 0.0F, -spread, -0.55F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private void animate(float walkPos, float walkSpeed, float age, float headYaw) {
        frames++;
        for (int i = 0; i < 3; i++) {
            float phase = (i % 2 == 0 ? 0 : Mth.PI);
            float swing = Mth.cos(walkPos * 1.3F + phase) * 0.5F * walkSpeed;
            float lift = Mth.abs(Mth.sin(walkPos * 1.3F + phase)) * 0.4F * walkSpeed;
            left[i].yRot += swing;
            left[i].zRot += lift;
            right[i].yRot -= swing;
            right[i].zRot -= lift;
        }
        this.head.yRot = headYaw * Mth.DEG_TO_RAD;
        this.head.y = 18.0F + Mth.sin(age * 0.1F) * 0.2F;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        super.setupAnim(state);
        animate(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(io.github.hcuadrado428.stormroot.entity.Weaver entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        animate(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw);
    }
    *///?}
}
