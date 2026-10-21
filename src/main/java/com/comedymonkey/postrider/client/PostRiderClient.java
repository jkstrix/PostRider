package com.comedymonkey.postrider.client;

import com.comedymonkey.postrider.PostRider;
import com.comedymonkey.postrider.client.gui.LetterEditScreen;
import com.comedymonkey.postrider.client.gui.LetterReadScreen;
import com.comedymonkey.postrider.client.gui.MailboxScreen;
import com.comedymonkey.postrider.client.model.MochilaModel;
import com.comedymonkey.postrider.client.model.PostmanModel;
import com.comedymonkey.postrider.client.model.TentModel;
import com.comedymonkey.postrider.client.renderer.PostmanHorseRenderer;
import com.comedymonkey.postrider.client.renderer.PostmanRenderer;
import com.comedymonkey.postrider.client.renderer.TentRenderer;
import com.comedymonkey.postrider.registry.ModEntities;
import com.comedymonkey.postrider.content.letter.LetterScreens;
import com.comedymonkey.postrider.content.mailbox.MailboxBlock;
import com.comedymonkey.postrider.registry.ModBlocks;
import com.comedymonkey.postrider.registry.ModItems;
import com.comedymonkey.postrider.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = PostRider.MOD_ID, dist = Dist.CLIENT)
public class PostRiderClient {
	public PostRiderClient(IEventBus modBus) {
		LetterScreens.setOpener(new LetterScreens.Opener() {
			@Override
			public void openEditor(Player player, InteractionHand hand, ItemStack letter) {
				Minecraft.getInstance().setScreen(new LetterEditScreen(player, hand, letter));
			}

			@Override
			public void openReader(ItemStack letter) {
				Minecraft.getInstance().setScreen(new LetterReadScreen(letter));
			}
		});
		modBus.addListener(this::registerScreens);
		modBus.addListener(this::registerLayers);
		modBus.addListener(this::registerRenderers);
		modBus.addListener(this::registerBlockColors);
		modBus.addListener(this::registerItemColors);
	}

	private void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(PostmanModel.LAYER, PostmanModel::createBodyLayer);
		event.registerLayerDefinition(PostmanModel.SLIM_LAYER, PostmanModel::createSlimBodyLayer);
		event.registerLayerDefinition(MochilaModel.LAYER, MochilaModel::createBodyLayer);
		event.registerLayerDefinition(TentModel.LAYER, TentModel::createBodyLayer);
	}

	private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntities.POSTMAN.get(), PostmanRenderer::new);
		event.registerEntityRenderer(ModEntities.POSTMAN_HORSE.get(), PostmanHorseRenderer::new);
		event.registerEntityRenderer(ModEntities.TENT.get(), TentRenderer::new);
	}

	private void registerScreens(RegisterMenuScreensEvent event) {
		event.register(ModMenus.MAILBOX.get(), MailboxScreen::new);
	}

	/** The mailbox box faces are tint index 0; one white texture covers every dye colour. */
	private void registerBlockColors(RegisterColorHandlersEvent.Block event) {
		for (DyeColor color : DyeColor.values()) {
			MailboxBlock block = ModBlocks.mailbox(color).get();
			event.register((state, level, pos, tint) -> tint == 0 ? color.getTextureDiffuseColor() : -1, block);
		}
	}

	private void registerItemColors(RegisterColorHandlersEvent.Item event) {
		for (DyeColor color : DyeColor.values())
			event.register((stack, tint) -> tint == 0 ? color.getTextureDiffuseColor() : -1, ModItems.mailbox(color).get());
	}
}
