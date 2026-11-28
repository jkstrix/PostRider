package com.comedymonkey.postrider.client.renderer.state;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** What the rider's model needs from him for a frame: his animations, what he's doing, who he is and his satchel. */
public class PostmanRenderState extends LivingEntityRenderState {
	public final AnimationState idleAnimation = new AnimationState();
	public final AnimationState rideAnimation = new AnimationState();
	public final AnimationState rideIdleAnimation = new AnimationState();
	public final AnimationState hurtAnimation = new AnimationState();
	public final Map<PostmanEntity.Activity, AnimationState> activityAnimations = new EnumMap<>(PostmanEntity.Activity.class);
	public final Map<PostmanEntity.Gesture, AnimationState> gestureAnimations = new EnumMap<>(PostmanEntity.Gesture.class);
	public PostmanEntity.Activity activity = PostmanEntity.Activity.NONE;
	public boolean gesturing;
	/** Whether he's sitting on something (his horse, a barge, a boat). */
	public boolean inVehicle;
	public boolean carrying;
	@Nullable
	public Roster.Rider rider;

	public PostmanRenderState() {
		for (PostmanEntity.Activity activity : PostmanEntity.Activity.values())
			activityAnimations.put(activity, new AnimationState());
		for (PostmanEntity.Gesture gesture : PostmanEntity.Gesture.values())
			gestureAnimations.put(gesture, new AnimationState());
	}

	/** Copies what the model draws from the rider this frame. */
	public void copyFrom(PostmanEntity entity) {
		idleAnimation.copyFrom(entity.idleAnimation);
		rideAnimation.copyFrom(entity.rideAnimation);
		rideIdleAnimation.copyFrom(entity.rideIdleAnimation);
		hurtAnimation.copyFrom(entity.hurtAnimation);
		activityAnimations.forEach((activity, state) -> state.copyFrom(entity.activityAnimations.get(activity)));
		gestureAnimations.forEach((gesture, state) -> state.copyFrom(entity.gestureAnimations.get(gesture)));
		activity = entity.getActivity();
		gesturing = entity.isGesturing();
		inVehicle = entity.getVehicle() != null;
		carrying = entity.isCarrying();
		rider = entity.getRider();
	}
}
