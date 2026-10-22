package com.comedymonkey.postrider.content.postman;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.advancement.PostEvents;
import com.comedymonkey.postrider.content.postman.PostmanEntity.Gesture;
import com.comedymonkey.postrider.post.PostOfficeHours;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The little things a rider does on the road to show there's someone in the saddle: waving at passing players,
 * tipping his hat to villagers, wiping his brow in the desert, yawning in the evening, checking the address as he
 * gets close. Greetings come first; otherwise every 20-45 seconds he picks something that suits where he is.
 */
public final class Mannerisms {
	/** The quiet stretch between mannerisms, in ticks. */
	public static final int MIN_GAP = 20 * 20;
	public static final int MAX_GAP = 45 * 20;
	/** He greets someone who comes this close in plain sight, once until they've been this far apart again. */
	public static final double GREET_RANGE = 12;
	public static final double GREET_FORGET_RANGE = 32;
	/** Villagers get a tip of the hat now and then, not every time. */
	public static final double VILLAGER_RANGE = 8;
	public static final float VILLAGER_GREET_CHANCE = 0.3F;
	/** Within this many blocks of the mailbox he checks the address; further than this is a long way to go. */
	public static final double NEAR_DESTINATION = 100;
	public static final double LONG_WAY = 500;
	/** 4pm: from here until he makes camp, he's getting tired. */
	public static final long EVENING = 10000;
	/** Deserts, savannas and badlands, by base temperature. */
	public static final float HOT = 1.5F;

	/** What's going on around him, which makes some mannerisms likelier than others. */
	public record Situation(boolean riding, boolean hot, boolean cold, boolean raining, boolean evening, boolean buggy,
		boolean detouring, double toGo) {}

	private final Set<UUID> greeted = new HashSet<>();
	private int nextAt = -1;
	@Nullable
	private Gesture last;
	@Nullable
	private Gesture prompted;
	private boolean wasRaining;
	private boolean readAddress;

	/** How likely each mannerism is in a situation; zero for ones he'd never do there (and for work gestures). */
	public static double weight(Gesture gesture, Situation s) {
		return switch (gesture) {
			case WIPE_BROW -> s.hot() ? 3 : 0.3;
			case SWIG -> s.hot() ? 2.5 : 0.6;
			case SHIVER -> s.cold() ? 3 : 0;
			case PULL_HAT_DOWN -> s.raining() ? 1.5 : 0;
			case YAWN -> s.evening() ? 3 : 0.2;
			case STRETCH -> s.evening() ? 2 : 0.6;
			case SCRATCH_HEAD -> s.detouring() ? 3 : 0.3;
			case SHADE_EYES -> s.toGo() > LONG_WAY ? 2 : 0.4;
			case POCKET_WATCH -> s.toGo() > LONG_WAY ? 1.5 : 0.8;
			case SWAT_FLY -> s.buggy() ? 3 : 0.3;
			case PAT_HORSE -> s.riding() ? 1.5 : 0;
			case LOOK_BACK -> s.riding() ? 1 : 0.5;
			case CHECK_SATCHEL, ADJUST_HAT, HUM, NECK_ROLL, CRACK_KNUCKLES -> 1;
			// Greetings, the address check and his work have their own moments
			case WAVE, TIP_HAT, READ_LETTER, PICKUP, DELIVER, WHISTLE, DRINK, LIGHT_FIRE -> 0;
		};
	}

	/** A mannerism for the situation, never the same one twice running; null if there's nothing he'd do. */
	@Nullable
	public static Gesture pick(Situation situation, @Nullable Gesture last, RandomSource random) {
		double total = 0;
		for (Gesture gesture : Gesture.values())
			if (gesture != last)
				total += weight(gesture, situation);
		double roll = random.nextDouble() * total;
		for (Gesture gesture : Gesture.values()) {
			if (gesture == last)
				continue;
			roll -= weight(gesture, situation);
			if (weight(gesture, situation) > 0 && roll < 0)
				return gesture;
		}
		return null;
	}

	/** Forgets anyone who's left (or wandered off past {@link #GREET_FORGET_RANGE}), so meeting them again earns a fresh greeting. */
	public static void forget(Set<UUID> greeted, Map<UUID, Double> nearbyDistanceSqr) {
		greeted.removeIf(id -> {
			Double distanceSqr = nearbyDistanceSqr.get(id);
			return distanceSqr == null || distanceSqr > GREET_FORGET_RANGE * GREET_FORGET_RANGE;
		});
	}

	/** Whether someone gets greeted now: close, in sight and not yet greeted this time round. Marks them greeted. */
	public static boolean greets(Set<UUID> greeted, UUID who, double distanceSqr, double range, boolean inSight) {
		if (distanceSqr > range * range || !inSight || greeted.contains(who))
			return false;
		greeted.add(who);
		return true;
	}

	/** Has him do a particular mannerism as soon as he's free (a head scratch after being lost, say). */
	public void prompt(Gesture gesture) {
		prompted = gesture;
	}

	/** Called every tick while he's travelling. */
	public void tick(PostmanEntity rider, ServerLevel level, double toGo, boolean detouring) {
		BlockPos pos = rider.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		boolean raining = level.isRaining() && biome.value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
		if (raining && !wasRaining)
			prompted = Gesture.PULL_HAT_DOWN;
		wasRaining = raining;
		if (toGo < NEAR_DESTINATION && !readAddress) {
			readAddress = true;
			prompted = Gesture.READ_LETTER;
		}
		if (rider.isBusy())
			return;
		if (rider.tickCount % 10 == 0 && greet(rider, level))
			return;
		if (prompted != null) {
			play(rider, prompted);
			prompted = null;
			return;
		}
		if (nextAt < 0)
			nextAt = rider.tickCount + gap(rider.getRandom());
		if (rider.tickCount < nextAt)
			return;
		long time = level.getDayTime() % 24000L;
		Situation situation = new Situation(rider.getVehicle() != null,
			biome.value().getBaseTemperature() >= HOT && time < PostOfficeHours.CLOSES,
			biome.value().coldEnoughToSnow(pos), raining, time >= EVENING && time < PostOfficeHours.CLOSES + 1200,
			biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP), detouring, toGo);
		Gesture gesture = pick(situation, last, rider.getRandom());
		if (gesture != null)
			play(rider, gesture);
		else
			nextAt = rider.tickCount + gap(rider.getRandom());
	}

	private void play(PostmanEntity rider, Gesture gesture) {
		rider.playGesture(gesture);
		last = gesture;
		nextAt = rider.tickCount + gap(rider.getRandom());
	}

	public static int gap(RandomSource random) {
		return MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP + 1);
	}

	/** A wave or a tip of the hat for a player who's just come along, or now and then for a villager. */
	private boolean greet(PostmanEntity rider, ServerLevel level) {
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, rider.getBoundingBox().inflate(GREET_FORGET_RANGE),
			e -> (e instanceof Player player && !player.isSpectator() && !player.isInvisible()) || e instanceof Villager);
		forget(greeted, nearby.stream().collect(Collectors.toMap(LivingEntity::getUUID, rider::distanceToSqr, (a, b) -> a)));
		for (LivingEntity other : nearby) {
			boolean villager = other instanceof Villager;
			if (!greets(greeted, other.getUUID(), rider.distanceToSqr(other), villager ? VILLAGER_RANGE : GREET_RANGE, rider.hasLineOfSight(other)))
				continue;
			if (villager && rider.getRandom().nextFloat() >= VILLAGER_GREET_CHANCE)
				continue;
			play(rider, villager || rider.getRandom().nextBoolean() ? Gesture.TIP_HAT : Gesture.WAVE);
			Roster.Rider who = rider.getRider();
			if (other instanceof ServerPlayer player && who != null)
				PostEvents.fire(player, PostEvents.GREETED, who.id(), 1);
			return true;
		}
		return false;
	}
}
