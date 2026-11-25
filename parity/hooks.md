# Hook ledger

Every place Post Rider touches a mod loader, and what does the same job on each loader. NeoForge is the reference. The
mod's behaviour lives in common (`PostRiderEvents`, `PostRiderClientEvents`, `Platform`, `ModPayloads`, `Registrar`);
each loader only connects its own hook to it. Checked by `loader-parity-check` (every NeoForge class used in `neoforge/`
must be in a row, every loader cell filled).

| NeoForge | Fabric | Forge | Common handler | Notes |
| --- | --- | --- | --- | --- |
| `@Mod`, `IEventBus`, `ModContainer`, `NeoForge` (event bus) | TODO | TODO | `PostRiderEvents.init` | init runs once, when the mod is constructed, before registries fill |
| `Mod` with `Dist.CLIENT` (client entry) | TODO | TODO | `PostRiderClientEvents.init` | client only |
| `RegisterEvent` (every registry) | TODO | TODO | `ModRegistries.ALL`, `Registrar.registerAll` | in the order of `ModRegistries.ALL`; each entry's value is made when registered |
| `FMLCommonSetupEvent` (`enqueueWork`) | TODO | TODO | `PostRiderEvents.commonSetup` | after registries, on the main thread (stat formats) |
| `EntityAttributeCreationEvent` | TODO | TODO | `ModEntities.registerAttributes` | postman and postman horse |
| `ModConfig`, `ModConfigSpec` (SERVER, `postrider-server.toml`) | TODO | TODO | `PostRiderConfig.ALL`, `PostRiderConfig.setValues` | same file name, sections, keys, comments, defaults, ranges; reads defaults until loaded; record each setting with `ParityManifest.recordConfig` |
| `RegisterPayloadHandlersEvent`, `PayloadRegistrar`, `IPayloadContext` | TODO | TODO | `ModPayloads.ALL`, `PacketContext` | protocol "1"; handlers call `enqueueWork` for game logic, which must run on the main thread; record each payload with `ParityManifest.recordPayload` |
| `PacketDistributor.sendToPlayer` | TODO | TODO | `Platform.sendToPlayer` | |
| `PacketDistributor.sendToServer` | TODO | TODO | `Platform.sendToServer` | client only |
| `IMenuTypeExtension.create`, `Player.openMenu(provider, buf)` | TODO | TODO | `Platform.menuType`, `Platform.openMenu` | the mailbox menu: block pos then the address string, written by the server, read by the client's menu |
| `EventHooks.finalizeMobSpawn` | TODO | TODO | `Platform.finalizeMobSpawn` | NeoForge fires FinalizeSpawnEvent, then calls `Mob.finalizeSpawn` unless cancelled |
| `TicketController`, `TicketHelper`, `TicketSet`, `RegisterTicketControllersEvent` | TODO | TODO | `Platform.forceChunk`, `RiderChunks.validate` | tickets saved with the world under `postrider:rider`, ticking or not, per rider UUID; when a level loads, `RiderChunks.validate` sees the saved tickets and drops some (riders no longer working; non-ticking chunks) |
| `CreativeModeTab.builder()` (NeoForge's own) | TODO | TODO | `Platform.creativeTabBuilder` | the mod's own tab, placed after the vanilla ones |
| `SavedData.Factory` with a null `DataFixTypes` (NeoForge reads such data unfixed) | TODO | TODO | `Platform.savedDataFactory` | post office and mailbox directory; saved under `data/postrider_*` as before |
| `FMLPaths.GAMEDIR` | TODO | TODO | `Platform.gameDirectory` | trials.jsonl is written there |
| `ModList.isLoaded` | TODO | TODO | `Platform.isModLoaded` | |
| `ServerTickEvent.Pre` | TODO | TODO | `PostRiderEvents.serverTickStart` | before the levels tick |
| `ServerTickEvent.Post` | TODO | TODO | `PostRiderEvents.serverTickEnd` | after the levels tick |
| `ServerStartedEvent` | TODO | TODO | `PostRiderEvents.serverStarted` | the parity manifest is written here |
| `ServerStoppedEvent` | TODO | TODO | `PostRiderEvents.serverStopped` | |
| `EntityJoinLevelEvent` | TODO | TODO | `PostRiderEvents.entityJoinedLevel` | every entity added to a level, loaded from disk or spawned, both sides (the handler ignores the client); never cancelled |
| `BlockEvent.BreakEvent` (`setCanceled`; NeoForge resends the block to the client itself) | TODO | TODO | `PostRiderEvents.breakRefused` | a player breaking a block, server side, before it breaks; true means cancel and resend the block to the client |
| `RegisterCommandsEvent` | TODO | TODO | `PostRiderEvents.registerCommands` | |
| `RegisterGameTestsEvent` (`register(GameTests.class)`) | TODO | TODO | `GameTests.all` (a vanilla `@GameTestGenerator`) | 53 tests named `<method name in lower case>`, structures `postrider:<template>`, batch and timeout from each test's `@GameTest`; the game test runs keep only the `postrider` namespace |
| `EntityTickEvent.Post` (client) | TODO | TODO | `PostRiderClientEvents.entityTickEnd` | after each entity's tick on the client, passengers too (riders ride horses) |
| `ClientTickEvent.Post` | TODO | TODO | `PostRiderClientEvents.clientTickEnd` | |
| `EntityRenderersEvent.RegisterLayerDefinitions` | TODO | TODO | `PostRiderClientEvents.registerLayers` | |
| `EntityRenderersEvent.RegisterRenderers` | TODO | TODO | `PostRiderClientEvents.registerRenderers` | |
| `RegisterMenuScreensEvent` | TODO | TODO | `PostRiderClientEvents.registerScreens` | |
| `RegisterColorHandlersEvent.Block`, `RegisterColorHandlersEvent.Item` | TODO | TODO | `PostRiderClientEvents.registerBlockColors`, `PostRiderClientEvents.registerItemColors` | tint index 0 of each mailbox block and item, its dye colour; -1 (untinted) otherwise |
| `META-INF/accesstransformer.cfg` (common) | TODO | TODO | | `Horse.setVariantAndMarkings`, `Mob.targetSelector`, `BlockEntityType$BlockEntitySupplier` |
| `client/dailies` (Dailies compat) | n/a: Dailies is a NeoForge mod (user, 2026-11-25) | n/a: Dailies is a NeoForge mod (user, 2026-11-25) | | NeoForge module only |
