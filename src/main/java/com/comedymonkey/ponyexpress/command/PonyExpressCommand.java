package com.comedymonkey.ponyexpress.command;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;

import com.comedymonkey.ponyexpress.content.postman.PostmanEntity;
import com.comedymonkey.ponyexpress.registry.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
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
 * Operator-only tools for trying the mod out: /ponyexpress spawn, gesture, activity and satchel act on the rider
 * nearest the player.
 */
public final class PonyExpressCommand {
	private static final double SEARCH_RANGE = 32;

	private PonyExpressCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ponyexpress").requires(source -> source.hasPermission(2));
		root.then(Commands.literal("spawn").executes(ctx -> spawn(ctx.getSource())));

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

	private static int spawn(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.serverLevel();
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(3, 0, 3));
		PostmanEntity postman = ModEntities.POSTMAN.get().create(level);
		if (postman == null)
			return 0;
		postman.moveTo(ahead.x, player.getY(), ahead.z, player.getYRot() + 180, 0);
		level.addFreshEntity(postman);
		source.sendSuccess(() -> Component.translatable("command.ponyexpress.spawned"), true);
		return 1;
	}

	private static int withNearest(CommandSourceStack source, Consumer<PostmanEntity> action) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PostmanEntity nearest = player.serverLevel()
			.getEntitiesOfClass(PostmanEntity.class, new AABB(player.blockPosition()).inflate(SEARCH_RANGE))
			.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(player))).orElse(null);
		if (nearest == null) {
			source.sendFailure(Component.translatable("command.ponyexpress.no_rider", (int) SEARCH_RANGE));
			return 0;
		}
		action.accept(nearest);
		return 1;
	}
}
