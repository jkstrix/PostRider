package com.comedymonkey.ponyexpress.client.renderer;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.client.model.PostmanModel;
import com.comedymonkey.ponyexpress.content.postman.PostmanEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PostmanRenderer extends MobRenderer<PostmanEntity, PostmanModel> {
	private static final ResourceLocation TEXTURE = PonyExpress.asResource("textures/entity/postman.png");

	public PostmanRenderer(EntityRendererProvider.Context context) {
		super(context, new PostmanModel(context.bakeLayer(PostmanModel.LAYER)), 0.5F);
	}

	@Override
	public ResourceLocation getTextureLocation(PostmanEntity entity) {
		return TEXTURE;
	}
}
