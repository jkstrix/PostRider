package com.comedymonkey.postrider.client.model;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;

/**
 * The rider's mochila: a leather cover over the saddle with four mail pockets. Geometry is Blockbench's
 * Mojang-mappings export of blockbench/mochila.bbmodel (without its reference horse); its pivot is the vanilla horse
 * body's, so it can copy the body's pose and move with it.
 */
public class MochilaModel extends EntityModel<EquineRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("mochila"), "main");
	/** Vanilla draws a living horse a tenth bigger than its mesh; the mochila is scaled the same way to fit it. */
	public static final MeshTransformer HORSE_SCALE = MeshTransformer.scaling(1.1F);

	private final ModelPart mochila;
	/** The horse this is worn by, posed for each frame to find where its body is; null to be posed by hand. */
	@Nullable
	private final PostmanHorseModel horse;

	public MochilaModel(ModelPart root) {
		this(root, null);
	}

	public MochilaModel(ModelPart root, @Nullable PostmanHorseModel horse) {
		super(root);
		this.mochila = root.getChild("mochila");
		this.horse = horse;
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

	/** The mochila as drawn on a horse: scaled up with it, as vanilla scales the horse's own mesh. */
	public static LayerDefinition createHorseLayer() {
		return createBodyLayer().apply(HORSE_SCALE);
	}

	/** Puts the mochila where the horse's body is this frame. */
	public void followBody(ModelPart body) {
		mochila.setPos(body.x, body.y, body.z);
		mochila.setRotation(body.xRot, body.yRot, body.zRot);
		mochila.xScale = body.xScale;
		mochila.yScale = body.yScale;
		mochila.zScale = body.zScale;
	}

	ModelPart part() {
		return mochila;
	}

	/** Puts the mochila where the horse's body is in this frame, the horse posed as it's drawn. */
	@Override
	public void setupAnim(EquineRenderState state) {
		super.setupAnim(state);
		if (horse != null) {
			horse.setupAnim(state);
			followBody(horse.body());
		}
	}
}
