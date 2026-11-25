package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.TentModel;
import com.comedymonkey.postrider.content.postman.TentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws the tent like a mob model: flipped the way entity models are, and turned to face the way it was pitched. */
public class TentRenderer extends EntityRenderer<TentEntity> {
	private static final ResourceLocation TEXTURE = PostRider.asResource("textures/entity/tent.png");

	private final TentModel model;

	public TentRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new TentModel(context.bakeLayer(TentModel.LAYER));
		this.shadowRadius = 0.0F;
	}

	@Override
	public void render(TentEntity tent, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - tent.getYRot()));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		super.render(tent, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(TentEntity tent) {
		return TEXTURE;
	}
}
