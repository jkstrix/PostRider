package com.comedymonkey.ponyexpress.network;

import com.comedymonkey.ponyexpress.PonyExpress;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.ponyexpress.content.mailbox.MailboxMenu;
import com.comedymonkey.ponyexpress.post.MailboxDirectory;
import com.comedymonkey.ponyexpress.post.MailboxNames;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: give the open mailbox a new address. */
public record RenameMailboxPayload(String name) implements CustomPacketPayload {
	public static final Type<RenameMailboxPayload> TYPE = new Type<>(PonyExpress.asResource("rename_mailbox"));
	public static final StreamCodec<ByteBuf, RenameMailboxPayload> STREAM_CODEC =
		ByteBufCodecs.stringUtf8(64).map(RenameMailboxPayload::new, RenameMailboxPayload::name);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(RenameMailboxPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)
					|| !(player.containerMenu instanceof MailboxMenu menu) || !menu.stillValid(player))
				return;
			MailboxBlockEntity mailbox = menu.getMailbox();
			if (mailbox == null)
				return;
			String name = MailboxNames.clean(payload.name());
			MailboxNames.Problem problem = MailboxNames.check(name);
			MailboxDirectory.Result result = problem == null ? mailbox.rename(name, player) : MailboxDirectory.Result.INVALID;
			Component message = switch (result) {
				case OK -> Component.translatable("message.ponyexpress.mailbox.named", mailbox.getName()).withStyle(ChatFormatting.DARK_GREEN);
				case TAKEN -> Component.translatable("message.ponyexpress.mailbox.name_taken", name).withStyle(ChatFormatting.RED);
				case INVALID -> problemMessage(problem).withStyle(ChatFormatting.RED);
			};
			player.displayClientMessage(message, true);
			mailbox.sendInfo(player);
		});
	}

	private static net.minecraft.network.chat.MutableComponent problemMessage(MailboxNames.Problem problem) {
		if (problem == null)
			return Component.translatable("message.ponyexpress.mailbox.name_bad_character");
		return switch (problem) {
			case TOO_SHORT -> Component.translatable("message.ponyexpress.mailbox.name_too_short", MailboxNames.MIN_LENGTH);
			case TOO_LONG -> Component.translatable("message.ponyexpress.mailbox.name_too_long", MailboxNames.MAX_LENGTH);
			case BAD_CHARACTER -> Component.translatable("message.ponyexpress.mailbox.name_bad_character");
		};
	}
}
