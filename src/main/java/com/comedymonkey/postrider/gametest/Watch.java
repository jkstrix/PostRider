package com.comedymonkey.postrider.gametest;

import org.jetbrains.annotations.Nullable;

import com.comedymonkey.postrider.content.postman.BargeEntity;
import com.comedymonkey.postrider.content.postman.Crossing;
import com.comedymonkey.postrider.content.postman.DeliveryRun;
import com.comedymonkey.postrider.content.postman.PostmanEntity;
import com.comedymonkey.postrider.content.postman.PostmanHorse;
import com.comedymonkey.postrider.post.PendingDelivery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Keeps an eye on the rider carrying one delivery, every tick, and remembers what he got up to. Test-only. */
final class Watch {
	private final Scene scene;
	final PendingDelivery delivery;
	@Nullable
	PostmanEntity rider;
	@Nullable
	DeliveryRun run;
	boolean studiedMap;
	boolean rowed;
	/** How many times he's put out in the barge. */
	int launches;
	private boolean wasOnBarge;
	boolean horseAboard;
	boolean landed;
	/** He (or his horse) stood or swam in water off the barge, and where. */
	@Nullable
	String waded;
	boolean hadHorseBeforeWater;
	boolean hadHorseAfterLanding;

	Watch(Scene scene, PendingDelivery delivery) {
		this.scene = scene;
		this.delivery = delivery;
		scene.helper.onEachTick(this::look);
	}

	private void look() {
		PostmanEntity now = scene.rider(delivery);
		if (now == null)
			return;
		rider = now;
		if (now.getRun() != null)
			run = now.getRun();
		if (now.getActivity() == PostmanEntity.Activity.STUDY_MAP)
			studiedMap = true;
		boolean onBarge = now.getVehicle() instanceof BargeEntity;
		if (onBarge && !wasOnBarge)
			launches++;
		wasOnBarge = onBarge;
		if (onBarge) {
			rowed = true;
			for (Entity passenger : now.getVehicle().getPassengers())
				if (!(passenger instanceof PostmanEntity))
					horseAboard = true;
		} else if (rowed) {
			landed = true;
		}
		if (!rowed && now.getHorse() != null)
			hadHorseBeforeWater = true;
		// Once over, his horse comes when he whistles (he may be at the box and get down again before he's ridden it far)
		if (landed && (now.getHorse() != null || !scene.level.getEntitiesOfClass(PostmanHorse.class, now.getBoundingBox().inflate(8)).isEmpty()))
			hadHorseAfterLanding = true;
		Entity body = now.getVehicle() != null ? now.getVehicle() : now;
		// What's at his feet (or his horse's) now: the "in water" flag lags a tick behind stepping off the barge
		if (!onBarge && waded == null && (feetWet(now) || feetWet(body))) {
			Entity wet = feetWet(now) ? now : body;
			waded = (wet == now ? "rider" : "horse") + " at " + scene.helper.relativeVec(wet.position()) + " " + now.getRun()
				+ ", box " + wet.getBoundingBox() + ", feet in " + scene.level.getBlockState(wet.blockPosition());
		}
	}

	private boolean feetWet(Entity entity) {
		return Crossing.isWater(scene.level.getFluidState(BlockPos.containing(entity.getX(), entity.getY() + 0.1, entity.getZ())));
	}

		int jumpsFree() {
		return run == null ? 0 : run.jumpsFree();
	}
}
