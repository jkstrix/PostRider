package com.comedymonkey.postrider.client.model;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.renderer.state.TentRenderState;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** The rider's A-frame canvas tent. Geometry is Blockbench's Mojang-mappings export of blockbench/tent.bbmodel. */
public class TentModel extends EntityModel<TentRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("tent"), "main");

	public TentModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition tent = mesh.getRoot().addOrReplaceChild("tent", CubeListBuilder.create()
			.texOffs(0, 64).addBox(-12.0F, -4.0F, 17.0F, 24.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 64).addBox(-9.0F, -8.0F, 17.0F, 18.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 64).addBox(-7.0F, -12.0F, 17.0F, 14.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 64).addBox(-4.0F, -16.0F, 17.0F, 8.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 64).addBox(-2.0F, -19.0F, 17.0F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(0, 89).addBox(-0.5F, -21.0F, -19.0F, 1.0F, 1.0F, 38.0F, new CubeDeformation(0.0F))
			.texOffs(100, 0).addBox(-0.5F, -20.0F, -18.5F, 1.0F, 20.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(100, 0).addBox(-0.5F, -20.0F, 17.5F, 1.0F, 20.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(106, 0).addBox(-0.5F, -2.0F, -27.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
			.texOffs(106, 0).addBox(-0.5F, -2.0F, 26.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));
		tent.addOrReplaceChild("rope_back_r1", CubeListBuilder.create().texOffs(110, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -20.0F, 18.5F, 0.4014F, 0.0F, 0.0F));
		tent.addOrReplaceChild("rope_front_r1", CubeListBuilder.create().texOffs(110, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 22.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -20.0F, -18.5F, -0.4014F, 0.0F, 0.0F));
		tent.addOrReplaceChild("flap_right_r1", CubeListBuilder.create().texOffs(76, 0).mirror().addBox(-0.5F, 0.0F, -18.6F, 1.0F, 25.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false),
			PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 0.0F, 0.6632F));
		tent.addOrReplaceChild("flap_left_r1", CubeListBuilder.create().texOffs(76, 0).addBox(-0.5F, 0.0F, -18.6F, 1.0F, 25.0F, 7.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 0.0F, -0.6632F));
		tent.addOrReplaceChild("side_right_r1", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-0.5F, 0.0F, -18.0F, 1.0F, 25.0F, 36.0F, new CubeDeformation(0.0F)).mirror(false),
			PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 0.0F, 0.6109F));
		tent.addOrReplaceChild("side_left_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, 0.0F, -18.0F, 1.0F, 25.0F, 36.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, -20.0F, 0.0F, 0.0F, 0.0F, -0.6109F));
		return LayerDefinition.create(mesh, 128, 128);
	}

}
