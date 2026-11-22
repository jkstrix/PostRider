package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.hopper.IceHopperEntity;
import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.comedymonkey.postrider.content.postman.TentEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.Avatar;
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
			.vehicleAttachment(Avatar.DEFAULT_VEHICLE_ATTACHMENT)
			.clientTrackingRange(10)
			.build(key("postman")));

	public static final DeferredHolder<EntityType<?>, EntityType<PostmanHorse>> POSTMAN_HORSE = ENTITIES.register("postman_horse",
		() -> EntityType.Builder.of(PostmanHorse::new, MobCategory.MISC)
			.sized(1.3964844F, 1.6F)
			.eyeHeight(1.52F)
			.passengerAttachments(1.31875F)
			.clientTrackingRange(10)
			.build(key("postman_horse")));

	public static final DeferredHolder<EntityType<?>, EntityType<TentEntity>> TENT = ENTITIES.register("tent",
		() -> EntityType.Builder.<TentEntity>of(TentEntity::new, MobCategory.MISC)
			.sized(1.75F, 1.3F)
			.fireImmune()
			.noSummon()
			.clientTrackingRange(10)
			.build(key("tent")));

	/** The ice hopper, a player's boat that hops from the water onto sea ice: sized like a vanilla boat. */
	public static final DeferredHolder<EntityType<?>, EntityType<IceHopperEntity>> ICE_HOPPER = ENTITIES.register("ice_hopper",
		() -> EntityType.Builder.<IceHopperEntity>of(IceHopperEntity::new, MobCategory.MISC)
			.sized(1.375F, 0.5625F)
			.eyeHeight(0.5625F)
			.clientTrackingRange(10)
			.build(key("ice_hopper")));

	public static final DeferredHolder<EntityType<?>, EntityType<BargeEntity>> BARGE = ENTITIES.register("barge",
		() -> EntityType.Builder.<BargeEntity>of(BargeEntity::new, MobCategory.MISC)
			.sized((float) BargeEntity.BOX_WIDTH, (float) (BargeEntity.HULL_DEPTH + BargeEntity.RAILING_HEIGHT))
			.fireImmune()
			.noSummon()
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(key("barge")));

	private ModEntities() {}

	private static ResourceKey<EntityType<?>> key(String name) {
		return ResourceKey.create(Registries.ENTITY_TYPE, PostRider.asResource(name));
	}

	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(POSTMAN.get(), PostmanEntity.createAttributes().build());
		event.put(POSTMAN_HORSE.get(), AbstractHorse.createBaseHorseAttributes().add(Attributes.FOLLOW_RANGE, PostmanHorse.FOLLOW_RANGE).build());
	}
}
