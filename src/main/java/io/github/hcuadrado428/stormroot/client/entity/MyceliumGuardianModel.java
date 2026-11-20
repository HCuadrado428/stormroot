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
 * Modelo del guardián de micelio: una seta gigante que anda. Un pie con cara y un anillo, un sombrero de tres pisos,
 * brazos de raíz con dedos (hijos del cuerpo, para que giren con él) y dos patas de raíz. El renderer lo agranda a
 * 1,25. Textura 128×128 ({@code textures/entity/mycelium_guardian.png}); lo que brilla, en {@code *_glow.png}.
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
    private final ModelPart body;
    private final ModelPart cap;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public MyceliumGuardianModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.body = root.getChild("body");
        this.cap = body.getChild("cap");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    private static void mushroom(PartDefinition parent, String name, float x, float y, float z, float tiltX, float tiltZ) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(20, 82).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F)
                        .texOffs(28, 82).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 2.0F, 6.0F),
                PartPose.offsetAndRotation(x, y, z, tiltX, 0.0F, tiltZ));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        // Pie de la seta (con la cara delante) y su anillo.
        PartDefinition body = parts.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 54).addBox(-6.0F, -16.0F, -5.0F, 12.0F, 16.0F, 10.0F)
                .texOffs(44, 54).addBox(-7.0F, -7.0F, -6.0F, 14.0F, 2.0F, 12.0F), PartPose.offset(0.0F, 18.0F, 0.0F));
        // Sombrero de tres pisos.
        PartDefinition cap = body.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-13.0F, -4.0F, -13.0F, 26.0F, 4.0F, 26.0F)
                .texOffs(0, 30).addBox(-10.0F, -8.0F, -10.0F, 20.0F, 4.0F, 20.0F)
                .texOffs(80, 30).addBox(-6.0F, -11.0F, -6.0F, 12.0F, 3.0F, 12.0F), PartPose.offset(0.0F, -16.0F, 0.0F));
        mushroom(cap, "mushroom_brim", 11.5F, -4.0F, 0.0F, 0.0F, 0.15F);
        mushroom(body, "mushroom_ring", 5.5F, -7.0F, 3.0F, 0.15F, 0.25F);
        // Brazos de raíz con dos dedos cada uno.
        CubeListBuilder arm = CubeListBuilder.create()
                .texOffs(96, 54).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 13.0F, 4.0F)
                .texOffs(112, 54).addBox(-2.0F, 12.0F, -2.0F, 2.0F, 4.0F, 2.0F)
                .texOffs(112, 60).addBox(0.0F, 12.0F, 0.0F, 2.0F, 3.0F, 2.0F);
        body.addOrReplaceChild("right_arm", arm, PartPose.offset(-8.0F, -13.0F, 0.0F));
        body.addOrReplaceChild("left_arm", arm, PartPose.offset(8.0F, -13.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 82).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 6.0F, 5.0F);
        parts.addOrReplaceChild("right_leg", leg, PartPose.offset(-3.5F, 18.0F, 0.0F));
        parts.addOrReplaceChild("left_leg", leg, PartPose.offset(3.5F, 18.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /** Onda triangular entre -1 y 1 (la del golpe del gólem de hierro). */
    private static float triangle(float x, float period) {
        return (Math.abs(x % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    /** {@code attack}: ticks que le quedan al golpe (0 si no golpea). */
    private void animate(float f, float g, float age, float headYaw, float headPitch, float attack) {
        frames++;
        float step = f * 0.6662F;
        // El pie mira hacia donde mira el guardián (no tiene cabeza aparte) y se balancea al andar.
        this.body.yRot = headYaw * Mth.DEG_TO_RAD * 0.6F;
        this.body.xRot = headPitch * Mth.DEG_TO_RAD * 0.2F;
        this.body.zRot = Mth.sin(step) * 0.06F * g;
        this.cap.zRot = Mth.sin(age * 0.05F) * 0.025F;
        this.cap.xRot = Mth.cos(age * 0.04F) * 0.02F;
        this.rightLeg.xRot = Mth.cos(step) * 1.0F * g;
        this.leftLeg.xRot = Mth.cos(step + Mth.PI) * 1.0F * g;
        if (attack > 0.0F) {
            // Los dos brazos arriba de golpe.
            this.rightArm.xRot = -2.0F + 1.5F * triangle(attack, 10.0F);
            this.leftArm.xRot = -2.0F + 1.5F * triangle(attack, 10.0F);
        } else {
            this.rightArm.xRot = Mth.cos(step + Mth.PI) * 0.8F * g;
            this.leftArm.xRot = Mth.cos(step) * 0.8F * g;
        }
        // Quieto, los brazos se mecen un poco, como raíces.
        this.rightArm.zRot = 0.08F + Mth.sin(age * 0.05F) * 0.04F;
        this.leftArm.zRot = -0.08F - Mth.sin(age * 0.05F) * 0.04F;
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
