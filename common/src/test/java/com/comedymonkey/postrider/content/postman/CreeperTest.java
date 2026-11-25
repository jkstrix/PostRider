package com.comedymonkey.postrider.content.postman;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.comedymonkey.postrider.content.lost.LostParcelBlock;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;

class CreeperTest {
	@Test
	void creepersIgnoreRidersUntilTheyAreRightUpClose() {
		assertFalse(RiderHunting.creeperNotices(10 * 10, false));
		assertFalse(RiderHunting.creeperNotices(4 * 4, false));
		assertTrue(RiderHunting.creeperNotices(3 * 3, false));
		assertTrue(RiderHunting.creeperNotices(1, false));
	}

	@Test
	void creepersNeverNoticeHimInHisTent() {
		assertFalse(RiderHunting.creeperNotices(1, true));
		assertFalse(RiderHunting.creeperNotices(0, true));
	}

	@Test
	void creepersNeverStalkRiders() {
		assertFalse(RiderHunting.huntsRiders(Creeper.class));
		assertEquals(3.0, RiderHunting.CREEPER_NOTICE_RANGE);
	}

	@Test
	void explosionsNeverDestroyLostParcelsOrMailboxes() throws NoSuchMethodException {
		// Each takes over the game's own explosion handling, which is what takes a block away
		Class<?>[] args = {BlockState.class, ServerLevel.class, BlockPos.class, Explosion.class, BiConsumer.class};
		assertEquals(LostParcelBlock.class, LostParcelBlock.class.getDeclaredMethod("onExplosionHit", args).getDeclaringClass());
		assertEquals(MailboxBlock.class, MailboxBlock.class.getDeclaredMethod("onExplosionHit", args).getDeclaringClass());
		assertEquals(CampfireBlock.class, CampfireBlock.class.getDeclaredMethod("onExplosionHit", args).getDeclaringClass());
	}
}
