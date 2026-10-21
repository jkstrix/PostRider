package com.comedymonkey.postrider.network;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.MailboxNames;

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
	public static final Type<RenameMailboxPayload> TYPE = new Type<>(PostRider.asResource("rename_mailbox"));
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
				case OK -> Component.translatable("message.postrider.mailbox.named", mailbox.getName()).withStyle(ChatFormatting.DARK_GREEN);
				case TAKEN -> Component.translatable("message.postrider.mailbox.name_taken", name).withStyle(ChatFormatting.RED);
				case INVALID -> MailboxNames.describe(problem != null ? problem : MailboxNames.Problem.BAD_CHARACTER).withStyle(ChatFormatting.RED);
			};
			MailboxNoticePayload.tell(player, message, result == MailboxDirectory.Result.OK);
			mailbox.sendInfo(player);
		});
	}
}
