package com.comedymonkey.postrider.content.hopper;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Puts an ice hopper down on the water (or the ice) the player is looking at, the way a boat item does. */
public class IceHopperItem extends Item {
	private static final Predicate<Entity> PICKABLE = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);

	public IceHopperItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
		if (hit.getType() == HitResult.Type.MISS)
			return InteractionResult.PASS;
		Vec3 look = player.getViewVector(1.0F);
		List<Entity> near = level.getEntities(player, player.getBoundingBox().expandTowards(look.scale(5.0)).inflate(1.0), PICKABLE);
		Vec3 eyes = player.getEyePosition();
		for (Entity entity : near)
			if (entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eyes))
				return InteractionResult.PASS;
		if (hit.getType() != HitResult.Type.BLOCK)
			return InteractionResult.PASS;
		Vec3 spot = hit.getLocation();
		IceHopperEntity hopper = new IceHopperEntity(level, spot.x, spot.y, spot.z);
		if (level instanceof ServerLevel server)
			EntityType.<IceHopperEntity>createDefaultStackConfig(server, stack, player).apply(hopper);
		hopper.setYRot(player.getYRot());
		if (!level.noCollision(hopper, hopper.getBoundingBox()))
			return InteractionResult.FAIL;
		if (!level.isClientSide()) {
			level.addFreshEntity(hopper);
			level.gameEvent(player, GameEvent.ENTITY_PLACE, spot);
			stack.consume(1, player);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS;
	}
}
