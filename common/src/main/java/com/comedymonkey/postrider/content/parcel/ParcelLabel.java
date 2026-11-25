package com.comedymonkey.postrider.content.parcel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The label on a parcel: the address it came from and went to, who sent it, the day it was posted and how many game
 * ticks it took to arrive (0 if unknown).
 */
public record ParcelLabel(String from, String to, String sender, long day, long travelTicks) {
	public static final Codec<ParcelLabel> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("from").forGetter(ParcelLabel::from),
		Codec.STRING.fieldOf("to").forGetter(ParcelLabel::to),
		Codec.STRING.fieldOf("sender").forGetter(ParcelLabel::sender),
		Codec.LONG.fieldOf("day").forGetter(ParcelLabel::day),
		Codec.LONG.optionalFieldOf("travel_ticks", 0L).forGetter(ParcelLabel::travelTicks)
	).apply(i, ParcelLabel::new));
	public static final StreamCodec<ByteBuf, ParcelLabel> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, ParcelLabel::from,
		ByteBufCodecs.STRING_UTF8, ParcelLabel::to,
		ByteBufCodecs.STRING_UTF8, ParcelLabel::sender,
		ByteBufCodecs.VAR_LONG, ParcelLabel::day,
		ByteBufCodecs.VAR_LONG, ParcelLabel::travelTicks,
		ParcelLabel::new);
}
