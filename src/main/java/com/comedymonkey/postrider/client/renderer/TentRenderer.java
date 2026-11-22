package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.TentModel;
import com.comedymonkey.postrider.client.renderer.state.TentRenderState;
import com.comedymonkey.postrider.content.postman.TentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws the tent like a mob model: flipped the way entity models are, and turned to face the way it was pitched. */
public class TentRenderer extends EntityRenderer<TentEntity, TentRenderState> {
	private static final Identifier TEXTURE = PostRider.asResource("textures/entity/tent.png");

	private final TentModel model;

	public TentRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new TentModel(context.bakeLayer(TentModel.LAYER));
		this.shadowRadius = 0.0F;
	}

	@Override
	public TentRenderState createRenderState() {
		return new TentRenderState();
	}

	@Override
	public void extractRenderState(TentEntity tent, TentRenderState state, float partialTick) {
		super.extractRenderState(tent, state, partialTick);
		state.yRot = tent.getYRot();
	}

	@Override
	public void submit(TentRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		submitNodeCollector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}
}
