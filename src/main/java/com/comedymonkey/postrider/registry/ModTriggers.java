package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.advancement.PostEventTrigger;

import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The advancement trigger and the statistic the post keeps. */
public final class ModTriggers {
	public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, PostRider.MOD_ID);
	public static final DeferredRegister<Identifier> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, PostRider.MOD_ID);

	public static final DeferredHolder<CriterionTrigger<?>, PostEventTrigger> POST_EVENT = TRIGGERS.register("post_event", PostEventTrigger::new);
	/** Letters sent on their own, shown in the statistics screen and counted for "Pen Pal". */
	public static final DeferredHolder<Identifier, Identifier> LETTERS_SENT = STATS.register("letters_sent",
		() -> PostRider.asResource("letters_sent"));

	private ModTriggers() {}

	/** Makes the statistic show in the statistics screen; once the registries are ready. */
	public static void registerStatFormats() {
		Stats.CUSTOM.get(LETTERS_SENT.get(), StatFormatter.DEFAULT);
	}
}
