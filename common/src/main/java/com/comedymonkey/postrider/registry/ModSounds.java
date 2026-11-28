package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final Registrar<SoundEvent> SOUNDS = Registrar.create(Registries.SOUND_EVENT);

	/** The tune the rider plays outside his tent of an evening (see CREDITS.md). */
	public static final RegistryEntry<SoundEvent, SoundEvent> HARMONICA =
		SOUNDS.register("harmonica", () -> SoundEvent.createVariableRangeEvent(PostRider.asResource("harmonica")));

	private ModSounds() {}
}
