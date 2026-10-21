package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.PostmanModel;
import com.comedymonkey.postrider.content.postman.PostmanEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PostmanRenderer extends MobRenderer<PostmanEntity, PostmanModel> {
	private static final ResourceLocation TEXTURE = PostRider.asResource("textures/entity/postman.png");

	public PostmanRenderer(EntityRendererProvider.Context context) {
		super(context, new PostmanModel(context.bakeLayer(PostmanModel.LAYER)), 0.5F);
	}

	@Override
	public ResourceLocation getTextureLocation(PostmanEntity entity) {
		return TEXTURE;
	}
}
