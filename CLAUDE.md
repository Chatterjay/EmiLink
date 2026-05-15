# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development

```bash
./gradlew build              # Full build (Java 21 toolchain)
./gradlew runClient          # Start Minecraft client
./gradlew runServer          # Start dedicated server
./gradlew runGameTestServer  # Run game tests
```

- **Java 21**, NeoForge 21.1.220, Minecraft 1.21.1
- Parchment mappings: 1.21.11 (2025.12.20)
- IDE: `./gradlew idea` or open in IntelliJ with NeoForge plugin

## Project Architecture

### Mod Identity
- **Mod ID**: `emilink` — a **NeoForge** client+server mod that integrates **EMI** (recipe mod) with **AE2**, **BeyondDimensions (BD)**, and **ExtendedAE_Plus (EAEP)**
- All optional mods are detected via reflection at runtime — no hard dependencies beyond NeoForge, Minecraft, and EMI

### Package Layout (`org.chatterjay.emiextend`)

| Package | Role |
|---------|------|
| `EmiAE2.java` | Main entry point: config, packet registration, server events |
| `config/` | NeoForge `ModConfigSpec` — cache TTL, debug mode, rate limiting |
| `network/packet/c2s/` | 4 client→server packets (AEQuery, AELockedSlots, BDAction, TransferMatching) |
| `network/packet/s2c/` | 3 server→client packets (AEQueryResponse, ClearCache, ServerHasMod) |
| `network/PacketRateLimiter.java` | Game-tick-based rate limiter for debug packets |
| `integration/` | Reflection proxies for optional mods (AE2Proxy, BDProxy, EAEPProxy, CuriosProxy) |
| `client/` | Client-side event subscribers (BDShortcutHandler, InputEvents, AENetworkCache, ModKeybindings) |
| `client/handler/` | Business-logic handlers (EmiInteractionHandler, BookmarkOnCancelHandler, WrapAsBookHandler) |
| `mixin/` | Mixins targeting AE2, EMI, and generic MC classes |
| `server/` | Server-side logic (IPNLockHandler) |
| `util/` | Utility classes (EmiCraftHelper, IPNProxy, IEProxy, ProviderSearchHelper, ServerIPNState, ModLogger) |

### Key Patterns

**Integration Proxies**
All follow the same pattern: lazy `isLoaded()` check via `ModList.get().isLoaded("modid")`, then reflection for all class/method access. This means the mod compiles without optional mods on the classpath (AE2/BD are `compileOnly` in build.gradle). EAEP is loaded from a local JAR in `libs/`.

**Network Layer**
CustomPacketPayload records with NeoForge `StreamCodec` for serialization. Registered in `EmiAE2.registerPackets()` as `optional()` so missing server-side doesn't crash the client. Packet handlers check `PacketFlow` and use `context.enqueueWork()`.

**Conditional Mixins**
`MixinConditionalPlugin` checks classpath resource presence before applying mixins that target AE2/IE classes, preventing `MixinTargetAlreadyLoadedException`.

**AE Network Query** (`AEQueryPacket`)
- Resolves `IGrid` from `AEBaseMenu` via reflection (multiple API paths for compatibility)
- Queries item count via `KeyCounter.get()` and craftability via `ICraftingService.isCraftable()`
- Rate-limited on server side (1 debug packet/tick)

**IPN Locked Slot Integration**
- Client: `IPNProxy` reads locked slots from IPN API via reflection
- Server: `AELockedSlotsPacket` syncs locked slots before Space+click MOVE_REGION
- `IPNLockHandler` saves/restores locked slot items around MOVE_REGION to prevent IPN from filling them

**BD Network Operations** (all via reflection)
- `BDProxy.extractFromNetwork()` — get items from BD storage into player inventory
- `BDProxy.depositToNetwork()` — put items from player inventory into BD storage
- `BDProxy.massCraft()` — loop craft up to 512 times by simulating PICKUP on result slot
- Fallback: `BDProxy.sendBatchTransfer()` uses BD's native `BatchTransferPacket` when server lacks EmiLink

### New Feature Template

To add a new optional-mod integration:
1. Create a proxy class in `integration/` with `isLoaded()` using `ModList.get().isLoaded("modid")`
2. Add packets if needed (register in `EmiAE2.registerPackets()`)
3. Wire handlers in client subscriber classes (`@EventBusSubscriber`)
4. Add mixin with `MixinConditionalPlugin` resource check if targeting optional mods
