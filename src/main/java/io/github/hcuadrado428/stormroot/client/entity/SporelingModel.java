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
 * Modelo del esporín: cuerpo redondo con un sombrerito de seta y cuatro patitas. Textura 32×32
 * ({@code textures/entity/sporeling.png}). Hasta 1.21.1 se anima con la entidad; desde 1.21.2, con su estado de render.
 */
//? if >=1.21.2 {
public class SporelingModel extends net.minecraft.client.model.EntityModel<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> {
//?} else {
/*public class SporelingModel extends net.minecraft.client.model.HierarchicalModel<io.github.hcuadrado428.stormroot.entity.Sporeling> {
*///?}
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ModIds.id("sporeling"), "main");
    /** Veces que se ha animado (para el self-test: el modelo se usa al dibujar). */
    public static long frames;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart cap;
    private final ModelPart leftFront;
    private final ModelPart rightFront;
    private final ModelPart leftBack;
    private final ModelPart rightBack;

    public SporelingModel(ModelPart root) {
        //? if >=1.21.2 {
        super(root);
        //?}
        this.root = root;
        this.body = root.getChild("body");
        this.cap = this.body.getChild("cap");
        this.leftFront = root.getChild("left_front");
        this.rightFront = root.getChild("right_front");
        this.leftBack = root.getChild("left_back");
        this.rightBack = root.getChild("right_back");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition body = parts.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 22.0F, 0.0F));
        body.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 12)
                .addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F), PartPose.offset(0.0F, -6.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 22).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F);
        parts.addOrReplaceChild("left_front", leg, PartPose.offset(2.0F, 22.0F, -2.0F));
        parts.addOrReplaceChild("right_front", leg, PartPose.offset(-2.0F, 22.0F, -2.0F));
        parts.addOrReplaceChild("left_back", leg, PartPose.offset(2.0F, 22.0F, 2.0F));
        parts.addOrReplaceChild("right_back", leg, PartPose.offset(-2.0F, 22.0F, 2.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Patitas que se mueven al andar y un sombrero que se balancea un poco. */
    private void animate(float walkPos, float walkSpeed, float age) {
        frames++;
        float swing = Mth.cos(walkPos * 1.6F) * 1.2F * walkSpeed;
        this.leftFront.xRot = swing;
        this.rightBack.xRot = swing;
        this.rightFront.xRot = -swing;
        this.leftBack.xRot = -swing;
        this.body.y = 22.0F + Mth.abs(Mth.sin(walkPos * 1.6F)) * -0.6F * walkSpeed;
        this.cap.zRot = Mth.sin(age * 0.08F) * 0.06F;
    }

    //? if >=1.21.2 {
    @Override
    public void setupAnim(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        super.setupAnim(state);
        animate(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks);
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
        animate(limbSwing, limbSwingAmount, ageInTicks);
    }
    *///?}
}
