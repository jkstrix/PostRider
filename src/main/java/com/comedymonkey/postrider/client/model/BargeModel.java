package com.comedymonkey.postrider.client.model;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.renderer.state.BargeRenderState;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The ferry barge. Geometry is Blockbench's Mojang-mappings export of blockbench/barge.bbmodel, and the animations
 * (rowing, and the pennant and lantern stirring while it waits) come from {@link BargeAnimations}.
 * <p>
 * The lantern's flame is drawn on its own, at full brightness, so it glows at night: one of these models draws the
 * barge without the flame, and another (made {@code glowOnly}) draws only the flame, posed the same way.
 */
public class BargeModel extends EntityModel<BargeRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("barge"), "main");

	private final ModelPart glow;
	private final boolean glowOnly;
	private final KeyframeAnimation row;
	private final KeyframeAnimation idle;

	public BargeModel(ModelPart root, boolean glowOnly) {
		super(root);
		this.glow = root.getChild("barge").getChild("lantern_post").getChild("lantern").getChild("lantern_glow");
		this.glowOnly = glowOnly;
		this.row = BargeAnimations.ROW.bake(root);
		this.idle = BargeAnimations.IDLE.bake(root);
		if (glowOnly)
			allParts().forEach(part -> part.skipDraw = part != glow);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();

		PartDefinition barge = parts.addOrReplaceChild("barge", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		barge.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 64).addBox(-24.0F, -2.0F, -26.0F, 48.0F, 2.0F, 56.0F, new CubeDeformation(0.0F))
			.texOffs(0, 0).addBox(-25.0F, -4.0F, -26.0F, 1.0F, 8.0F, 56.0F, new CubeDeformation(0.0F))
			.texOffs(0, 0).addBox(24.0F, -4.0F, -26.0F, 1.0F, 8.0F, 56.0F, new CubeDeformation(0.0F))
			.texOffs(114, 0).addBox(-26.0F, -4.0F, -26.0F, 1.0F, 2.0F, 56.0F, new CubeDeformation(0.0F))
			.texOffs(114, 0).addBox(25.0F, -4.0F, -26.0F, 1.0F, 2.0F, 56.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		barge.addOrReplaceChild("bow", CubeListBuilder.create().texOffs(116, 122).addBox(-23.0F, -2.0F, -14.0F, 46.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(208, 64).addBox(-24.0F, -4.0F, -14.0F, 1.0F, 8.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(208, 64).addBox(23.0F, -4.0F, -14.0F, 1.0F, 8.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(0, 167).addBox(-25.0F, -4.0F, -14.0F, 1.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(0, 167).addBox(24.0F, -4.0F, -14.0F, 1.0F, 2.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(128, 185).addBox(-24.0F, -6.0F, -15.0F, 48.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(168, 199).addBox(-3.0F, -7.0F, -15.0F, 6.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -26.0F, -0.384F, 0.0F, 0.0F));
		barge.addOrReplaceChild("stern", CubeListBuilder.create().texOffs(114, 167).addBox(-24.0F, -2.0F, -2.0F, 48.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
			.texOffs(90, 122).addBox(-25.0F, -4.0F, -2.0F, 1.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
			.texOffs(90, 122).addBox(24.0F, -4.0F, -2.0F, 1.0F, 8.0F, 12.0F, new CubeDeformation(0.0F))
			.texOffs(0, 185).addBox(-26.0F, -4.0F, -2.0F, 1.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
			.texOffs(0, 185).addBox(25.0F, -4.0F, -2.0F, 1.0F, 2.0F, 12.0F, new CubeDeformation(0.0F))
			.texOffs(26, 185).addBox(-25.0F, -8.0F, 10.0F, 50.0F, 12.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(242, 64).addBox(23.0F, -15.0F, 10.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(242, 64).addBox(8.0F, -15.0F, 10.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(242, 64).addBox(-9.0F, -15.0F, 10.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(242, 64).addBox(-24.0F, -15.0F, 10.0F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 207).addBox(-25.0F, -15.0F, 10.0F, 50.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 30.0F, 0.2094F, 0.0F, 0.0F));
		PartDefinition railings = barge.addOrReplaceChild("railings", CubeListBuilder.create().texOffs(236, 0).addBox(-25.0F, -14.0F, -14.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(-25.0F, -14.0F, 0.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(-25.0F, -14.0F, 14.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(-25.0F, -14.0F, 29.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 122).addBox(-25.0F, -14.0F, -14.0F, 1.0F, 1.0F, 44.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(24.0F, -14.0F, -14.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(24.0F, -14.0F, 0.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(24.0F, -14.0F, 14.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(236, 0).addBox(24.0F, -14.0F, 29.0F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 122).addBox(24.0F, -14.0F, -14.0F, 1.0F, 1.0F, 44.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		railings.addOrReplaceChild("swag_r1", CubeListBuilder.create().texOffs(240, 122).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(240, 122).addBox(-49.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -9.5F, 29.0F, 0.2793F, 0.0F, 0.0F));
		railings.addOrReplaceChild("swag_r2", CubeListBuilder.create().texOffs(240, 122).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(240, 122).addBox(-49.5F, -0.5F, 0.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -9.5F, 15.0F, -0.2793F, 0.0F, 0.0F));
		railings.addOrReplaceChild("swag_r3", CubeListBuilder.create().texOffs(240, 122).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(240, 122).addBox(-49.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -9.5F, 14.0F, 0.2793F, 0.0F, 0.0F));
		railings.addOrReplaceChild("swag_r4", CubeListBuilder.create().texOffs(76, 199).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
			.texOffs(76, 199).addBox(-49.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -9.5F, 1.0F, -0.2793F, 0.0F, 0.0F));
		railings.addOrReplaceChild("swag_r5", CubeListBuilder.create().texOffs(240, 122).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(240, 122).addBox(-49.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -9.5F, 0.0F, 0.2793F, 0.0F, 0.0F));
		railings.addOrReplaceChild("swag_r6", CubeListBuilder.create().texOffs(76, 199).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
			.texOffs(76, 199).addBox(-49.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -9.5F, -13.0F, -0.2793F, 0.0F, 0.0F));
		barge.addOrReplaceChild("fenders", CubeListBuilder.create().texOffs(250, 185).addBox(-27.0F, -4.0F, -10.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(90, 199).addBox(-29.0F, -1.0F, -11.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(250, 185).addBox(-27.0F, -4.0F, 12.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(90, 199).addBox(-29.0F, -1.0F, 11.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(250, 185).addBox(26.0F, -4.0F, -10.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(90, 199).addBox(26.0F, -1.0F, -11.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(250, 185).addBox(26.0F, -4.0F, 12.0F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(90, 199).addBox(26.0F, -1.0F, 11.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		barge.addOrReplaceChild("bench", CubeListBuilder.create().texOffs(0, 199).addBox(-16.0F, -7.0F, -3.0F, 32.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
			.texOffs(102, 199).addBox(12.0F, -5.0F, -2.0F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(102, 199).addBox(-14.0F, -5.0F, -2.0F, 2.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(250, 64).addBox(-25.0F, -7.0F, -3.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(250, 64).addBox(24.0F, -7.0F, -3.0F, 1.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -21.0F));
		barge.addOrReplaceChild("oar_left", CubeListBuilder.create().texOffs(102, 207).addBox(-19.5F, -0.5F, -0.5F, 38.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(138, 199).addBox(-27.5F, -1.5F, -0.5F, 8.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-24.5F, -6.5F, -23.0F, 0.0F, 0.0F, -0.2618F));
		barge.addOrReplaceChild("oar_right", CubeListBuilder.create().texOffs(102, 207).addBox(-18.5F, -0.5F, -0.5F, 38.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(138, 199).addBox(19.5F, -1.5F, -0.5F, 8.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(24.5F, -6.5F, -23.0F, 0.0F, 0.0F, 0.2618F));
		PartDefinition cargo = barge.addOrReplaceChild("cargo", CubeListBuilder.create().texOffs(58, 167).addBox(14.0F, -10.0F, 19.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
			.texOffs(90, 167).addBox(7.0F, -11.0F, 21.0F, 6.0F, 9.0F, 6.0F, new CubeDeformation(0.0F))
			.texOffs(226, 185).addBox(-16.0F, -3.0F, 20.0F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
			.texOffs(114, 199).addBox(-15.0F, -4.0F, 21.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		cargo.addOrReplaceChild("small_crate_r1", CubeListBuilder.create().texOffs(234, 167).addBox(-2.5F, -4.0F, -2.5F, 5.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(18.5F, -10.0F, 23.5F, 0.0F, -0.3142F, 0.0F));
		PartDefinition lanternPost = barge.addOrReplaceChild("lantern_post", CubeListBuilder.create().texOffs(228, 0).addBox(-1.0F, -30.0F, -1.0F, 2.0F, 30.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(184, 199).addBox(1.0F, -29.0F, -0.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(246, 64).addBox(-0.5F, -35.0F, -0.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-22.0F, -2.0F, 27.0F));
		lanternPost.addOrReplaceChild("pennant", CubeListBuilder.create().texOffs(34, 167).addBox(0.0F, -2.5F, 0.0F, 0.0F, 5.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -32.5F, 0.5F));
		PartDefinition lantern = lanternPost.addOrReplaceChild("lantern", CubeListBuilder.create().texOffs(198, 199).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(156, 199).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(240, 0).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(156, 199).addBox(-1.5F, 7.0F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(5.5F, -28.0F, 0.0F));
		lantern.addOrReplaceChild("lantern_glow", CubeListBuilder.create().texOffs(130, 199).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.5F, 0.0F));

		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(BargeRenderState state) {
		super.setupAnim(state);
		row.apply(state.rowAnimation, state.ageInTicks);
		idle.apply(state.idleAnimation, state.ageInTicks);
		glow.visible = glowOnly;
	}
}
