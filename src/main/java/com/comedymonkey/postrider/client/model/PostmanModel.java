package com.comedymonkey.postrider.client.model;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.renderer.state.PostmanRenderState;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * The rider's model. The geometry is Blockbench's Mojang-mappings export of blockbench/postman.bbmodel, or for the
 * women of the crew blockbench/postman_slim.bbmodel (a narrower head and slim arms); both share the parts and so the
 * animations from {@link PostmanAnimations}. Long hair is drawn with extra parts on the head, shown for whoever wears it.
 */
public class PostmanModel extends EntityModel<PostmanRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(PostRider.asResource("postman"), "main");
	public static final ModelLayerLocation SLIM_LAYER = new ModelLayerLocation(PostRider.asResource("postman"), "slim");
	public static final List<String> HAIR_PARTS = List.of("hair_back", "hair_bun", "hair_braid", "hair_pigtails");

	/** The bone at his feet that the animations call "root" (see the constructor), not the model's own root above it. */
	private final ModelPart root;
	private final Function<String, ModelPart> bones;
	private final ModelPart head;
	private final ModelPart harmonica;
	private final ModelPart hammer;
	private final ModelPart rightArm;
	private final ModelPart potion;
	private final ModelPart canteen;
	private final ModelPart food;
	private final ModelPart pocketWatch;
	private final ModelPart letter;
	private final ModelPart striker;
	private final ModelPart flint;
	private final ModelPart map;
	private final ModelPart satchelFlat;
	private final ModelPart satchelFull;
	private final Map<String, ModelPart> hair = new HashMap<>();
	private final KeyframeAnimation walk;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation ride;
	private final KeyframeAnimation rideIdle;
	private final KeyframeAnimation hurt;
	private final Map<PostmanEntity.Activity, KeyframeAnimation> activities = new EnumMap<>(PostmanEntity.Activity.class);
	private final Map<PostmanEntity.Gesture, KeyframeAnimation> gestures = new EnumMap<>(PostmanEntity.Gesture.class);

	public PostmanModel(ModelPart baked) {
		super(baked, RenderTypes::entityCutout);
		// Animations of the "root" bone have to go to the bone at his feet that Blockbench pivots them on, not the
		// model's own root above it (which pivots 1.5 blocks up and swung him into the air lying down), so they're
		// baked against that bone: a part's lookup answers "root" with the part itself
		this.root = baked.getChild("root");
		this.bones = root.createPartLookup();
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.harmonica = head.getChild("harmonica");
		this.rightArm = body.getChild("right_arm");
		this.hammer = rightArm.getChild("hammer");
		this.potion = rightArm.getChild("potion");
		this.canteen = rightArm.getChild("canteen");
		this.food = rightArm.getChild("food");
		ModelPart leftArm = body.getChild("left_arm");
		this.pocketWatch = leftArm.getChild("pocket_watch");
		this.letter = leftArm.getChild("letter");
		this.striker = rightArm.getChild("striker");
		this.flint = leftArm.getChild("flint");
		this.map = body.getChild("map");
		ModelPart satchel = body.getChild("satchel");
		this.satchelFlat = satchel.getChild("satchel_flat");
		this.satchelFull = satchel.getChild("satchel_full");
		for (String part : HAIR_PARTS)
			hair.put(part, head.getChild(part));
		this.walk = PostmanAnimations.WALK.bake(root);
		this.idle = PostmanAnimations.IDLE.bake(root);
		this.ride = PostmanAnimations.RIDE.bake(root);
		this.rideIdle = PostmanAnimations.RIDE_IDLE.bake(root);
		this.hurt = PostmanAnimations.HURT.bake(root);
		for (PostmanEntity.Activity activity : PostmanEntity.Activity.values()) {
			AnimationDefinition animation = activityAnimation(activity);
			if (animation != null)
				activities.put(activity, animation.bake(root));
		}
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values())
			gestures.put(gesture, gestureAnimation(gesture).bake(root));
	}

	public static LayerDefinition createBodyLayer() {
		return createBodyLayer(false);
	}

	public static LayerDefinition createSlimBodyLayer() {
		return createBodyLayer(true);
	}

	/** The two builds differ only in the head's width (8 or 7), the arms' (4 or 3) and where things sit on them. */
	public static LayerDefinition createBodyLayer(boolean slim) {
		float armWidth = slim ? 3.0F : 4.0F, handOffset = slim ? 0.5F : 1.0F;
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parts = mesh.getRoot();

		PartDefinition root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
			.texOffs(64, 28).addBox(-4.5F, -12.0F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
			.texOffs(92, 28).addBox(-1.5F, -10.0F, -3.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(slim ? -3.5F : -4.0F, -8.0F, -4.0F, slim ? 7.0F : 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -12.0F, 0.0F));
		head.addOrReplaceChild("harmonica", CubeListBuilder.create().texOffs(112, 28).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -1.5F, -4.5F));

		head.addOrReplaceChild("hair_back", CubeListBuilder.create().texOffs(0, 32).addBox(slim ? -4.0F : -4.5F, -6.0F, 4.0F, slim ? 8.0F : 9.0F, 8.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.ZERO);
		head.addOrReplaceChild("hair_bun", CubeListBuilder.create().texOffs(20, 32).addBox(-1.5F, -4.0F, 4.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.ZERO);
		head.addOrReplaceChild("hair_braid", CubeListBuilder.create().texOffs(30, 32).addBox(-1.0F, -1.0F, 2.0F, 2.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.ZERO);
		head.addOrReplaceChild("hair_pigtails", CubeListBuilder.create().texOffs(36, 32).addBox(slim ? 3.5F : 4.0F, -2.0F, 0.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(36, 32).addBox(slim ? -4.5F : -5.0F, -2.0F, 0.0F, 1.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.ZERO);

		PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(64, 0).addBox(-4.5F, -1.5F, -4.5F, 9.0F, 3.0F, 9.0F, new CubeDeformation(0.0F))
			.texOffs(64, 44).addBox(-4.0F, -2.5F, -3.5F, 8.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(64, 13).addBox(-5.0F, 0.5F, -7.0F, 10.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.5F, 0.0F));
		hat.addOrReplaceChild("hat_brim_right_r1", CubeListBuilder.create().texOffs(64, 52).addBox(0.0F, -0.5F, -5.5F, 3.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(4.5F, 1.0F, 0.0F, 0.0F, 0.0F, -0.3927F));
		hat.addOrReplaceChild("hat_brim_left_r1", CubeListBuilder.create().texOffs(96, 48).addBox(-3.0F, -0.5F, -5.5F, 3.0F, 1.0F, 11.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-4.5F, 1.0F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0F, -2.0F, -2.0F, armWidth, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -10.0F, 0.0F));
		rightArm.addOrReplaceChild("hammer", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
			.texOffs(48, 0).addBox(-1.0F, -1.5F, -8.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(handOffset, 9.5F, 0.0F));
		rightArm.addOrReplaceChild("potion", CubeListBuilder.create().texOffs(32, 8).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(44, 8).addBox(-0.5F, -0.5F, -7.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
			.texOffs(50, 8).addBox(-0.5F, -0.5F, -8.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(handOffset, 9.5F, 0.0F));
		rightArm.addOrReplaceChild("canteen", CubeListBuilder.create().texOffs(56, 0).addBox(-0.5F, -1.5F, -5.0F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
			.texOffs(48, 5).addBox(-0.5F, -0.5F, -6.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(handOffset, 9.5F, 0.0F));
		rightArm.addOrReplaceChild("striker", CubeListBuilder.create().texOffs(48, 36).addBox(-0.5F, 0.0F, -5.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offset(handOffset, 9.5F, 0.0F));
		// A hunk of bread, poking out below his fist so it reaches his mouth when his arm comes up
		rightArm.addOrReplaceChild("food", CubeListBuilder.create().texOffs(54, 6).addBox(-1.0F, 0.0F, -4.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
			PartPose.offset(handOffset, 9.5F, 0.0F));
		PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(slim ? -2.0F : -3.0F, -2.0F, -2.0F, armWidth, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -10.0F, 0.0F));
		leftArm.addOrReplaceChild("pocket_watch", CubeListBuilder.create().texOffs(58, 11).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-handOffset, 9.5F, 0.0F));
		leftArm.addOrReplaceChild("flint", CubeListBuilder.create().texOffs(56, 36).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(-handOffset, 9.5F, 0.0F));
		leftArm.addOrReplaceChild("letter", CubeListBuilder.create().texOffs(44, 11).addBox(-1.5F, 1.0F, -6.0F, 4.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-handOffset, 9.5F, 0.0F));

		// The route map, held open in front of his chest; its right half is hinged at the crease so it can unfold
		PartDefinition map = body.addOrReplaceChild("map", CubeListBuilder.create().texOffs(64, 35).addBox(-6.0F, -8.0F, 0.0F, 6.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, -2.5F, -8.5F));
		map.addOrReplaceChild("map_right", CubeListBuilder.create().texOffs(76, 35).addBox(0.0F, -8.0F, 0.0F, 6.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.ZERO);

		PartDefinition satchel = body.addOrReplaceChild("satchel", CubeListBuilder.create(), PartPose.offset(-2.5F, -3.0F, 2.0F));
		satchel.addOrReplaceChild("satchel_flat", CubeListBuilder.create().texOffs(100, 28).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
		satchel.addOrReplaceChild("satchel_full", CubeListBuilder.create().texOffs(100, 36).addBox(-2.5F, 0.0F, 0.0F, 5.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(1.9F, -12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.9F, -12.0F, 0.0F));

		return LayerDefinition.create(mesh, 128, 64);
	}

	/** The bone the animations are baked against, which they call "root": the one at his feet. */
	public ModelPart animationRoot() {
		return root;
	}

	@Override
	public void setupAnim(PostmanRenderState state) {
		super.setupAnim(state);
		if (headTurns(state.activity, state.gesturing)) {
			head.yRot = state.yRot * Mth.DEG_TO_RAD;
			head.xRot = state.xRot * Mth.DEG_TO_RAD;
		}

		float ageInTicks = state.ageInTicks;
		boolean free = !state.inVehicle && state.activity == PostmanEntity.Activity.NONE;
		if (free)
			walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
		idle.apply(state.idleAnimation, ageInTicks);
		ride.apply(state.rideAnimation, ageInTicks);
		rideIdle.apply(state.rideIdleAnimation, ageInTicks);
		for (PostmanEntity.Activity activity : PostmanEntity.Activity.values())
			if (activity != PostmanEntity.Activity.STUDY_MAP && activities.containsKey(activity))
				activities.get(activity).apply(state.activityAnimations.get(activity), ageInTicks);
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values())
			if (!overridesUpperBody(gesture))
				gestures.get(gesture).apply(state.gestureAnimations.get(gesture), ageInTicks);
		hurt.apply(state.hurtAnimation, ageInTicks);
		// Studying the map takes over his head and arms, so it looks the same in the saddle as on foot
		AnimationState studying = state.activityAnimations.get(PostmanEntity.Activity.STUDY_MAP);
		if (studying.isStarted()) {
			resetBones(PostmanAnimations.STUDY_MAP);
			activities.get(PostmanEntity.Activity.STUDY_MAP).apply(studying, ageInTicks);
		}
		// A drink or a mannerism can come mid-gallop: the parts it moves drop whatever they were doing, and the rest of
		// him carries on
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values()) {
			AnimationState gestureState = state.gestureAnimations.get(gesture);
			if (overridesUpperBody(gesture) && gestureState.isStarted()) {
				resetBones(gestureAnimation(gesture));
				gestures.get(gesture).apply(gestureState, ageInTicks);
			}
		}

		Roster.Rider rider = state.rider;
		String hairPart = rider == null ? null : rider.hair().part();
		hair.forEach((name, part) -> part.visible = name.equals(hairPart));
		harmonica.visible = state.activity == PostmanEntity.Activity.HARMONICA;
		hammer.visible = state.activity == PostmanEntity.Activity.CAMP_HAMMER;
		potion.visible = state.gestureAnimations.get(PostmanEntity.Gesture.DRINK).isStarted();
		canteen.visible = state.gestureAnimations.get(PostmanEntity.Gesture.SWIG).isStarted();
		food.visible = state.gestureAnimations.get(PostmanEntity.Gesture.EAT).isStarted();
		pocketWatch.visible = state.gestureAnimations.get(PostmanEntity.Gesture.POCKET_WATCH).isStarted();
		letter.visible = state.gestureAnimations.get(PostmanEntity.Gesture.READ_LETTER).isStarted();
		boolean lighting = state.gestureAnimations.get(PostmanEntity.Gesture.LIGHT_FIRE).isStarted();
		striker.visible = lighting;
		flint.visible = lighting;
		map.visible = state.activity == PostmanEntity.Activity.STUDY_MAP;
		satchelFlat.visible = !state.carrying;
		satchelFull.visible = state.carrying;
	}

	/** Puts back the parts an animation moves, so it takes them over rather than adding to what they were doing. */
	private void resetBones(AnimationDefinition animation) {
		for (String bone : animation.boneAnimations().keySet()) {
			ModelPart part = bones.apply(bone);
			if (part != null)
				part.resetPose();
		}
	}

	/** The animation each activity plays, or null for none (just standing about). */
	@Nullable
	public static AnimationDefinition activityAnimation(PostmanEntity.Activity activity) {
		return switch (activity) {
			case NONE -> null;
			case CAMP_HAMMER -> PostmanAnimations.CAMP_HAMMER;
			case CAMP_REST -> PostmanAnimations.CAMP_REST;
			case ROW -> PostmanAnimations.ROW;
			case HARMONICA -> PostmanAnimations.HARMONICA;
			case SLEEP -> PostmanAnimations.SLEEP;
			case STUDY_MAP -> PostmanAnimations.STUDY_MAP;
			case CLIMB -> PostmanAnimations.CLIMB;
			case DRIFT -> PostmanAnimations.DRIFT;
		};
	}

	/** His head only follows where he's looking while he's free; at work the animation keeps his eyes on the job. */
	public static boolean headTurns(PostmanEntity.Activity activity, boolean gesturing) {
		return activity == PostmanEntity.Activity.NONE && !gesturing;
	}

	/**
	 * Gestures that take over the parts they move rather than adding to what those parts are doing, so they look
	 * right on horseback or on the move. Only the work at the mailbox, the whistle and lighting the fire use his whole body.
	 */
	public static boolean overridesUpperBody(PostmanEntity.Gesture gesture) {
		return switch (gesture) {
			case PICKUP, DELIVER, WHISTLE, LIGHT_FIRE -> false;
			default -> true;
		};
	}

	public static AnimationDefinition gestureAnimation(PostmanEntity.Gesture gesture) {
		return switch (gesture) {
			case PICKUP -> PostmanAnimations.PICKUP;
			case DELIVER -> PostmanAnimations.DELIVER;
			case WHISTLE -> PostmanAnimations.WHISTLE;
			case WAVE -> PostmanAnimations.WAVE;
			case DRINK -> PostmanAnimations.DRINK;
			case LIGHT_FIRE -> PostmanAnimations.LIGHT_FIRE;
			case WIPE_BROW -> PostmanAnimations.WIPE_BROW;
			case TIP_HAT -> PostmanAnimations.TIP_HAT;
			case PAT_HORSE -> PostmanAnimations.PAT_HORSE;
			case CHECK_SATCHEL -> PostmanAnimations.CHECK_SATCHEL;
			case STRETCH -> PostmanAnimations.STRETCH;
			case YAWN -> PostmanAnimations.YAWN;
			case LOOK_BACK -> PostmanAnimations.LOOK_BACK;
			case SCRATCH_HEAD -> PostmanAnimations.SCRATCH_HEAD;
			case SWAT_FLY -> PostmanAnimations.SWAT_FLY;
			case ADJUST_HAT -> PostmanAnimations.ADJUST_HAT;
			case PULL_HAT_DOWN -> PostmanAnimations.PULL_HAT_DOWN;
			case SHADE_EYES -> PostmanAnimations.SHADE_EYES;
			case SHIVER -> PostmanAnimations.SHIVER;
			case SWIG -> PostmanAnimations.SWIG;
			case POCKET_WATCH -> PostmanAnimations.POCKET_WATCH;
			case READ_LETTER -> PostmanAnimations.READ_LETTER;
			case HUM -> PostmanAnimations.HUM;
			case NECK_ROLL -> PostmanAnimations.NECK_ROLL;
			case CRACK_KNUCKLES -> PostmanAnimations.CRACK_KNUCKLES;
			case EAT -> PostmanAnimations.EAT;
		};
	}
}
