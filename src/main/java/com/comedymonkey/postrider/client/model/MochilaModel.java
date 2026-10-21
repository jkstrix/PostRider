package com.comedymonkey.postrider.client.model;

import com.comedymonkey.postrider.PostRider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * The rider's mochila: a leather cover over the saddle with four mail pockets. Geometry is Blockbench's
 * Mojang-mappings export of blockbench/mochila.bbmodel (without its reference horse); its pivot is the vanilla horse
 * body's, so it can copy the body's pose and move with it.
 */
public class MochilaModel extends Model {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("mochila"), "main");

	private final ModelPart mochila;

	public MochilaModel(ModelPart root) {
		super(RenderType::entityCutoutNoCull);
		this.mochila = root.getChild("mochila");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("mochila", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-6.0F, -9.5F, -10.0F, 12.0F, 1.0F, 11.0F, new CubeDeformation(0.0F))
			.texOffs(0, 12).addBox(-7.0F, -9.5F, -10.0F, 1.0F, 9.0F, 11.0F, new CubeDeformation(0.0F))
			.texOffs(0, 12).mirror().addBox(6.0F, -9.5F, -10.0F, 1.0F, 9.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false)
			.texOffs(46, 0).addBox(-9.0F, -6.0F, -9.5F, 2.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(46, 0).addBox(-9.0F, -6.0F, -3.5F, 2.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(46, 0).mirror().addBox(7.0F, -6.0F, -9.5F, 2.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
			.texOffs(46, 0).mirror().addBox(7.0F, -6.0F, -3.5F, 2.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false),
			PartPose.offset(0.0F, 11.0F, 5.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** Puts the mochila where the horse's body is this frame. */
	public void followBody(ModelPart body) {
		mochila.copyFrom(body);
	}

	ModelPart part() {
		return mochila;
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		mochila.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}
