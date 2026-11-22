package com.comedymonkey.postrider.network;

import java.util.List;
import java.util.Optional;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.letter.LetterContent;
import com.comedymonkey.postrider.content.letter.LetterDraft;
import com.comedymonkey.postrider.registry.ModDataComponents;
import com.comedymonkey.postrider.registry.ModItems;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: save the pages of the blank letter in a slot as a draft, or seal it into a Written Letter. */
public record EditLetterPayload(int slot, List<String> pages, boolean seal) implements CustomPacketPayload {
	public static final Type<EditLetterPayload> TYPE = new Type<>(PostRider.asResource("edit_letter"));
	public static final StreamCodec<ByteBuf, EditLetterPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, EditLetterPayload::slot,
		ByteBufCodecs.stringUtf8(LetterDraft.MAX_PAGE_LENGTH * 4).apply(ByteBufCodecs.list(LetterDraft.MAX_PAGES)), EditLetterPayload::pages,
		ByteBufCodecs.BOOL, EditLetterPayload::seal,
		EditLetterPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** Only the hotbar and the offhand, where a letter can be held while writing. */
	public static boolean isWritableSlot(int slot) {
		return Inventory.isHotbarSlot(slot) || slot == Inventory.SLOT_OFFHAND;
	}

	public static void handle(EditLetterPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !isWritableSlot(payload.slot()))
				return;
			ItemStack held = player.getInventory().getItem(payload.slot());
			if (!held.is(ModItems.LETTER.get()))
				return;
			List<String> pages = LetterDraft.sanitize(payload.pages());
			if (payload.seal() && pages.stream().allMatch(String::isBlank)) {
				player.sendOverlayMessage(Component.translatable("message.postrider.letter.empty").withStyle(ChatFormatting.RED));
				return;
			}
			ItemStack result;
			if (payload.seal()) {
				result = new ItemStack(ModItems.WRITTEN_LETTER.get());
				result.set(ModDataComponents.LETTER.get(), new LetterContent(pages, player.getGameProfile().name(), Optional.empty()));
				player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1, 1.2F);
			} else {
				result = held.copyWithCount(1);
				if (pages.isEmpty())
					result.remove(ModDataComponents.LETTER_DRAFT.get());
				else
					result.set(ModDataComponents.LETTER_DRAFT.get(), new LetterDraft(pages));
			}
			// A stack of blank letters gives up just the one that was written on
			if (held.getCount() > 1) {
				held.shrink(1);
				player.getInventory().placeItemBackInInventory(result, Prediction.SERVER_ONLY);
			} else {
				player.getInventory().setItem(payload.slot(), result);
			}
		});
	}
}
