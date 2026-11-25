package com.comedymonkey.postrider.client.sound;

import java.util.Map;
import java.util.WeakHashMap;

import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A rider's harmonica, played on each player's own client while he sits playing: it follows him, starts for anyone
 * who comes along mid-tune, and fades out over a couple of seconds once he stops, without touching any other
 * rider's tune.
 */
public class HarmonicaSound extends AbstractTickableSoundInstance {
	/** Each rider's tune, as last struck up here. */
	private static final Map<PostmanEntity, HarmonicaSound> PLAYING = new WeakHashMap<>();
	/** A tune the sound engine didn't take (the volume was down, say) is tried again this often. */
	private static final int RETRY_TICKS = 20;
	/** Once he stops playing, the tune fades out over this many ticks (two seconds) rather than cutting off. */
	public static final int FADE_TICKS = 40;

	private final PostmanEntity rider;
	private boolean fading;

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
	public static void onEntityTick(Entity entity) {
		if (!(entity instanceof PostmanEntity rider) || !rider.level().isClientSide() || !plays(rider))
			return;
		SoundManager sounds = Minecraft.getInstance().getSoundManager();
		HarmonicaSound current = PLAYING.get(rider);
		// Struck up once he starts; and again if the engine didn't take it, or dropped it (a resource reload)
		if (current == null || !sounds.isActive(current) && rider.tickCount % RETRY_TICKS == 0) {
			HarmonicaSound tune = new HarmonicaSound(rider);
			PLAYING.put(rider, tune);
			sounds.play(tune);
		}
	}

	private static boolean plays(PostmanEntity rider) {
		return !rider.isRemoved() && rider.getActivity() == PostmanEntity.Activity.HARMONICA;
	}

	@Override
	public void tick() {
		if (fading || !plays(rider)) {
			if (!fading) {
				fading = true;
				PLAYING.remove(rider);
			}
			volume = faded(volume);
			if (volume <= 0)
				stop();
		}
		if (!rider.isRemoved()) {
			x = rider.getX();
			y = rider.getY();
			z = rider.getZ();
		}
	}

	/** The volume a tick on in the fade: down by an equal step each tick, to nothing after {@link #FADE_TICKS}. */
	public static float faded(float volume) {
		return Math.max(0, volume - 1.0F / FADE_TICKS);
	}
}
