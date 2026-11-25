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
| `ModConfig`, `ModConfigSpec` (SYNCED, `postrider-server.toml`) | TODO | TODO | `PostRiderConfig.ALL`, `PostRiderConfig.setValues` | same file name, sections, keys, comments, defaults, ranges; reads defaults until loaded; record each setting with `ParityManifest.recordConfig` |
| `RegisterPayloadHandlersEvent`, `PayloadRegistrar`, `IPayloadContext` | TODO | TODO | `ModPayloads.ALL`, `PacketContext` | protocol "1"; handlers call `enqueueWork` for game logic, which must run on the main thread; record each payload with `ParityManifest.recordPayload` |
| `PacketDistributor.sendToPlayer` | TODO | TODO | `Platform.sendToPlayer` | |
| `ClientPacketDistributor.sendToServer` | TODO | TODO | `Platform.sendToServer` | client only |
| `IMenuTypeExtension.create`, `Player.openMenu(provider, buf)` | TODO | TODO | `Platform.menuType`, `Platform.openMenu` | the mailbox menu: block pos then the address string, written by the server, read by the client's menu |
| `EventHooks.finalizeMobSpawn` | TODO | TODO | `Platform.finalizeMobSpawn` | NeoForge fires FinalizeSpawnEvent, then calls `Mob.finalizeSpawn` unless cancelled |
| `TicketController`, `TicketHelper`, `RegisterTicketControllersEvent` | TODO | TODO | `Platform.forceChunk`, `RiderChunks.validate` | tickets saved with the world under `postrider:rider`, ticking or not, per rider UUID; when a level loads, `RiderChunks.validate` sees the saved tickets and drops some (riders no longer working; non-ticking chunks) |
| `CreativeModeTab.builder()` (NeoForge's own) | TODO | TODO | `Platform.creativeTabBuilder` | the mod's own tab, placed after the vanilla ones |
| `SavedDataType` three-argument constructor (null data fixer) | TODO | TODO | `Platform.savedDataType` | post office and mailbox directory; saved under `data/postrider_*` as before |
| `FMLPaths.GAMEDIR` | TODO | TODO | `Platform.gameDirectory` | trials.jsonl is written there |
| `ModList.isLoaded` | TODO | TODO | `Platform.isModLoaded` | |
| `ServerTickEvent.Pre` | TODO | TODO | `PostRiderEvents.serverTickStart` | before the levels tick |
| `ServerTickEvent.Post` | TODO | TODO | `PostRiderEvents.serverTickEnd` | after the levels tick |
| `ServerStartedEvent` | TODO | TODO | `PostRiderEvents.serverStarted` | the parity manifest is written here |
| `ServerStoppedEvent` | TODO | TODO | `PostRiderEvents.serverStopped` | |
| `EntityJoinLevelEvent` | TODO | TODO | `PostRiderEvents.entityJoinedLevel` | every entity added to a level, loaded from disk or spawned, both sides (the handler ignores the client); never cancelled |
| `BreakBlockEvent` (`setCanceled`, `setNotifyClient`) | TODO | TODO | `PostRiderEvents.breakRefused` | a player breaking a block, server side, before it breaks; true means cancel and resend the block to the client |
| `RegisterCommandsEvent` | TODO | TODO | `PostRiderEvents.registerCommands` | |
| `RegisterGameTestsEvent`, `RegisterEvent` for `TEST_FUNCTION` | TODO | TODO | `GameTests.registerFunctions`, `GameTests.registerTests` | 53 tests, an environment per batch, names `postrider:<method name in lower case>` |
| `EntityTickEvent.Post` (client) | TODO | TODO | `PostRiderClientEvents.entityTickEnd` | after each entity's tick on the client, passengers too (riders ride horses) |
| `ClientTickEvent.Post` | TODO | TODO | `PostRiderClientEvents.clientTickEnd` | |
| `EntityRenderersEvent.RegisterLayerDefinitions` | TODO | TODO | `PostRiderClientEvents.registerLayers` | |
| `EntityRenderersEvent.RegisterRenderers` | TODO | TODO | `PostRiderClientEvents.registerRenderers` | |
| `RegisterMenuScreensEvent` | TODO | TODO | `PostRiderClientEvents.registerScreens` | |
| `RegisterColorHandlersEvent.BlockTintSources` | TODO | TODO | `PostRiderClientEvents.registerBlockTints` | tint index 0 of each mailbox, its dye colour |
| `META-INF/accesstransformer.cfg` (common) | TODO | TODO | | `Horse.setVariantAndMarkings`, `Mob.targetSelector`, `CreativeModeTab$Output` |
| `client/dailies` (Dailies compat) | n/a: Dailies is a NeoForge mod (user, 2026-11-25) | n/a: Dailies is a NeoForge mod (user, 2026-11-25) | | NeoForge module only |
