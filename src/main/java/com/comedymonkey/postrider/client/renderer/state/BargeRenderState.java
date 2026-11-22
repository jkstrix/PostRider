package com.comedymonkey.postrider.client.renderer.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** What the barge's model needs for a frame: which way it's heading, and its rowing and idling animations. */
public class BargeRenderState extends EntityRenderState {
	public float yRot;
	public final AnimationState rowAnimation = new AnimationState();
	public final AnimationState idleAnimation = new AnimationState();
}
