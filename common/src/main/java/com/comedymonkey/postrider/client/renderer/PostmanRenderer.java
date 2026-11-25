package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.PostmanModel;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws each rider with their own face, on the regular build or (for the women of the crew) the slim one. */
public class PostmanRenderer extends MobRenderer<PostmanEntity, PostmanModel> {
	/** The plain uniform, for a rider who isn't one of the crew. */
	private static final ResourceLocation TEXTURE = PostRider.asResource("textures/entity/postman.png");

	private final PostmanModel regular;
	private final PostmanModel slim;

	public PostmanRenderer(EntityRendererProvider.Context context) {
		super(context, new PostmanModel(context.bakeLayer(PostmanModel.LAYER)), 0.5F);
		this.regular = model;
		this.slim = new PostmanModel(context.bakeLayer(PostmanModel.SLIM_LAYER));
	}

	@Override
	public void render(PostmanEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		Roster.Rider rider = entity.getRider();
		model = rider != null && rider.slim() ? slim : regular;
		super.render(entity, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(PostmanEntity entity) {
		Roster.Rider rider = entity.getRider();
		return rider == null ? TEXTURE : PostRider.asResource(rider.texture());
	}
}
