package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.IceHopperModel;
import com.comedymonkey.postrider.content.hopper.IceHopperEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the ice hopper like a boat: turned the way it's heading, rocking when hit, oars swinging as it's rowed. */
public class IceHopperRenderer extends EntityRenderer<IceHopperEntity> {
	private static final ResourceLocation TEXTURE = PostRider.asResource("textures/entity/ice_hopper.png");

	private final IceHopperModel model;

	public IceHopperRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new IceHopperModel(context.bakeLayer(IceHopperModel.LAYER));
		this.shadowRadius = 0.8F;
	}

	@Override
	public void render(IceHopperEntity hopper, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		poseStack.pushPose();
		// Afloat the hull is drawn up at the waterline; on ice it sits where it is
		poseStack.translate(0.0F, (float) hopper.hullLift(), 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
		float hurt = hopper.getHurtTime() - partialTick;
		float damage = hopper.getDamage() - partialTick;
		if (damage < 0.0F)
			damage = 0.0F;
		if (hurt > 0.0F)
			poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * hopper.getHurtDir()));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		model.setupAnim(hopper, partialTick, 0, hopper.tickCount + partialTick, 0, 0);
		model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		super.render(hopper, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(IceHopperEntity hopper) {
		return TEXTURE;
	}
}
