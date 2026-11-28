package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.letter.LetterDraft;
import com.comedymonkey.postrider.content.parcel.ParcelLabel;
import com.comedymonkey.postrider.post.JourneyNotes;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

public final class ModDataComponents {
	public static final Registrar<DataComponentType<?>> COMPONENTS = Registrar.create(Registries.DATA_COMPONENT_TYPE);

	public static final RegistryEntry<DataComponentType<?>, DataComponentType<LetterDraft>> LETTER_DRAFT =
		COMPONENTS.registerComponentType("letter_draft", b -> b.persistent(LetterDraft.CODEC).networkSynchronized(LetterDraft.STREAM_CODEC));
	public static final RegistryEntry<DataComponentType<?>, DataComponentType<LetterContent>> LETTER =
		COMPONENTS.registerComponentType("letter", b -> b.persistent(LetterContent.CODEC).networkSynchronized(LetterContent.STREAM_CODEC));
	public static final RegistryEntry<DataComponentType<?>, DataComponentType<ParcelLabel>> PARCEL_LABEL =
		COMPONENTS.registerComponentType("parcel_label", b -> b.persistent(ParcelLabel.CODEC).networkSynchronized(ParcelLabel.STREAM_CODEC));

	public static final RegistryEntry<DataComponentType<?>, DataComponentType<JourneyNotes>> JOURNEY_NOTES =
		COMPONENTS.registerComponentType("journey_notes", b -> b.persistent(JourneyNotes.CODEC).networkSynchronized(JourneyNotes.STREAM_CODEC));

	private ModDataComponents() {}
}
