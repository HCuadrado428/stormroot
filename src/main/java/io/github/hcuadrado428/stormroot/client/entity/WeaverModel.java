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
 * Modelo de la tejedora: el cuerpo y la forma de andar de la araña vanilla ({@code SpiderModel}, mismas piezas y
 * pivotes) con setas que le crecen por el abdomen, la cabeza y dos patas. El renderer la encoge a 0,7 como a la araña
 * de cueva. Textura 64×64 ({@code textures/entity/weaver.png}): arriba la distribución de la araña, abajo las setas.
 */
//? if >=1.21.2 {
public class WeaverModel extends net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> {
//?} else {
/*public class WeaverModel extends net.minecraft.client.model.HierarchicalModel<io.github.hcuadrado428.stormroot.entity.Weaver> {
*///?}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModIds.id("weaver"), "main");
    public static long frames;

    private static final float QUARTER = Mth.PI / 4.0F;
    private static final float EIGHTH = Mth.PI / 8.0F;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightMiddleHindLeg;
    private final ModelPart leftMiddleHindLeg;
    private final ModelPart rightMiddleFrontLeg;
    private final ModelPart leftMiddleFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;

    public WeaverModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.head = root.getChild("head");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightMiddleHindLeg = root.getChild("right_middle_hind_leg");
        this.leftMiddleHindLeg = root.getChild("left_middle_hind_leg");
        this.rightMiddleFrontLeg = root.getChild("right_middle_front_leg");
        this.leftMiddleFrontLeg = root.getChild("left_middle_front_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
    }

    /** Tamaños de seta: 0 grande, 1 pequeña, 2 diminuta (cada uno con su sitio en la textura). */
    private static void mushroom(PartDefinition parent, String name, int size, float x, float y, float z, float tiltX, float tiltZ) {
        float stemW = size == 0 ? 2.0F : 1.0F;
        float stemH = size == 0 ? 3.0F : size == 1 ? 2.0F : 1.0F;
        float capW = size == 0 ? 5.0F : size == 1 ? 3.0F : 2.0F;
        float capH = size == 0 ? 2.0F : 1.0F;
        int stemU = size == 0 ? 20 : size == 1 ? 40 : 52;
        int capU = size == 0 ? 0 : size == 1 ? 28 : 44;
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(stemU, 32).addBox(-stemW / 2.0F, -stemH, -stemW / 2.0F, stemW, stemH, stemW)
                        .texOffs(capU, 32).addBox(-capW / 2.0F, -stemH - capH, -capW / 2.0F, capW, capH, capW),
                PartPose.offsetAndRotation(x, y, z, tiltX, 0.0F, tiltZ));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition head = parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(32, 4)
                .addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 15.0F, -3.0F));
        parts.addOrReplaceChild("body0", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
        PartDefinition body = parts.addOrReplaceChild("body1", CubeListBuilder.create().texOffs(0, 12)
                .addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F), PartPose.offset(0.0F, 15.0F, 9.0F));
        CubeListBuilder right = CubeListBuilder.create().texOffs(18, 0).addBox(-15.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F);
        CubeListBuilder left = CubeListBuilder.create().texOffs(18, 0).mirror().addBox(-1.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F);
        parts.addOrReplaceChild("right_hind_leg", right, PartPose.offset(-4.0F, 15.0F, 2.0F));
        parts.addOrReplaceChild("left_hind_leg", left, PartPose.offset(4.0F, 15.0F, 2.0F));
        PartDefinition rightMiddleHind = parts.addOrReplaceChild("right_middle_hind_leg", right, PartPose.offset(-4.0F, 15.0F, 1.0F));
        parts.addOrReplaceChild("left_middle_hind_leg", left, PartPose.offset(4.0F, 15.0F, 1.0F));
        parts.addOrReplaceChild("right_middle_front_leg", right, PartPose.offset(-4.0F, 15.0F, 0.0F));
        parts.addOrReplaceChild("left_middle_front_leg", left, PartPose.offset(4.0F, 15.0F, 0.0F));
        parts.addOrReplaceChild("right_front_leg", right, PartPose.offset(-4.0F, 15.0F, -1.0F));
        PartDefinition leftFront = parts.addOrReplaceChild("left_front_leg", left, PartPose.offset(4.0F, 15.0F, -1.0F));

        // Setas: la grande en el lomo, un corro alrededor, una que sale de lado y otras en la cabeza y en dos patas.
        mushroom(body, "mushroom_big", 0, 1.5F, -4.0F, 1.0F, -0.1F, 0.12F);
        mushroom(body, "mushroom_back_a", 1, -2.5F, -4.0F, -2.5F, 0.05F, -0.2F);
        mushroom(body, "mushroom_back_b", 1, -1.0F, -4.0F, 4.0F, 0.2F, 0.05F);
        mushroom(body, "mushroom_back_c", 2, 3.5F, -4.0F, -3.5F, -0.15F, 0.3F);
        mushroom(body, "mushroom_side", 2, -5.0F, -1.0F, 2.0F, 0.0F, -1.3F);
        mushroom(head, "mushroom_head_a", 1, 1.5F, -4.0F, -5.0F, 0.1F, 0.25F);
        mushroom(head, "mushroom_head_b", 2, -2.5F, -4.0F, -2.0F, -0.1F, -0.2F);
        mushroom(leftFront, "mushroom_leg_a", 2, 7.0F, -1.0F, 0.0F, 0.0F, -0.5F);
        mushroom(rightMiddleHind, "mushroom_leg_b", 2, -8.0F, -1.0F, 0.0F, 0.0F, 0.5F);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** La misma animación que la araña vanilla. */
    private void animate(float f, float g, float headYaw, float headPitch) {
        frames++;
        this.head.yRot = headYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.rightHindLeg.zRot = -QUARTER;
        this.leftHindLeg.zRot = QUARTER;
        this.rightMiddleHindLeg.zRot = -0.58119464F;
        this.leftMiddleHindLeg.zRot = 0.58119464F;
        this.rightMiddleFrontLeg.zRot = -0.58119464F;
        this.leftMiddleFrontLeg.zRot = 0.58119464F;
        this.rightFrontLeg.zRot = -QUARTER;
        this.leftFrontLeg.zRot = QUARTER;
        this.rightHindLeg.yRot = QUARTER;
        this.leftHindLeg.yRot = -QUARTER;
        this.rightMiddleHindLeg.yRot = EIGHTH;
        this.leftMiddleHindLeg.yRot = -EIGHTH;
        this.rightMiddleFrontLeg.yRot = -EIGHTH;
        this.leftMiddleFrontLeg.yRot = EIGHTH;
        this.rightFrontLeg.yRot = -QUARTER;
        this.leftFrontLeg.yRot = QUARTER;
        float n = -(Mth.cos(f * 0.6662F * 2.0F) * 0.4F) * g;
        float o = -(Mth.cos(f * 0.6662F * 2.0F + Mth.PI) * 0.4F) * g;
        float p = -(Mth.cos(f * 0.6662F * 2.0F + Mth.PI / 2.0F) * 0.4F) * g;
        float q = -(Mth.cos(f * 0.6662F * 2.0F + Mth.PI * 3.0F / 2.0F) * 0.4F) * g;
        float r = Math.abs(Mth.sin(f * 0.6662F) * 0.4F) * g;
        float s = Math.abs(Mth.sin(f * 0.6662F + Mth.PI) * 0.4F) * g;
        float t = Math.abs(Mth.sin(f * 0.6662F + Mth.PI / 2.0F) * 0.4F) * g;
        float u = Math.abs(Mth.sin(f * 0.6662F + Mth.PI * 3.0F / 2.0F) * 0.4F) * g;
        this.rightHindLeg.yRot += n;
        this.leftHindLeg.yRot += -n;
        this.rightMiddleHindLeg.yRot += o;
        this.leftMiddleHindLeg.yRot += -o;
        this.rightMiddleFrontLeg.yRot += p;
        this.leftMiddleFrontLeg.yRot += -p;
        this.rightFrontLeg.yRot += q;
        this.leftFrontLeg.yRot += -q;
        this.rightHindLeg.zRot += r;
        this.leftHindLeg.zRot += -r;
        this.rightMiddleHindLeg.zRot += s;
        this.leftMiddleHindLeg.zRot += -s;
        this.rightMiddleFrontLeg.zRot += t;
        this.leftMiddleFrontLeg.zRot += -t;
        this.rightFrontLeg.zRot += u;
        this.leftFrontLeg.zRot += -u;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        super.setupAnim(state);
        animate(state.walkAnimationPos, state.walkAnimationSpeed, state.yRot, state.xRot);
    }
    //?} else {
    /*@Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(io.github.hcuadrado428.stormroot.entity.Weaver entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        animate(limbSwing, limbSwingAmount, netHeadYaw, headPitch);
    }
    *///?}
}
