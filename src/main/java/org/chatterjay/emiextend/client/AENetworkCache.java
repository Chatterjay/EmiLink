
package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.CuriosProxy;
import org.chatterjay.emiextend.network.packet.c2s.AEBatchQueryPacket;
import org.chatterjay.emiextend.util.ModLogger;
import net.neoforged.bus.api.SubscribeEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AENetworkCache {

    private static long batchFlushMs() {
        return EmiLinkConfig.BATCH_FLUSH_MS.get();
    }

    private static final Map<String, ServerState> serverStates = new HashMap<>();
    private static String currentServerId = "local";
    private static ServerState current = new ServerState();

    /**
     * One-shot AE query state for quick-craft preflight. Results are visible to
     * normal cache readers while the scope is active, but are never persisted.
     */
    private static final Set<CacheKey> ephemeralKeys = new HashSet<>();
    private static final Map<CacheKey, CachedInfo> ephemeralCache = new HashMap<>();

    /** Items pending server query. */
    private static final Map<CacheKey, PendingQuery> pendingBatch = new HashMap<>();
    private static long lastBatchFlushTime = 0;

    /**
     * A transfer packet and its follow-up cache query must not race each other.
     * The configured batch interval also controls this priority refresh window.
     * The default is five seconds.
     */
    private static long networkRefreshAt = Long.MAX_VALUE;

    /** Tracks terminal open/close state for initial scan detection. */
    private static boolean wasInTerminal = false;
    private static boolean needsInitialScan = false;
    private static Screen initialScanScreen = null;

    /** Server capability handshake has not completed while joining a world. */
    private static boolean serverCapabilityKnown = false;

    /** Per-frame cache: screen that hasAEAccess() was last computed for. */
    private static Screen accessCheckScreen = null;
    private static boolean accessCheckResult = false;

    /** Throttle hovered-stack collection to every N tick() calls. */
    private static int hoverTickCounter = 0;
    private static final int HOVER_SAMPLE_INTERVAL = 10;

    /** Disk persistence. */
    private static boolean cacheDirty = false;
    private static long lastAutoSaveTime = 0;
    private static final long AUTO_SAVE_INTERVAL_MS = 30_000;

    private static Path cachePath;

    private AENetworkCache() {}

    /** Queue a single item for the next batch query. */
    public static void submitForBatch(ItemStack stack) {
        submitForBatch(stack, true);
    }

    /** Queue a single item, optionally skipping expensive AE craftability checks. */
    public static void submitForBatch(ItemStack stack, boolean queryCraftability) {
        submitForBatch(stack, queryCraftability, false);
    }

    /** Queue an item and optionally force a refresh even while its cache entry is fresh. */
    private static void submitForBatch(ItemStack stack, boolean queryCraftability, boolean forceRefresh) {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return;
        if (stack == null || stack.isEmpty()) return;
        var keyStack = normalizeKey(stack);
        var key = cacheKey(keyStack);
        var previous = pendingBatch.get(key);
        pendingBatch.put(key, new PendingQuery(keyStack,
                queryCraftability || (previous != null && previous.queryCraftability()),
                forceRefresh || (previous != null && previous.forceRefresh())));
    }

    /**
     * Queue an item for overlay rendering. Overlays query counts first and only
     * ask AE for craftability later when the item is not stored.
     */
    public static void submitForBadge(ItemStack stack) {
        submitForBatch(stack, false);
    }

    /** Begin a temporary query scope whose results should be discarded after use. */
    public static void beginEphemeralQuery(Collection<ItemStack> stacks) {
        clearEphemeralQuery();
        if (stacks == null || stacks.isEmpty()) return;
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            ephemeralKeys.add(cacheKey(normalizeKey(stack)));
        }
        ModLogger.debug("AE cache: began ephemeral query keys={}", ephemeralKeys.size());
    }

    /** Discard temporary query results so they cannot affect later rendering or tooltip state. */
    public static void clearEphemeralQuery() {
        if (!ephemeralKeys.isEmpty() || !ephemeralCache.isEmpty()) {
            ModLogger.debug("AE cache: clearing ephemeral keys={} results={}",
                    ephemeralKeys.size(), ephemeralCache.size());
        }
        ephemeralKeys.clear();
        ephemeralCache.clear();
    }

    /** Returns true once per terminal open, signalling the mixin to scan visible items. */
    public static boolean consumeInitialScanFlag() {
        if (needsInitialScan) {
            needsInitialScan = false;
            return true;
        }
        return false;
    }

    /**
     * Arm the first sidebar scan from the EMI render path as well as the tick
     * path. This covers the frame where EMI finishes building its index after
     * the AE terminal was already open.
     */
    public static void requestInitialScanIfNeeded() {
        var mc = Minecraft.getInstance();
        if (!canQueryNetwork(mc) || mc.screen == null) return;
        if (!wasInTerminal || initialScanScreen != mc.screen) {
            wasInTerminal = true;
            initialScanScreen = mc.screen;
            needsInitialScan = true;
            lastBatchFlushTime = 0;
            ModLogger.debug("Initial scan: armed for AE screen {}", mc.screen.getClass().getName());
        }
    }

    /** Called when the server confirms that EmiLink packet handlers are available. */
    public static void onServerCapabilityConfirmed() {
        serverCapabilityKnown = true;
        BDShortcutHandler.serverHasMod = true;
        ModLogger.debug("Server capability confirmed; keeping persisted AE cache");
    }

    /** Immediately flush the pending batch (used after initial scan collection). */
    public static void flushBatchNow() {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return;
        networkRefreshAt = Long.MAX_VALUE;
        if (!pendingBatch.isEmpty()) {
            lastBatchFlushTime = System.currentTimeMillis();
            flushBatch();
        }
    }

    // ---- Disk persistence -------------------------------------------------

    private static Path cachePath() {
        if (cachePath == null) {
            var dir = FMLPaths.GAMEDIR.get().resolve("emilink");
            try {
                java.nio.file.Files.createDirectories(dir);
            } catch (Exception ignored) {}
            cachePath = dir.resolve("cache.bin");
        }
        return cachePath;
    }

    private static void saveToDisk() {
        if (!cacheDirty) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        RegistryAccess ra = level.registryAccess();

        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), ra);
        buf.writeVarInt(-1);
        buf.writeVarInt(serverStates.size());
        for (var entry : serverStates.entrySet()) {
            buf.writeUtf(entry.getKey());
            var cache = entry.getValue().cache;
            buf.writeVarInt(cache.size());
            for (var mapEntry : cache.entrySet()) {
                CachedEntry cached = mapEntry.getValue();
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, cached.stack());
                buf.writeVarLong(cached.info().count());
                buf.writeBoolean(cached.info().craftable());
                buf.writeBoolean(cached.info().craftabilityKnown());
                buf.writeVarLong(cached.info().timestamp());
            }
        }

        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        DiskCacheIO.save(cachePath(), data);
        ModLogger.debug("Cache saved ({} servers)", serverStates.size());
        cacheDirty = false;
    }

    private static void loadFromDisk() {
        byte[] data = DiskCacheIO.load(cachePath());
        if (data == null) return;

        var level = Minecraft.getInstance().level;
        if (level == null) return;
        RegistryAccess ra = level.registryAccess();

        try {
            var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), ra);
            int header = buf.readVarInt();
            boolean hasVersion = header == -1;
            int serverCount = hasVersion ? buf.readVarInt() : header;
            int totalEntries = 0;
            for (int i = 0; i < serverCount; i++) {
                String sid = buf.readUtf();
                int entryCount = buf.readVarInt();
                var state = new ServerState();
                for (int j = 0; j < entryCount; j++) {
                    try {
                        ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        long count = buf.readVarLong();
                        boolean craftable = buf.readBoolean();
                        boolean craftabilityKnown = hasVersion ? buf.readBoolean() : true;
                        long timestamp = buf.readVarLong();
                        stack = normalizeKey(stack);
                        var key = cacheKey(stack);
                        state.cache.put(key, new CachedEntry(stack,
                                new CachedInfo(count, craftable, craftabilityKnown, timestamp)));
                    } catch (Exception e) {
                        // Item no longer in registry (mod removed) — skip silently
                    }
                }
                serverStates.put(sid, state);
                totalEntries += entryCount;
            }
            // Point current if we just loaded our server
            var loaded = serverStates.get(currentServerId);
            if (loaded != null) current = loaded;
            ModLogger.debug("Cache loaded ({} servers, {} entries)", serverCount, totalEntries);
        } catch (Exception e) {
            ModLogger.warn("Cache load parse failed: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        tick();
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        BDShortcutHandler.serverHasMod = false;
        serverCapabilityKnown = false;
        currentServerId = resolveServerId();
        serverStates.remove(currentServerId);
        current = serverStates.computeIfAbsent(currentServerId, k -> new ServerState());
        pendingBatch.clear();
        clearEphemeralQuery();
        lastBatchFlushTime = 0;
        networkRefreshAt = Long.MAX_VALUE;
        wasInTerminal = false;
        needsInitialScan = false;
        initialScanScreen = null;
        cacheDirty = false;
        accessCheckScreen = null;
        hoverTickCounter = 0;
        ModLogger.debug("Client network login: reset EmiLink server capability state");
        loadFromDisk();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BDShortcutHandler.serverHasMod = false;
        serverCapabilityKnown = false;
        saveToDisk();
        pendingBatch.clear();
        clearEphemeralQuery();
        accessCheckScreen = null;
        initialScanScreen = null;
        ModLogger.debug("Client network logout: reset EmiLink server capability state");
    }

    /** Clear cache & pending batch for the current server only. */
    public static void clear() {
        current.cache.clear();
        pendingBatch.clear();
        clearEphemeralQuery();
        lastBatchFlushTime = 0;
        networkRefreshAt = Long.MAX_VALUE;
        wasInTerminal = false;
        needsInitialScan = false;
        initialScanScreen = null;
        cacheDirty = true;
        accessCheckScreen = null;
        hoverTickCounter = 0;
    }

    /** Clear cache & pending batch for all servers, and delete the disk cache file. */
    public static void clearAll() {
        serverStates.clear();
        current = new ServerState();
        currentServerId = "local";
        pendingBatch.clear();
        clearEphemeralQuery();
        lastBatchFlushTime = 0;
        wasInTerminal = false;
        needsInitialScan = false;
        initialScanScreen = null;
        cacheDirty = false;
        accessCheckScreen = null;
        hoverTickCounter = 0;
        try {
            java.nio.file.Files.deleteIfExists(cachePath());
        } catch (Exception ignored) {}
    }

    private static String resolveServerId() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isEmpty()) {
            return serverData.ip;
        }
        return "local";
    }

    private record CachedInfo(long count, boolean craftable, boolean craftabilityKnown, long timestamp) {
        boolean isExpired(boolean negative) {
            long ttl = negative ? EmiLinkConfig.NEGATIVE_CACHE_TTL_MS.get() : EmiLinkConfig.CACHE_TTL_MS.get();
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    /**
     * ItemStack intentionally uses identity equality in this Minecraft version.
     * This value object supplies content equality without scanning the cache.
     */
    private static final class CacheKey {
        private final Item item;
        private final DataComponentMap components;
        private final int hashCode;

        private CacheKey(Item item, DataComponentMap components) {
            this.item = item;
            this.components = components;
            this.hashCode = 31 * (31 + item.hashCode()) + components.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CacheKey key)) return false;
            return item == key.item && components.equals(key.components);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private record CachedEntry(ItemStack stack, CachedInfo info) {
    }

    private record PendingQuery(ItemStack stack, boolean queryCraftability, boolean forceRefresh) {
    }

    private static class ServerState {
        final Map<CacheKey, CachedEntry> cache = new HashMap<>();
    }

    /** Whether the player can actually query the AE network (needs an open AE2 terminal). */
    private static boolean canQueryNetwork(Minecraft mc) {
        if (!AE2Proxy.isLoaded()) return false;
        if (mc.player == null) return false;
        if (mc.screen == null) return false;
        return AE2Proxy.isMEStorageScreen(mc.screen) || AE2Proxy.isCraftConfirmScreen(mc.screen);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) return;
        if (!BDShortcutHandler.serverHasMod) {
            // The capability packet is sent after the client login event. Do
            // not mistake this short handshake window for a server without
            // EmiLink and erase the cache loaded from disk.
            if (!serverCapabilityKnown) {
                return;
            }
            if (wasInTerminal || !pendingBatch.isEmpty() || current.cache.size() > 0) {
                wasInTerminal = false;
                needsInitialScan = false;
                initialScanScreen = null;
                pendingBatch.clear();
                networkRefreshAt = Long.MAX_VALUE;
                current.cache.clear();
                clearEphemeralQuery();
                accessCheckScreen = null;
            }
            return;
        }

        boolean terminalAccess = canQueryNetwork(mc);
        boolean wirelessAccess = !terminalAccess && computeAEAccess(mc);
        if (!terminalAccess && !wirelessAccess) {
            if (wasInTerminal) {
                wasInTerminal = false;
                needsInitialScan = false;
                initialScanScreen = null;
            }
            if (!pendingBatch.isEmpty()) {
                pendingBatch.clear();
                networkRefreshAt = Long.MAX_VALUE;
                ModLogger.debug("Batch: cleared pending (not in terminal)");
            }
            return;
        }

        if (terminalAccess) {
            // Detect terminal just opened → signal mixin to collect visible items
            if (!wasInTerminal) {
                wasInTerminal = true;
                initialScanScreen = mc.screen;
                needsInitialScan = true;
                lastBatchFlushTime = 0;
            }

            // Collect hovered item into the pending batch — throttled to reduce per-frame EMI overhead
            if (++hoverTickCounter % HOVER_SAMPLE_INTERVAL == 0) {
                var hovered = EmiApi.getHoveredStack(true);
                if (hovered != null && !hovered.isEmpty()) {
                    var stack = hovered.getStack().getEmiStacks().stream()
                            .map(EmiStack::getItemStack)
                            .filter(s -> !s.isEmpty())
                            .findFirst()
                            .orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) {
                        submitForBatch(stack, true);
                    }
                }
            }
        } else if (wasInTerminal) {
            // Keep direct wireless refreshes alive without starting a full sidebar scan.
            wasInTerminal = false;
            needsInitialScan = false;
            initialScanScreen = null;
        }

        // Flush batch on timer
        long now = System.currentTimeMillis();
        if (networkRefreshAt != Long.MAX_VALUE && now >= networkRefreshAt) {
            networkRefreshAt = Long.MAX_VALUE;
            lastBatchFlushTime = now;
            ModLogger.debug("Batch: flushing priority network refresh after transfer");
            flushBatch(true);
        } else if (networkRefreshAt == Long.MAX_VALUE
                && now - lastBatchFlushTime >= batchFlushMs() && !pendingBatch.isEmpty()) {
            flushBatch();
            lastBatchFlushTime = now;
        }

        // Auto-save dirty cache to disk
        if (cacheDirty && now - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
            saveToDisk();
            lastAutoSaveTime = now;
        }
    }

    /**
     * Filter pending batch to items that aren't cached (or whose cache is expired),
     * then send the batch query to the server. A priority-only flush sends only
     * entries marked by a completed network transfer and leaves ordinary hover
     * queries queued for the regular batch interval.
     */
    private static void flushBatch() {
        flushBatch(false);
    }

    private static void flushBatch(boolean priorityOnly) {
        var countOnly = new ArrayList<ItemStack>();
        var withCraftability = new ArrayList<ItemStack>();
        var flushedKeys = new ArrayList<CacheKey>();
        for (var entry : pendingBatch.entrySet()) {
            if (priorityOnly && !entry.getValue().forceRefresh()) continue;

            var it = entry.getValue().stack();
            boolean queryCraftability = entry.getValue().queryCraftability();
            boolean forceRefresh = entry.getValue().forceRefresh();
            flushedKeys.add(entry.getKey());
            var cached = findCached(it);
            if (forceRefresh
                    || cached == null
                    || cached.isExpired(cached.count() == 0 && (!cached.craftabilityKnown() || !cached.craftable()))
                    || (queryCraftability && !cached.craftabilityKnown())) {
                if (queryCraftability) {
                    withCraftability.add(it);
                } else {
                    countOnly.add(it);
                }
            }
        }
        for (var key : flushedKeys) {
            pendingBatch.remove(key);
        }

        if (countOnly.isEmpty() && withCraftability.isEmpty()) {
            ModLogger.debug("Batch: {} flush skipped (all items already cached)",
                    priorityOnly ? "priority" : "regular");
            return;
        }

        try {
            if (!countOnly.isEmpty()) {
                ClientPacketHelper.sendToServer(new AEBatchQueryPacket(countOnly, false));
            }
            if (!withCraftability.isEmpty()) {
                ClientPacketHelper.sendToServer(new AEBatchQueryPacket(withCraftability, true));
            }
            ModLogger.debug("Batch: flushed {} count-only items and {} craftability items ({})",
                    countOnly.size(), withCraftability.size(), priorityOnly ? "priority" : "regular");
        } catch (Exception e) {
            ModLogger.warn("AEQuery: server doesn't have EmiLink, disabling cache");
            current.cache.clear();
        }
    }

    public static void receiveResponse(ItemStack stack, long count, boolean craftable) {
        receiveResponse(stack, count, craftable, true);
    }

    public static void receiveResponse(ItemStack stack, long count, boolean craftable, boolean craftabilityKnown) {
        if (stack == null || stack.isEmpty()) return;
        var keyStack = normalizeKey(stack);
        var key = cacheKey(keyStack);
        boolean ephemeral = ephemeralKeys.contains(key);
        ModLogger.debug("AE cache: response item={} count={} ephemeralMatch={} ephemeralKeys={} craftable={} known={}",
                stack.getHoverName().getString(), count, ephemeral, ephemeralKeys.size(), craftable, craftabilityKnown);
        if (ephemeral) {
            ephemeralCache.put(key, new CachedInfo(count, craftable, craftabilityKnown, System.currentTimeMillis()));
            return;
        }
        current.cache.put(key, new CachedEntry(keyStack,
                new CachedInfo(count, craftable, craftabilityKnown, System.currentTimeMillis())));
        cacheDirty = true;

        if (!craftabilityKnown
                && EmiLinkConfig.ENABLE_NETWORK_BADGES.get()
                && EmiLinkConfig.ENABLE_CRAFTABLE_NETWORK_BADGES.get()
                && hasAEAccess()) {
            submitForBatch(keyStack, true);
        }
    }

    /** Remove cache entries so the next getCachedResult returns not-found. */
    public static void invalidateEntries(java.util.Collection<ItemStack> stacks) {
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            var key = cacheKey(normalizeKey(stack));
            ephemeralKeys.remove(key);
            ephemeralCache.remove(key);
            current.cache.remove(key);
        }
        cacheDirty = true;
    }

    /**
     * Re-query one item after a successful network transfer. The transfer itself
     * is sent immediately by the caller, so the query is deferred briefly to
     * ensure the server processes the transfer first. Repeated transfers in the
     * same interaction are merged into one forced query.
     */
    public static void refreshAfterNetworkChange(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!hasAEAccess()) {
            ModLogger.debug("AE cache: deferred refresh for {} (no AE terminal or wireless terminal)",
                    stack.getHoverName().getString());
            return;
        }
        boolean queryCraftability = EmiLinkConfig.ENABLE_NETWORK_BADGES.get()
                && EmiLinkConfig.ENABLE_CRAFTABLE_NETWORK_BADGES.get();
        submitForBatch(stack, queryCraftability, true);
        long refreshDelay = batchFlushMs();
        networkRefreshAt = System.currentTimeMillis() + refreshDelay;
        ModLogger.debug("AE cache: scheduled refresh after network change for {} in {}ms",
                stack.getHoverName().getString(), refreshDelay);
    }

    /**
     * Public result record for network overlay rendering.
     */
    public record CachedResult(long count, boolean craftable, boolean found) {}

    /**
     * Public accessor for network overlay rendering. Returns cached AE network info
     * for the given stack, or {@code CachedResult(0, false, false)} if not cached.
     */
    public static CachedResult getCachedResult(ItemStack stack) {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return new CachedResult(0, false, false);
        if (stack == null || stack.isEmpty()) return new CachedResult(0, false, false);
        var cached = findCached(stack);
        if (cached != null) {
            return new CachedResult(cached.count(), cached.craftabilityKnown() && cached.craftable(), true);
        }
        return new CachedResult(0, false, false);
    }

    /**
     * Returns cached AE network info only if it was received at or after the
     * given timestamp. This is used by quick-craft preflight so stale hover/disk
     * cache entries cannot satisfy a forced refresh.
     */
    public static CachedResult getCachedResultSince(ItemStack stack, long minTimestamp) {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return new CachedResult(0, false, false);
        if (stack == null || stack.isEmpty()) return new CachedResult(0, false, false);
        var cached = findCached(stack);
        if (cached != null && cached.timestamp() >= minTimestamp) {
            return new CachedResult(cached.count(), cached.craftabilityKnown() && cached.craftable(), true);
        }
        return new CachedResult(0, false, false);
    }

    /**
     * Cache lookup by content key. This stays O(1) per visible item while still
     * matching the complete component set. {@link #normalizeKey} removes only
     * vanilla DAMAGE for damageable items, preserving all other components.
     */
    private static CachedInfo findCached(ItemStack stack) {
        var key = lookupKey(stack);
        var e = ephemeralCache.get(key);
        if (e != null) return e;
        var d = current.cache.get(key);
        return d == null ? null : d.info();
    }

    /** Quick check if the cache has any entries (avoids iterating visible items). */
    public static boolean hasAnyCached() {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return false;
        return !ephemeralCache.isEmpty() || !current.cache.isEmpty();
    }

    private static ItemStack normalizeKey(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        if (copy.isDamageableItem()) {
            copy.remove(net.minecraft.core.component.DataComponents.DAMAGE);
        }
        return copy;
    }

    private static CacheKey cacheKey(ItemStack stack) {
        return new CacheKey(stack.getItem(), stack.getComponents());
    }

    /**
     * Build a lookup key without copying ordinary stacks every render frame.
     * A copy is needed only when the damage component must be ignored.
     */
    private static CacheKey lookupKey(ItemStack stack) {
        if (stack.isDamageableItem() && stack.has(net.minecraft.core.component.DataComponents.DAMAGE)) {
            return cacheKey(normalizeKey(stack));
        }
        return cacheKey(stack);
    }

    public static boolean hasAEAccess() {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return false;
        if (!BDShortcutHandler.serverHasMod) return false;
        var mc = Minecraft.getInstance();
        if (mc.screen == accessCheckScreen) return accessCheckResult;
        accessCheckScreen = mc.screen;
        accessCheckResult = computeAEAccess(mc);
        return accessCheckResult;
    }

    private static boolean computeAEAccess(Minecraft mc) {
        if (!AE2Proxy.isLoaded()) return false;

        var player = mc.player;
        if (player == null) return false;

        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) {
            return true;
        }
        if (mc.screen != null && AE2Proxy.isCraftConfirmScreen(mc.screen)) {
            return true;
        }

        var inv = player.getInventory();
        for (var item : inv.items) {
            if (AE2Proxy.isWirelessTerminal(item)) return true;
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) return true;

        Class<?> wtClass = AE2Proxy.getWirelessTerminalClass();
        return wtClass != null && CuriosProxy.hasWirelessTerminal(player, wtClass);
    }
}
