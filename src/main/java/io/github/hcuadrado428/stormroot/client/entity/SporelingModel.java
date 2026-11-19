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
 * Modelo del esporín: un creeper en miniatura (mismas piezas y pivotes que {@code CreeperModel}) con un sombrero de
 * seta de ala ancha y cúpula. El renderer lo encoge a 0,35. Textura 64×64 ({@code textures/entity/sporeling.png}):
 * arriba la distribución del creeper, abajo el sombrero.
 */
//? if >=1.21.2 {
public class SporelingModel extends net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> {
//?} else {
/*public class SporelingModel extends net.minecraft.client.model.HierarchicalModel<io.github.hcuadrado428.stormroot.entity.Sporeling> {
*///?}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModIds.id("sporeling"), "main");
    public static long frames;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart cap;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;

    public SporelingModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.head = root.getChild("head");
        this.cap = head.getChild("cap");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition head = parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 6.0F, 0.0F));
        head.addOrReplaceChild("cap", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-6.0F, -10.0F, -6.0F, 12.0F, 2.0F, 12.0F)
                .texOffs(0, 46).addBox(-4.0F, -13.0F, -4.0F, 8.0F, 3.0F, 8.0F), PartPose.ZERO);
        parts.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F), PartPose.offset(0.0F, 6.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F);
        parts.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.0F, 18.0F, 4.0F));
        parts.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(2.0F, 18.0F, 4.0F));
        parts.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.0F, 18.0F, -4.0F));
        parts.addOrReplaceChild("left_front_leg", leg, PartPose.offset(2.0F, 18.0F, -4.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Las patas andan como las del creeper; el sombrero se mece un poco. */
    private void animate(float f, float g, float age, float headYaw, float headPitch) {
        frames++;
        this.head.yRot = headYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.rightHindLeg.xRot = Mth.cos(f * 0.6662F) * 1.4F * g;
        this.leftHindLeg.xRot = Mth.cos(f * 0.6662F + Mth.PI) * 1.4F * g;
        this.rightFrontLeg.xRot = Mth.cos(f * 0.6662F + Mth.PI) * 1.4F * g;
        this.leftFrontLeg.xRot = Mth.cos(f * 0.6662F) * 1.4F * g;
        this.cap.zRot = Mth.sin(age * 0.08F) * 0.05F;
        this.cap.xRot = Mth.cos(age * 0.06F) * 0.03F;
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
    public void setupAnim(io.github.hcuadrado428.stormroot.entity.Sporeling entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        animate(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
    *///?}
}
