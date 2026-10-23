package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.comedymonkey.postrider.content.postman.TentEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
	public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, PostRider.MOD_ID);

	public static final DeferredHolder<EntityType<?>, EntityType<PostmanEntity>> POSTMAN = ENTITIES.register("postman",
		() -> EntityType.Builder.of(PostmanEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			// Sits in a saddle as low as a player does
			.vehicleAttachment(Player.DEFAULT_VEHICLE_ATTACHMENT)
			.clientTrackingRange(10)
			.build(PostRider.asResource("postman").toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<PostmanHorse>> POSTMAN_HORSE = ENTITIES.register("postman_horse",
		() -> EntityType.Builder.of(PostmanHorse::new, MobCategory.MISC)
			.sized(1.3964844F, 1.6F)
			.eyeHeight(1.52F)
			.passengerAttachments(1.31875F)
			.clientTrackingRange(10)
			.build(PostRider.asResource("postman_horse").toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<TentEntity>> TENT = ENTITIES.register("tent",
		() -> EntityType.Builder.<TentEntity>of(TentEntity::new, MobCategory.MISC)
			.sized(1.75F, 1.3F)
			.fireImmune()
			.noSummon()
			.clientTrackingRange(10)
			.build(PostRider.asResource("tent").toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<BargeEntity>> BARGE = ENTITIES.register("barge",
		() -> EntityType.Builder.<BargeEntity>of(BargeEntity::new, MobCategory.MISC)
			.sized((float) BargeEntity.BOX_WIDTH, (float) (BargeEntity.HULL_DEPTH + BargeEntity.RAILING_HEIGHT))
			.fireImmune()
			.noSummon()
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(PostRider.asResource("barge").toString()));

	private ModEntities() {}

	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(POSTMAN.get(), PostmanEntity.createAttributes().build());
		event.put(POSTMAN_HORSE.get(), AbstractHorse.createBaseHorseAttributes().add(Attributes.FOLLOW_RANGE, PostmanHorse.FOLLOW_RANGE).build());
	}
}
