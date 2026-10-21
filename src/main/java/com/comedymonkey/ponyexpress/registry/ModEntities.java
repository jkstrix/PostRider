package com.comedymonkey.ponyexpress.registry;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.content.postman.PostmanEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
	public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, PonyExpress.MOD_ID);

	public static final DeferredHolder<EntityType<?>, EntityType<PostmanEntity>> POSTMAN = ENTITIES.register("postman",
		() -> EntityType.Builder.of(PostmanEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.clientTrackingRange(10)
			.build(PonyExpress.asResource("postman").toString()));

	private ModEntities() {}

	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(POSTMAN.get(), PostmanEntity.createAttributes().build());
	}
}
