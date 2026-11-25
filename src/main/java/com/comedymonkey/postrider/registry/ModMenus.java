package com.comedymonkey.postrider.registry;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.content.mailbox.MailboxMenu;
import com.comedymonkey.postrider.platform.Services;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final Registrar<MenuType<?>> MENUS = Registrar.create(Registries.MENU);

	public static final RegistryEntry<MenuType<?>, MenuType<MailboxMenu>> MAILBOX =
		MENUS.register("mailbox", () -> Services.PLATFORM.menuType(MailboxMenu::new));

	private ModMenus() {}
}
