package com.comedymonkey.postrider.forge.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Client only: what the mod's Forge glue reads from the client. Only loaded when called, on the client. */
public final class ForgeClientAccess {
	private ForgeClientAccess() {}

	/** The local player, who receives the packets sent to the client. */
	@Nullable
	public static Player localPlayer() {
		return Minecraft.getInstance().player;
	}
}
