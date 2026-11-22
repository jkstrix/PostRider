package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.IceHopperModel;
import com.comedymonkey.postrider.client.renderer.state.IceHopperRenderState;
import com.comedymonkey.postrider.content.hopper.IceHopperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/** Draws the ice hopper like a boat: turned the way it's heading, rocking when hit, oars swinging as it's rowed. */
public class IceHopperRenderer extends EntityRenderer<IceHopperEntity, IceHopperRenderState> {
	private static final Identifier TEXTURE = PostRider.asResource("textures/entity/ice_hopper.png");

	private final IceHopperModel model;

	public IceHopperRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new IceHopperModel(context.bakeLayer(IceHopperModel.LAYER));
		this.shadowRadius = 0.8F;
	}

	@Override
	public IceHopperRenderState createRenderState() {
		return new IceHopperRenderState();
	}

	@Override
	public void extractRenderState(IceHopperEntity hopper, IceHopperRenderState state, float partialTick) {
		super.extractRenderState(hopper, state, partialTick);
		state.yRot = Mth.lerp(partialTick, hopper.yRotO, hopper.getYRot());
		state.hurtTime = hopper.getHurtTime() - partialTick;
		state.hurtDir = hopper.getHurtDir();
		state.damageTime = Math.max(hopper.getDamage() - partialTick, 0.0F);
		state.hullLift = (float) hopper.hullLift();
		state.rowingTimeLeft = hopper.getRowingTime(AbstractBoat.PADDLE_LEFT, partialTick);
		state.rowingTimeRight = hopper.getRowingTime(AbstractBoat.PADDLE_RIGHT, partialTick);
		state.rowingLeft = hopper.getPaddleState(AbstractBoat.PADDLE_LEFT);
		state.rowingRight = hopper.getPaddleState(AbstractBoat.PADDLE_RIGHT);
	}

	@Override
	public void submit(IceHopperRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		// Afloat the hull is drawn up at the waterline; on ice it sits where it is
		poseStack.translate(0.0F, state.hullLift, 0.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		float hurt = state.hurtTime;
		if (hurt > 0.0F)
			poseStack.rotateDegrees(Axis.XP, Mth.sin(hurt) * hurt * state.damageTime / 10.0F * state.hurtDir);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		submitNodeCollector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
