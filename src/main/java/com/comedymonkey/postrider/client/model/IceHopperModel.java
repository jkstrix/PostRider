package com.comedymonkey.postrider.client.model;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.hopper.IceHopperEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * The ice hopper. Geometry is Blockbench's Mojang-mappings export of blockbench/ice_hopper.bbmodel; the oars swing
 * with the boat's own rowing time, as a vanilla boat's paddles do.
 */
public class IceHopperModel extends HierarchicalModel<IceHopperEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("ice_hopper"), "main");

	private final ModelPart root;
	private final ModelPart oarLeft;
	private final ModelPart oarRight;

	public IceHopperModel(ModelPart root) {
		this.root = root.getChild("boat");
		this.oarLeft = this.root.getChild("oar_left");
		this.oarRight = this.root.getChild("oar_right");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();

		PartDefinition boat = parts.addOrReplaceChild("boat", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		boat.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-10.0F, -2.0F, -15.0F, 20.0F, 2.0F, 31.0F, new CubeDeformation(0.0F))
			.texOffs(0, 32).addBox(-10.0F, -8.0F, -14.0F, 1.0F, 6.0F, 29.0F, new CubeDeformation(0.0F))
			.texOffs(64, 32).addBox(9.0F, -8.0F, -14.0F, 1.0F, 6.0F, 29.0F, new CubeDeformation(0.0F))
			.texOffs(0, 68).addBox(-10.0F, -8.0F, 15.0F, 20.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 76).addBox(-9.5F, -6.0F, 6.0F, 19.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(48, 76).addBox(-9.5F, -6.0F, -6.0F, 19.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(48, 68).addBox(-9.25F, -8.0F, -15.0F, 18.5F, 6.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		boat.addOrReplaceChild("bow", CubeListBuilder.create().texOffs(0, 84).addBox(-9.9F, -2.0F, -10.0F, 19.8F, 2.0F, 10.0F, new CubeDeformation(0.0F))
			.texOffs(60, 84).addBox(-9.9F, -7.0F, -10.0F, 0.8F, 5.0F, 10.0F, new CubeDeformation(0.0F))
			.texOffs(84, 84).addBox(9.1F, -7.0F, -10.0F, 0.8F, 5.0F, 10.0F, new CubeDeformation(0.0F))
			.texOffs(0, 100).addBox(-9.9F, -3.0F, -11.0F, 19.8F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -14.0F, -0.4363F, 0.0F, 0.0F));

		boat.addOrReplaceChild("oar_left", CubeListBuilder.create().texOffs(60, 106).addBox(-1.0F, -1.0F, -8.0F, 1.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(96, 106).addBox(-1.0F, -1.0F, 8.0F, 1.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-10.0F, -7.0F, 0.0F));

		boat.addOrReplaceChild("oar_right", CubeListBuilder.create().texOffs(60, 106).addBox(0.0F, -1.0F, -8.0F, 1.0F, 1.0F, 16.0F, new CubeDeformation(0.0F))
			.texOffs(96, 106).addBox(0.0F, -1.0F, 8.0F, 1.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(10.0F, -7.0F, 0.0F));

		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public ModelPart root() {
		return root;
	}

	/** {@code limbSwing} carries the partial tick: the oars follow the boat's rowing time. */
	@Override
	public void setupAnim(IceHopperEntity hopper, float partialTick, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		swing(oarLeft, hopper.getRowingTime(Boat.PADDLE_LEFT, partialTick), 1);
		swing(oarRight, hopper.getRowingTime(Boat.PADDLE_RIGHT, partialTick), -1);
	}

	/** An oar's dip and sweep at a point in the stroke, as a vanilla paddle's. */
	public static void swing(ModelPart oar, float time, int side) {
		oar.xRot = Mth.clampedLerp(-(float) Math.PI / 3, -(float) Math.PI / 12, (Mth.sin(-time) + 1) / 2) + (float) Math.PI / 3;
		// (Swept the other way from a vanilla paddle: these oars lie along the hull, blades aft)
		oar.yRot = -side * Mth.clampedLerp(-(float) Math.PI / 4, (float) Math.PI / 4, (Mth.sin(-time + 1) + 1) / 2);
	}
}
