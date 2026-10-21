package com.comedymonkey.ponyexpress.content.parcel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The label on a parcel: the address it came from and went to, who sent it, and the day it was posted. */
public record ParcelLabel(String from, String to, String sender, long day) {
	public static final Codec<ParcelLabel> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("from").forGetter(ParcelLabel::from),
		Codec.STRING.fieldOf("to").forGetter(ParcelLabel::to),
		Codec.STRING.fieldOf("sender").forGetter(ParcelLabel::sender),
		Codec.LONG.fieldOf("day").forGetter(ParcelLabel::day)
	).apply(i, ParcelLabel::new));
	public static final StreamCodec<ByteBuf, ParcelLabel> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, ParcelLabel::from,
		ByteBufCodecs.STRING_UTF8, ParcelLabel::to,
		ByteBufCodecs.STRING_UTF8, ParcelLabel::sender,
		ByteBufCodecs.VAR_LONG, ParcelLabel::day,
		ParcelLabel::new);
}
