package com.comedymonkey.postrider.network;

import java.util.List;
import java.util.UUID;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.post.Mail;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.post.PostOfficeHours;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: send what's in the open mailbox's parcel slots to an address. */
public record SendParcelPayload(String to) implements CustomPacketPayload {
	public static final Type<SendParcelPayload> TYPE = new Type<>(PostRider.asResource("send_parcel"));
	public static final StreamCodec<ByteBuf, SendParcelPayload> STREAM_CODEC =
		ByteBufCodecs.stringUtf8(64).map(SendParcelPayload::new, SendParcelPayload::to);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SendParcelPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)
					|| !(player.containerMenu instanceof MailboxMenu menu) || !menu.stillValid(player))
				return;
			MailboxBlockEntity mailbox = menu.getMailbox();
			if (mailbox == null || mailbox.getLevel() == null)
				return;
			Component problem = send(player, mailbox, payload.to());
			if (problem != null)
				player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
		});
	}

	/** Queues the parcel for a rider, or returns why it can't be sent. */
	private static Component send(ServerPlayer player, MailboxBlockEntity mailbox, String to) {
		if (!mailbox.hasName())
			return Component.translatable("message.postrider.mailbox.unnamed");
		if (mailbox.isParcelEmpty())
			return Component.translatable("message.postrider.mailbox.empty_parcel");
		MailboxDirectory.Entry target = MailboxDirectory.get(player.server).find(to);
		GlobalPos here = GlobalPos.of(mailbox.getLevel().dimension(), mailbox.getBlockPos());
		if (target == null || target.pos().equals(here))
			return Component.translatable("message.postrider.mailbox.unknown_address", to);
		if (!target.pos().dimension().equals(here.dimension()))
			return Component.translatable("message.postrider.mailbox.other_dimension", target.name());

		List<ItemStack> items = mailbox.takeParcelContents();
		PostOffice.get(player.server).enqueue(new PendingDelivery(UUID.randomUUID(), player.getUUID(),
			player.getGameProfile().getName(), mailbox.getName(), here, target.name(), items, player.serverLevel().getGameTime()));
		String key = Mail.isLetterOnly(items) ? "message.postrider.mailbox.queued_letter" : "message.postrider.mailbox.queued";
		if (!PostOfficeHours.isOpen(player.serverLevel().getDayTime()))
			key += "_night";
		player.displayClientMessage(Component.translatable(key, target.name()).withStyle(ChatFormatting.DARK_GREEN), true);
		return null;
	}
}
