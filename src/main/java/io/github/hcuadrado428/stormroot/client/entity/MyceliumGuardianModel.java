package io.github.hcuadrado428.stormroot.client.entity;

import io.github.hcuadrado428.stormroot.ModIds;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modelo del guardián de micelio: el cuerpo del gólem de hierro ({@code IronGolemModel}: mismas piezas, pivotes y
 * forma de andar y de golpear) hecho de corteza, con un sombrero de seta enorme y setas en los hombros. El renderer lo
 * encoge a 0,9. Textura 128×128 ({@code textures/entity/mycelium_guardian.png}).
 */
//? if >=1.21.2 {
public class MyceliumGuardianModel extends net.minecraft.client.model.EntityModel<MyceliumGuardianModel.State> {
    // Estado de render con lo que queda del golpe (como IronGolemRenderState.attackTicksRemaining).
    public static class State extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState {
        public float attackTicksRemaining;
    }
//?} else {
/*public class MyceliumGuardianModel extends net.minecraft.client.model.HierarchicalModel<io.github.hcuadrado428.stormroot.entity.MyceliumGuardian> {
*///?}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModIds.id("mycelium_guardian"), "main");
    public static long frames;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MyceliumGuardianModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    private static void mushroom(PartDefinition parent, String name, boolean big, float x, float y, float z, float tiltX, float tiltZ) {
        CubeListBuilder cubes = big
                ? CubeListBuilder.create().texOffs(108, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                        .texOffs(84, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 2.0F, 6.0F)
                : CubeListBuilder.create().texOffs(100, 10).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                        .texOffs(84, 10).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 1.0F, 4.0F);
        parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(x, y, z, tiltX, 0.0F, tiltZ));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition head = parts.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -12.0F, -5.5F, 8.0F, 10.0F, 8.0F)
                .texOffs(24, 0).addBox(-1.0F, -5.0F, -7.5F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -7.0F, -2.0F));
        head.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 90).addBox(-8.0F, -14.0F, -9.5F, 16.0F, 2.0F, 16.0F)
                .texOffs(64, 96).addBox(-5.0F, -18.0F, -6.5F, 10.0F, 4.0F, 10.0F), PartPose.ZERO);
        PartDefinition body = parts.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-9.0F, -2.0F, -6.0F, 18.0F, 12.0F, 11.0F)
                .texOffs(0, 70).addBox(-4.5F, 10.0F, -3.0F, 9.0F, 5.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, -7.0F, 0.0F));
        mushroom(body, "mushroom_shoulder", true, 6.0F, -2.0F, -0.5F, 0.05F, 0.2F);
        mushroom(body, "mushroom_shoulder_small", false, -6.5F, -2.0F, 1.0F, -0.1F, -0.25F);
        mushroom(body, "mushroom_back", false, 2.5F, -2.0F, 3.5F, 0.3F, 0.1F);
        parts.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(60, 21)
                .addBox(-13.0F, -2.5F, -3.0F, 4.0F, 30.0F, 6.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
        parts.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 58)
                .addBox(9.0F, -2.5F, -3.0F, 4.0F, 30.0F, 6.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
        parts.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(37, 0)
                .addBox(-3.5F, -3.0F, -3.0F, 6.0F, 16.0F, 5.0F), PartPose.offset(-4.0F, 11.0F, 0.0F));
        parts.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 0).mirror()
                .addBox(-3.5F, -3.0F, -3.0F, 6.0F, 16.0F, 5.0F), PartPose.offset(5.0F, 11.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Onda triangular entre -1 y 1 (la misma que usa el gólem de hierro). */
    private static float triangle(float x, float period) {
        return (Math.abs(x % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    /** {@code attack}: ticks que le quedan al golpe (0 si no golpea). */
    private void animate(float f, float g, float age, float headYaw, float headPitch, float attack) {
        frames++;
        this.head.yRot = headYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.rightLeg.xRot = -1.5F * triangle(f, 13.0F) * g;
        this.leftLeg.xRot = 1.5F * triangle(f, 13.0F) * g;
        this.rightLeg.yRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        if (attack > 0.0F) {
            // Los dos brazos arriba de golpe, como el gólem.
            this.rightArm.xRot = -2.0F + 1.5F * triangle(attack, 10.0F);
            this.leftArm.xRot = -2.0F + 1.5F * triangle(attack, 10.0F);
        } else {
            this.rightArm.xRot = (-0.2F + 1.5F * triangle(f, 13.0F)) * g;
            this.leftArm.xRot = (-0.2F - 1.5F * triangle(f, 13.0F)) * g;
        }
        // Quieto, los brazos se mecen un poco, como raíces.
        this.rightArm.zRot = Mth.sin(age * 0.05F) * 0.03F;
        this.leftArm.zRot = -Mth.sin(age * 0.05F) * 0.03F;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(State state) {
        super.setupAnim(state);
        animate(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot, state.attackTicksRemaining);
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
        float partialTick = ageInTicks - entity.tickCount;
        int tick = entity.getAttackAnimationTick();
        animate(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, tick > 0 ? tick - partialTick : 0.0F);
    }
    *///?}
}
