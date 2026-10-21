package com.comedymonkey.ponyexpress;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(PonyExpress.MOD_ID)
public class PonyExpress {
	public static final String MOD_ID = "ponyexpress";
	public static final Logger LOGGER = LogUtils.getLogger();

	public PonyExpress(IEventBus modBus, ModContainer container) {
		modBus.addListener(this::commonSetup);
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		LOGGER.info("Pony Express loaded");
	}
}
