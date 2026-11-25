package com.comedymonkey.postrider.post;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What delivered mail went through on the way: shown on its tooltip, and the stuff of "Neither Snow nor Rain". */
public record JourneyNotes(boolean barge, int nights) {
	public static final JourneyNotes NONE = new JourneyNotes(false, 0);
	public static final Codec<JourneyNotes> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("barge", false).forGetter(JourneyNotes::barge),
		Codec.INT.optionalFieldOf("nights", 0).forGetter(JourneyNotes::nights)
	).apply(i, JourneyNotes::new));
	public static final StreamCodec<ByteBuf, JourneyNotes> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BOOL, JourneyNotes::barge,
		ByteBufCodecs.VAR_INT, JourneyNotes::nights,
		JourneyNotes::new);

	/** Whether it had a hard road: across water by barge, or a night out on the trail. */
	public boolean weathered() {
		return barge || nights > 0;
	}

	/** The tooltip lines: "Crossed water by barge", "Spent 2 nights on the trail". */
	public void addTo(List<Component> tooltip) {
		if (barge)
			tooltip.add(Component.translatable("item.postrider.journey.barge").withStyle(ChatFormatting.DARK_AQUA));
		if (nights == 1)
			tooltip.add(Component.translatable("item.postrider.journey.night").withStyle(ChatFormatting.DARK_AQUA));
		else if (nights > 1)
			tooltip.add(Component.translatable("item.postrider.journey.nights", nights).withStyle(ChatFormatting.DARK_AQUA));
	}
}
