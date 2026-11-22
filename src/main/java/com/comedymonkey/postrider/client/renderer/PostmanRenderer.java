package com.comedymonkey.postrider.client.renderer;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.PostmanModel;
import com.comedymonkey.postrider.client.renderer.state.PostmanRenderState;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/** Draws each rider with their own face, on the regular build or (for the women of the crew) the slim one. */
public class PostmanRenderer extends MobRenderer<PostmanEntity, PostmanRenderState, PostmanModel> {
	/** The plain uniform, for a rider who isn't one of the crew. */
	private static final Identifier TEXTURE = PostRider.asResource("textures/entity/postman.png");

	private final PostmanModel regular;
	private final PostmanModel slim;

	public PostmanRenderer(EntityRendererProvider.Context context) {
		super(context, new PostmanModel(context.bakeLayer(PostmanModel.LAYER)), 0.5F);
		this.regular = model;
		this.slim = new PostmanModel(context.bakeLayer(PostmanModel.SLIM_LAYER));
	}

	@Override
	public PostmanRenderState createRenderState() {
		return new PostmanRenderState();
	}

	@Override
	public void extractRenderState(PostmanEntity entity, PostmanRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.copyFrom(entity);
	}

	@Override
	public void submit(PostmanRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		Roster.Rider rider = state.rider;
		model = rider != null && rider.slim() ? slim : regular;
		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	@Override
	public Identifier getTextureLocation(PostmanRenderState state) {
		Roster.Rider rider = state.rider;
		return rider == null ? TEXTURE : PostRider.asResource(rider.texture());
	}
}
