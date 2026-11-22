package com.comedymonkey.postrider.network;

import java.util.List;
import java.util.UUID;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.PostRiderConfig;
import com.comedymonkey.postrider.advancement.PostEvents;
import com.comedymonkey.postrider.content.mailbox.MailboxBlockEntity;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.post.Mail;
import com.comedymonkey.postrider.post.MailboxDirectory;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostOffice;
import com.comedymonkey.postrider.post.PostOfficeHours;
import com.comedymonkey.postrider.registry.ModTriggers;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
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
				MailboxNoticePayload.tell(player, problem, false);
		});
	}

	/** Queues the parcel for a rider, or returns why it can't be sent. */
	private static Component send(ServerPlayer player, MailboxBlockEntity mailbox, String to) {
		if (!mailbox.hasName())
			return Component.translatable("message.postrider.mailbox.unnamed");
		if (mailbox.isParcelEmpty())
			return Component.translatable("message.postrider.mailbox.empty_parcel");
		MailboxDirectory.Entry target = MailboxDirectory.get(player.level().getServer()).find(to);
		GlobalPos here = GlobalPos.of(mailbox.getLevel().dimension(), mailbox.getBlockPos());
		if (target == null || target.pos().equals(here))
			return Component.translatable("message.postrider.mailbox.unknown_address", to);
		if (!target.pos().dimension().equals(here.dimension()))
			return Component.translatable("message.postrider.mailbox.other_dimension", target.name());

		List<ItemStack> items = mailbox.takeParcelContents();
		PostEvents.fire(player, PostEvents.SENT);
		if (Mail.isLetterOnly(items)) {
			player.awardStat(ModTriggers.LETTERS_SENT.get());
			PostEvents.fire(player, PostEvents.SENT_LETTER, null, player.getStats().getValue(Stats.CUSTOM.get(ModTriggers.LETTERS_SENT.get())));
		}
		PostOffice.get(player.level().getServer()).enqueue(new PendingDelivery(UUID.randomUUID(), player.getUUID(),
			player.getGameProfile().name(), mailbox.getName(), here, target.name(), items, player.level().getGameTime()));
		String key = Mail.isLetterOnly(items) ? "message.postrider.mailbox.queued_letter" : "message.postrider.mailbox.queued";
		int first = PostRiderConfig.firstCollection();
		boolean collecting = PostOfficeHours.isCollecting(player.level().getOverworldClockTime(), first, PostRiderConfig.lastCollection());
		Component told = collecting ? Component.translatable(key, target.name())
			: Component.translatable(key + "_later", target.name(), PostOfficeHours.clock(first));
		MailboxNoticePayload.tell(player, told, true);
		return null;
	}
}
