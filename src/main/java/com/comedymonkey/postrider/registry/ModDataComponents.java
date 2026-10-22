package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.letter.LetterDraft;
import com.comedymonkey.postrider.content.parcel.ParcelLabel;
import com.comedymonkey.postrider.post.JourneyNotes;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
	public static final DeferredRegister.DataComponents COMPONENTS =
		DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PostRider.MOD_ID);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<LetterDraft>> LETTER_DRAFT =
		COMPONENTS.registerComponentType("letter_draft", b -> b.persistent(LetterDraft.CODEC).networkSynchronized(LetterDraft.STREAM_CODEC));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<LetterContent>> LETTER =
		COMPONENTS.registerComponentType("letter", b -> b.persistent(LetterContent.CODEC).networkSynchronized(LetterContent.STREAM_CODEC));
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<ParcelLabel>> PARCEL_LABEL =
		COMPONENTS.registerComponentType("parcel_label", b -> b.persistent(ParcelLabel.CODEC).networkSynchronized(ParcelLabel.STREAM_CODEC));

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<JourneyNotes>> JOURNEY_NOTES =
		COMPONENTS.registerComponentType("journey_notes", b -> b.persistent(JourneyNotes.CODEC).networkSynchronized(JourneyNotes.STREAM_CODEC));

	private ModDataComponents() {}
}
