package com.comedymonkey.postrider.command;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.RiderWatch;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.Roster;
import com.comedymonkey.postrider.post.PendingDelivery;
import com.comedymonkey.postrider.post.PostDispatcher;
import com.comedymonkey.postrider.post.Posting;
import com.comedymonkey.postrider.registry.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Operator-only tools for trying the mod out: /postrider spawn, gesture, activity and satchel act on the rider
 * nearest the player.
 */
public final class PostRiderCommand {
	private static final double SEARCH_RANGE = 32;

	private PostRiderCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("postrider").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
		spawn.executes(ctx -> spawn(ctx.getSource(), null));
		for (Roster.Rider each : Roster.ALL)
			spawn.then(Commands.literal(each.id()).executes(ctx -> spawn(ctx.getSource(), each)));
		root.then(spawn);
		root.then(Commands.literal("whistle").executes(ctx -> withNearest(ctx.getSource(), PostmanEntity::whistleForHorse)));
		root.then(Commands.literal("dismount").executes(ctx -> withNearest(ctx.getSource(), PostmanEntity::dismissHorse)));
		root.then(Commands.literal("vanish").executes(ctx -> withNearest(ctx.getSource(), PostmanEntity::vanish)));
		// Mail from one box to another by name (quoted if there are spaces): /postrider post "My Post" "Beach House"
		root.then(Commands.literal("post")
			.then(Commands.argument("from", StringArgumentType.string())
				.then(Commands.argument("to", StringArgumentType.string()).executes(ctx -> {
					try {
						PendingDelivery delivery = Posting.post(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "from"), StringArgumentType.getString(ctx, "to"));
						ctx.getSource().sendSuccess(() -> Component.literal("Posted from " + delivery.fromName() + " to " + delivery.toName()), true);
						return 1;
					} catch (IllegalArgumentException e) {
						ctx.getSource().sendFailure(Component.literal(e.getMessage()));
						return 0;
					}
				}))));
		// Watch the riders: a spectator kept a few blocks off the rider most recently out (shift lets go until the next)
		root.then(Commands.literal("watch")
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				RiderWatch.start(player);
				ctx.getSource().sendSuccess(() -> Component.literal("Watching the riders: you're kept off the one most recently out; shift lets go until the next. /postrider watch stop ends it."), false);
				return 1;
			})
			.then(Commands.literal("stop").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				RiderWatch.stop(player);
				ctx.getSource().sendSuccess(() -> Component.literal("No longer following the riders."), false);
				return 1;
			})));
		root.then(Commands.literal("dispatch").executes(ctx -> {
			int sent = PostDispatcher.dispatch(ctx.getSource().getServer());
			ctx.getSource().sendSuccess(() -> Component.translatable("command.postrider.dispatched", sent), true);
			return sent;
		}));
		root.then(Commands.literal("status").executes(ctx -> withNearest(ctx.getSource(), rider -> {
			String status = rider.getRun() != null && rider.level() instanceof ServerLevel level
				? rider.getRun().describe(rider, level)
				: rider.getName().getString() + ": no delivery (activity " + rider.getActivity() + ")";
			ctx.getSource().sendSuccess(() -> Component.literal(status), false);
		})));
		root.then(Commands.literal("giveup").executes(ctx -> withNearest(ctx.getSource(), rider -> {
			if (rider.getRun() != null && rider.level() instanceof ServerLevel level)
				rider.getRun().giveUp(rider, level);
		})));

		LiteralArgumentBuilder<CommandSourceStack> gesture = Commands.literal("gesture");
		for (PostmanEntity.Gesture each : PostmanEntity.Gesture.values())
			gesture.then(Commands.literal(name(each)).executes(ctx -> withNearest(ctx.getSource(), p -> p.playGesture(each))));
		root.then(gesture);

		LiteralArgumentBuilder<CommandSourceStack> activity = Commands.literal("activity");
		for (PostmanEntity.Activity each : PostmanEntity.Activity.values())
			activity.then(Commands.literal(name(each)).executes(ctx -> withNearest(ctx.getSource(), p -> p.setActivity(each))));
		root.then(activity);

		root.then(Commands.literal("satchel")
			.then(Commands.literal("full").executes(ctx -> withNearest(ctx.getSource(), p -> p.setCarrying(true))))
			.then(Commands.literal("empty").executes(ctx -> withNearest(ctx.getSource(), p -> p.setCarrying(false)))));
		dispatcher.register(root);
	}

	static String name(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	/** One of the crew in front of you: whoever you name, or anyone at random. */
	private static int spawn(CommandSourceStack source, @Nullable Roster.Rider who) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(3, 0, 3));
		Roster.Rider rider = who != null ? who : Roster.ALL.get(level.getRandom().nextInt(Roster.ALL.size()));
		PostmanEntity postman = PostmanEntity.appear(ModEntities.POSTMAN.get(), level, ahead.x, player.getY(), ahead.z, player.getYRot() + 180, rider);
		if (postman == null)
			return 0;
		source.sendSuccess(() -> Component.translatable("command.postrider.spawned", postman.getName()), true);
		return 1;
	}

	private static int withNearest(CommandSourceStack source, Consumer<PostmanEntity> action) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PostmanEntity nearest = player.level()
			.getEntitiesOfClass(PostmanEntity.class, new AABB(player.blockPosition()).inflate(SEARCH_RANGE))
			.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(player))).orElse(null);
		if (nearest == null) {
			source.sendFailure(Component.translatable("command.postrider.no_rider", (int) SEARCH_RANGE));
			return 0;
		}
		action.accept(nearest);
		return 1;
	}
}
