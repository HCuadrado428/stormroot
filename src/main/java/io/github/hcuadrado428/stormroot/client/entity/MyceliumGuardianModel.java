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
 * Modelo del guardián de micelio: un gigante de raíces (2,25 bloques) con un gran sombrero de seta, brazos largos y
 * piernas cortas y gruesas. Textura 128×64 ({@code textures/entity/mycelium_guardian.png}).
 */
//? if >=1.21.2 {
public class MyceliumGuardianModel extends net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> {
//?} else {
/*public class MyceliumGuardianModel extends net.minecraft.client.model.HierarchicalModel<io.github.hcuadrado428.stormroot.entity.MyceliumGuardian> {
*///?}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModIds.id("mycelium_guardian"), "main");
    public static long frames;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public MyceliumGuardianModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.head = root.getChild("head");
        this.leftArm = root.getChild("left_arm");
        this.rightArm = root.getChild("right_arm");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        parts.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7.0F, -14.0F, -5.0F, 14.0F, 14.0F, 10.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
        PartDefinition head = parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-5.0F, -8.0F, -4.0F, 10.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-8.0F, -12.0F, -8.0F, 16.0F, 4.0F, 16.0F), PartPose.ZERO);
        parts.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(84, 0)
                .addBox(-2.5F, -2.0F, -2.5F, 5.0F, 16.0F, 5.0F), PartPose.offset(9.5F, 2.0F, 0.0F));
        parts.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(84, 0).mirror()
                .addBox(-2.5F, -2.0F, -2.5F, 5.0F, 16.0F, 5.0F), PartPose.offset(-9.5F, 2.0F, 0.0F));
        parts.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(104, 0)
                .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 10.0F, 5.0F), PartPose.offset(3.5F, 14.0F, 0.0F));
        parts.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(104, 0).mirror()
                .addBox(-2.5F, 0.0F, -2.5F, 5.0F, 10.0F, 5.0F), PartPose.offset(-3.5F, 14.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    private void animate(float walkPos, float walkSpeed, float age, float headYaw, float headPitch) {
        frames++;
        float swing = Mth.cos(walkPos * 0.6F) * 1.0F * walkSpeed;
        this.leftLeg.xRot = swing;
        this.rightLeg.xRot = -swing;
        this.leftArm.xRot = -swing * 0.8F;
        this.rightArm.xRot = swing * 0.8F;
        // Brazos que se balancean un poco aunque esté quieto (como raíces).
        this.leftArm.zRot = -0.08F - Mth.sin(age * 0.05F) * 0.04F;
        this.rightArm.zRot = 0.08F + Mth.sin(age * 0.05F) * 0.04F;
        this.head.yRot = headYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        super.setupAnim(state);
        animate(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(io.github.hcuadrado428.stormroot.entity.MyceliumGuardian entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        animate(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
    *///?}
}
