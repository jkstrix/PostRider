package com.comedymonkey.postrider.client.renderer;

import java.util.EnumMap;
import java.util.Map;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.model.MochilaModel;
import com.comedymonkey.postrider.client.model.PostmanHorseModel;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.animal.equine.EquineSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.equine.Variant;

/**
 * Draws the rider's horse like a vanilla horse, coat, markings and saddle included, with the mochila on top. Like
 * vanilla's, the horse is drawn a tenth bigger than its mesh (the scaling is in the baked horse layer, and the mochila's).
 */
public class PostmanHorseRenderer extends AbstractHorseRenderer<PostmanHorse, HorseRenderState, PostmanHorseModel> {
	private static final Map<Variant, Identifier> COATS = new EnumMap<>(Variant.class);
	private static final Map<Markings, Identifier> MARKINGS = new EnumMap<>(Markings.class);
	private static final Identifier MOCHILA = PostRider.asResource("textures/entity/mochila.png");

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

	private static Identifier horse(String name) {
		return Identifier.withDefaultNamespace("textures/entity/horse/" + name + ".png");
	}

	public PostmanHorseRenderer(EntityRendererProvider.Context context) {
		this(context, new PostmanHorseModel(context.bakeLayer(ModelLayers.HORSE)));
	}

	/** The riders' horses are never foals, so the grown horse stands in for the foal's model too. */
	private PostmanHorseRenderer(EntityRendererProvider.Context context, PostmanHorseModel model) {
		super(context, model, model);
		addLayer(new MarkingsLayer(this));
		// The saddle was part of the horse's own model before; vanilla now draws it as worn equipment
		addLayer(new SimpleEquipmentLayer<>(this, context.getEquipmentRenderer(), EquipmentClientInfo.LayerType.HORSE_SADDLE,
			state -> state.saddle, new EquineSaddleModel(context.bakeLayer(ModelLayers.HORSE_SADDLE)), null, 2));
		addLayer(new MochilaLayer(this, new MochilaModel(context.bakeLayer(MochilaModel.LAYER),
			new PostmanHorseModel(context.bakeLayer(ModelLayers.HORSE)))));
	}

	@Override
	public HorseRenderState createRenderState() {
		return new HorseRenderState();
	}

	@Override
	public void extractRenderState(PostmanHorse horse, HorseRenderState state, float partialTick) {
		super.extractRenderState(horse, state, partialTick);
		state.variant = horse.getVariant();
		state.markings = horse.getMarkings();
	}

	@Override
	public Identifier getTextureLocation(HorseRenderState state) {
		return COATS.get(state.variant);
	}

	/** The coat markings, drawn the way vanilla draws them over the coat. */
	private static class MarkingsLayer extends RenderLayer<HorseRenderState, PostmanHorseModel> {
		MarkingsLayer(RenderLayerParent<HorseRenderState, PostmanHorseModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, HorseRenderState state, float yRot, float xRot) {
			Identifier texture = MARKINGS.get(state.markings);
			if (texture == null || state.isInvisible)
				return;
			submitNodeCollector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityTranslucent(texture), light,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		}
	}

	/** The mochila, following the horse's body as it walks, rears and grazes. */
	private static class MochilaLayer extends RenderLayer<HorseRenderState, PostmanHorseModel> {
		private final MochilaModel mochila;

		MochilaLayer(RenderLayerParent<HorseRenderState, PostmanHorseModel> parent, MochilaModel mochila) {
			super(parent);
			this.mochila = mochila;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, HorseRenderState state, float yRot, float xRot) {
			if (state.isInvisible)
				return;
			submitNodeCollector.order(3).submitModel(mochila, state, poseStack, RenderTypes.entityCutout(MOCHILA), light,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		}
	}
}
