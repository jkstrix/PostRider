package com.comedymonkey.postrider.client.model;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.PostmanEntity;

import net.minecraft.client.animation.AnimationDefinition;
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

/**
 * The rider's model. The geometry is Blockbench's Mojang-mappings export of blockbench/postman.bbmodel, and the
 * animations come from {@link PostmanAnimations}.
 */
public class PostmanModel extends HierarchicalModel<PostmanEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("postman"), "main");

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart harmonica;
	private final ModelPart hammer;
	private final ModelPart satchelFlat;
	private final ModelPart satchelFull;

	public PostmanModel(ModelPart baked) {
		// Animations of the "root" bone go to root(), so that has to be the bone at his feet that Blockbench pivots
		// them on, not the part above it (which pivots 1.5 blocks up and swung him into the air lying down)
		this.root = baked.getChild("root");
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.harmonica = head.getChild("harmonica");
		this.hammer = body.getChild("right_arm").getChild("hammer");
		ModelPart satchel = body.getChild("satchel");
		this.satchelFlat = satchel.getChild("satchel_flat");
		this.satchelFull = satchel.getChild("satchel_full");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();

		PartDefinition root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(64, 28).addBox(-4.5F, -12.0F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
			.texOffs(92, 28).addBox(-1.5F, -10.0F, -3.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));
		head.addOrReplaceChild("harmonica", CubeListBuilder.create().texOffs(112, 28).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -1.5F, -4.5F));

		PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(64, 0).addBox(-4.5F, -1.5F, -4.5F, 9.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
			.texOffs(64, 44).addBox(-4.0F, -2.5F, -3.5F, 8.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(64, 13).addBox(-5.0F, 0.5F, -7.0F, 10.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.5F, 0.0F));
		hat.addOrReplaceChild("hat_brim_right_r1", CubeListBuilder.create().texOffs(64, 52).addBox(0.0F, -0.5F, -5.5F, 3.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(4.5F, 1.0F, 0.0F, 0.0F, 0.0F, -0.3927F));
		hat.addOrReplaceChild("hat_brim_left_r1", CubeListBuilder.create().texOffs(96, 48).addBox(-3.0F, -0.5F, -5.5F, 3.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-4.5F, 1.0F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -10.0F, 0.0F));
		rightArm.addOrReplaceChild("hammer", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(48, 0).addBox(-1.0F, -1.5F, -8.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, 9.5F, 0.0F));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -10.0F, 0.0F));

		PartDefinition satchel = body.addOrReplaceChild("satchel", CubeListBuilder.create(), PartPose.offset(-2.5F, -3.0F, 2.0F));
		satchel.addOrReplaceChild("satchel_flat", CubeListBuilder.create().texOffs(100, 28).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		satchel.addOrReplaceChild("satchel_full", CubeListBuilder.create().texOffs(100, 36).addBox(-2.5F, 0.0F, 0.0F, 5.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(1.9F, -12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.9F, -12.0F, 0.0F));

		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public ModelPart root() {
		return root;
	}

	@Override
	public void setupAnim(PostmanEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		root().getAllParts().forEach(ModelPart::resetPose);
		if (headTurns(entity.getActivity(), entity.isGesturing())) {
			head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
			head.xRot = headPitch * Mth.DEG_TO_RAD;
		}

		boolean free = entity.getVehicle() == null && entity.getActivity() == PostmanEntity.Activity.NONE;
		if (free)
			animateWalk(PostmanAnimations.WALK, limbSwing, limbSwingAmount, 2.0F, 2.5F);
		animate(entity.idleAnimation, PostmanAnimations.IDLE, ageInTicks);
		animate(entity.rideAnimation, PostmanAnimations.RIDE, ageInTicks);
		animate(entity.rideIdleAnimation, PostmanAnimations.RIDE_IDLE, ageInTicks);
		animate(entity.activityAnimations.get(PostmanEntity.Activity.CAMP_HAMMER), PostmanAnimations.CAMP_HAMMER, ageInTicks);
		animate(entity.activityAnimations.get(PostmanEntity.Activity.CAMP_REST), PostmanAnimations.CAMP_REST, ageInTicks);
		animate(entity.activityAnimations.get(PostmanEntity.Activity.ROW), PostmanAnimations.ROW, ageInTicks);
		animate(entity.activityAnimations.get(PostmanEntity.Activity.HARMONICA), PostmanAnimations.HARMONICA, ageInTicks);
		animate(entity.activityAnimations.get(PostmanEntity.Activity.SLEEP), PostmanAnimations.SLEEP, ageInTicks);
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values())
			animate(entity.gestureAnimations.get(gesture), gestureAnimation(gesture), ageInTicks);
		animate(entity.hurtAnimation, PostmanAnimations.HURT, ageInTicks);

		harmonica.visible = entity.getActivity() == PostmanEntity.Activity.HARMONICA;
		hammer.visible = entity.getActivity() == PostmanEntity.Activity.CAMP_HAMMER;
		satchelFlat.visible = !entity.isCarrying();
		satchelFull.visible = entity.isCarrying();
	}

	/** His head only follows where he's looking while he's free; at work the animation keeps his eyes on the job. */
	public static boolean headTurns(PostmanEntity.Activity activity, boolean gesturing) {
		return activity == PostmanEntity.Activity.NONE && !gesturing;
	}

	public static AnimationDefinition gestureAnimation(PostmanEntity.Gesture gesture) {
		return switch (gesture) {
			case PICKUP -> PostmanAnimations.PICKUP;
			case DELIVER -> PostmanAnimations.DELIVER;
			case WHISTLE -> PostmanAnimations.WHISTLE;
			case WAVE -> PostmanAnimations.WAVE;
		};
	}
}
