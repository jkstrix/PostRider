package com.comedymonkey.ponyexpress.client.renderer;

import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.client.model.MochilaModel;
import com.comedymonkey.ponyexpress.client.model.PostmanHorseModel;
import com.comedymonkey.ponyexpress.content.postman.PostmanHorse;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Markings;
import net.minecraft.world.entity.animal.horse.Variant;

/** Draws the rider's horse like a vanilla horse, coat and markings included, with the mochila on top. */
public class PostmanHorseRenderer extends AbstractHorseRenderer<PostmanHorse, PostmanHorseModel> {
	private static final Map<Variant, ResourceLocation> COATS = new EnumMap<>(Variant.class);
	private static final Map<Markings, ResourceLocation> MARKINGS = new EnumMap<>(Markings.class);
	private static final ResourceLocation MOCHILA = PonyExpress.asResource("textures/entity/mochila.png");

	static {
		COATS.put(Variant.WHITE, horse("horse_white"));
		COATS.put(Variant.CREAMY, horse("horse_creamy"));
		COATS.put(Variant.CHESTNUT, horse("horse_chestnut"));
		COATS.put(Variant.BROWN, horse("horse_brown"));
		COATS.put(Variant.BLACK, horse("horse_black"));
		COATS.put(Variant.GRAY, horse("horse_gray"));
		COATS.put(Variant.DARK_BROWN, horse("horse_darkbrown"));
		MARKINGS.put(Markings.WHITE, horse("horse_markings_white"));
		MARKINGS.put(Markings.WHITE_FIELD, horse("horse_markings_whitefield"));
		MARKINGS.put(Markings.WHITE_DOTS, horse("horse_markings_whitedots"));
		MARKINGS.put(Markings.BLACK_DOTS, horse("horse_markings_blackdots"));
	}

	private static ResourceLocation horse(String name) {
		return ResourceLocation.withDefaultNamespace("textures/entity/horse/" + name + ".png");
	}

	public PostmanHorseRenderer(EntityRendererProvider.Context context) {
		super(context, new PostmanHorseModel(context.bakeLayer(ModelLayers.HORSE)), 1.1F);
		addLayer(new MarkingsLayer(this));
		addLayer(new MochilaLayer(this, new MochilaModel(context.bakeLayer(MochilaModel.LAYER))));
	}

	@Override
	public ResourceLocation getTextureLocation(PostmanHorse horse) {
		return COATS.get(horse.getVariant());
	}

	/** The coat markings, drawn the way vanilla draws them over the coat. */
	private static class MarkingsLayer extends RenderLayer<PostmanHorse, PostmanHorseModel> {
		MarkingsLayer(RenderLayerParent<PostmanHorse, PostmanHorseModel> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, PostmanHorse horse, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			ResourceLocation texture = MARKINGS.get(horse.getMarkings());
			if (texture == null || horse.isInvisible())
				return;
			getParentModel().renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(texture)), light,
				LivingEntityRenderer.getOverlayCoords(horse, 0.0F));
		}
	}

	/** The mochila, following the horse's body as it walks, rears and grazes. */
	private static class MochilaLayer extends RenderLayer<PostmanHorse, PostmanHorseModel> {
		private final MochilaModel mochila;

		MochilaLayer(RenderLayerParent<PostmanHorse, PostmanHorseModel> parent, MochilaModel mochila) {
			super(parent);
			this.mochila = mochila;
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, PostmanHorse horse, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (horse.isInvisible())
				return;
			mochila.followBody(getParentModel().body());
			mochila.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(MOCHILA)), light,
				LivingEntityRenderer.getOverlayCoords(horse, 0.0F));
		}
	}
}
