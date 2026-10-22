package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.BargeModel;
import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the barge like a mob model (flipped the way entity models are, turned the way it's heading), rowing or idling,
 * with the lantern's flame drawn again at full brightness so it glows at night.
 */
public class BargeRenderer extends EntityRenderer<BargeEntity> {
	private static final ResourceLocation TEXTURE = PostRider.asResource("textures/entity/barge.png");

	private final BargeModel model;

	public BargeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new BargeModel(context.bakeLayer(BargeModel.LAYER));
		this.shadowRadius = 0.0F;
	}

	@Override
	public void render(BargeEntity barge, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		// The model is drawn from the deck; the barge's position is the bottom of the hull, below it
		poseStack.translate(0.0F, -1.501F - (float) BargeEntity.HULL_DEPTH, 0.0F);
		model.setupAnim(barge, 0, 0, barge.tickCount + partialTick, 0, 0);
		RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
		model.hideGlow(true);
		model.renderToBuffer(poseStack, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
		model.renderGlow(poseStack, buffers.getBuffer(type), OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		super.render(barge, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(BargeEntity barge) {
		return TEXTURE;
	}
}
