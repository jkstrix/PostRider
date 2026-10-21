package com.comedymonkey.postrider.client.sound;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A rider's harmonica, played on each player's own client while he sits playing: it follows him, starts for anyone
 * who comes along mid-tune, and stops the moment he does, without touching any other rider's tune.
 */
public class HarmonicaSound extends AbstractTickableSoundInstance {
	/** The riders whose tune is playing here already. */
	private static final Set<PostmanEntity> PLAYING = Collections.newSetFromMap(new WeakHashMap<>());

	private final PostmanEntity rider;

	private HarmonicaSound(PostmanEntity rider) {
		super(ModSounds.HARMONICA.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.rider = rider;
		this.looping = true;
		this.delay = 0;
		this.x = rider.getX();
		this.y = rider.getY();
		this.z = rider.getZ();
	}

	/** Strikes up the tune for any rider who has started playing. */
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity() instanceof PostmanEntity rider && rider.level().isClientSide && plays(rider) && PLAYING.add(rider))
			Minecraft.getInstance().getSoundManager().play(new HarmonicaSound(rider));
	}

	private static boolean plays(PostmanEntity rider) {
		return !rider.isRemoved() && rider.getActivity() == PostmanEntity.Activity.HARMONICA;
	}

	@Override
	public void tick() {
		if (!plays(rider)) {
			PLAYING.remove(rider);
			stop();
			return;
		}
		x = rider.getX();
		y = rider.getY();
		z = rider.getZ();
	}
}
