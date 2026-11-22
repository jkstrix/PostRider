package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.BargeModel;
import com.comedymonkey.postrider.client.renderer.state.BargeRenderState;
import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws the barge like a mob model (flipped the way entity models are, turned the way it's heading), rowing or idling,
 * with the lantern's flame drawn again at full brightness so it glows at night.
 */
public class BargeRenderer extends EntityRenderer<BargeEntity, BargeRenderState> {
	private static final Identifier TEXTURE = PostRider.asResource("textures/entity/barge.png");

	private final BargeModel model;
	private final BargeModel glow;

	public BargeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new BargeModel(context.bakeLayer(BargeModel.LAYER), false);
		this.glow = new BargeModel(context.bakeLayer(BargeModel.LAYER), true);
		this.shadowRadius = 0.0F;
	}

	/** It's culled by its own box, which takes in the whole hull and the lantern post. */
	@Override
	protected AABB getBoundingBoxForCulling(BargeEntity barge, float partialTick) {
		return barge.getBoundingBoxForCulling();
	}

	@Override
	public BargeRenderState createRenderState() {
		return new BargeRenderState();
	}

	@Override
	public void extractRenderState(BargeEntity barge, BargeRenderState state, float partialTick) {
		super.extractRenderState(barge, state, partialTick);
		state.yRot = Mth.lerp(partialTick, barge.yRotO, barge.getYRot());
		state.rowAnimation.copyFrom(barge.rowAnimation);
		state.idleAnimation.copyFrom(barge.idleAnimation);
	}

	@Override
	public void submit(BargeRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		// The model is drawn from the deck; the barge's position is the bottom of the hull, below it
		poseStack.translate(0.0F, -1.501F - (float) BargeEntity.HULL_DEPTH, 0.0F);
		RenderType type = RenderTypes.entityCutout(TEXTURE);
		submitNodeCollector.submitModel(model, state, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		submitNodeCollector.submitModel(glow, state, poseStack, type, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
