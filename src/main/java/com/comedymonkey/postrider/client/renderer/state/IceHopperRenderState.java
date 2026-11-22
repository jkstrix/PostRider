package com.comedymonkey.postrider.client.renderer.state;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * What the ice hopper's model needs for a frame: which way it's heading, how it's rocking from a hit, how high the
 * hull rides, and where each oar is in its stroke.
 */
public class IceHopperRenderState extends EntityRenderState {
	public float yRot;
	public float hurtTime;
	public int hurtDir;
	public float damageTime;
	public float hullLift;
	public float rowingTimeLeft;
	public float rowingTimeRight;
	public boolean rowingLeft;
	public boolean rowingRight;
}
