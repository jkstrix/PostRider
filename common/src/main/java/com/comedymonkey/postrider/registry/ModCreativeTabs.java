package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import com.comedymonkey.postrider.platform.Services;

public final class ModCreativeTabs {
	public static final Registrar<CreativeModeTab> TABS = Registrar.create(Registries.CREATIVE_MODE_TAB);

	public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> Services.PLATFORM.creativeTabBuilder()
		.title(Component.translatable("itemGroup.postrider"))
		.icon(() -> ModItems.mailbox(DyeColor.BLUE).toStack())
		.displayItems((params, output) -> {
			for (DyeColor color : DyeColor.values())
				output.accept(ModItems.mailbox(color));
			output.accept(ModItems.LETTER);
			output.accept(ModItems.ICE_HOPPER);
		})
		.build());

	private ModCreativeTabs() {}
}
