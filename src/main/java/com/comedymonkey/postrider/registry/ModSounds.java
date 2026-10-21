package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, PostRider.MOD_ID);

	/** The tune the rider plays outside his tent of an evening (see CREDITS.md). */
	public static final DeferredHolder<SoundEvent, SoundEvent> HARMONICA =
		SOUNDS.register("harmonica", () -> SoundEvent.createVariableRangeEvent(PostRider.asResource("harmonica")));

	private ModSounds() {}
}
