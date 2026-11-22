package com.comedymonkey.postrider.advancement;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The one advancement trigger for everything that happens in the post: an event (see {@link PostEvents}), which rider
 * it involved if any, and a running count for "do it ten times" advancements.
 *
 * <pre>{"trigger": "postrider:post_event", "conditions": {"event": "greeted", "rider": "chris"}}</pre>
 */
public class PostEventTrigger extends SimpleCriterionTrigger<PostEventTrigger.Instance> {
	@Override
	public Codec<Instance> codec() {
		return Instance.CODEC;
	}

	public void trigger(ServerPlayer player, String event, @Nullable String rider, int count) {
		trigger(player, instance -> instance.matches(event, rider, count));
	}

	public record Instance(Optional<Holder<LootItemCondition>> player, String event, Optional<String> rider, int atLeast)
			implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
			LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Instance::player),
			Codec.STRING.fieldOf("event").forGetter(Instance::event),
			Codec.STRING.optionalFieldOf("rider").forGetter(Instance::rider),
			Codec.INT.optionalFieldOf("at_least", 1).forGetter(Instance::atLeast)
		).apply(i, Instance::new));

		/** The same event, for the rider asked about (if any), done at least as often as asked. */
		public boolean matches(String event, @Nullable String rider, int count) {
			return this.event.equals(event) && this.rider.map(wanted -> wanted.equals(rider)).orElse(true) && count >= atLeast;
		}
	}
}
