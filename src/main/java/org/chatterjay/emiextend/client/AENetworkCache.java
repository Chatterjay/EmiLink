
package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import io.netty.buffer.Unpooled;
import appeng.util.ReadableNumberConverter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
import java.util.List;
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
    private static final Set<ItemStack> ephemeralKeys = new HashSet<>();
    private static final Map<ItemStack, CachedInfo> ephemeralCache = new HashMap<>();

    /** Items pending server query. Value is true when craftability should be queried too. */
    private static final Map<ItemStack, Boolean> pendingBatch = new HashMap<>();
    private static long lastBatchFlushTime = 0;

    /** Tracks terminal open/close state for initial scan detection. */
    private static boolean wasInTerminal = false;
    private static boolean needsInitialScan = false;

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
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return;
        if (stack == null || stack.isEmpty()) return;
        var key = normalizeKey(stack);
        pendingBatch.put(key, pendingBatch.getOrDefault(key, false) || queryCraftability);
    }

    /** Begin a temporary query scope whose results should be discarded after use. */
    public static void beginEphemeralQuery(Collection<ItemStack> stacks) {
        clearEphemeralQuery();
        if (stacks == null || stacks.isEmpty()) return;
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            ephemeralKeys.add(normalizeKey(stack));
        }
    }

    /** Discard temporary query results so they cannot affect later rendering or tooltip state. */
    public static void clearEphemeralQuery() {
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

    /** Immediately flush the pending batch (used after initial scan collection). */
    public static void flushBatchNow() {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return;
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
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, mapEntry.getKey());
                buf.writeVarLong(mapEntry.getValue().count());
                buf.writeBoolean(mapEntry.getValue().craftable());
                buf.writeBoolean(mapEntry.getValue().craftabilityKnown());
                buf.writeVarLong(mapEntry.getValue().timestamp());
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
                        state.cache.put(stack, new CachedInfo(count, craftable, craftabilityKnown, timestamp));
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
        currentServerId = resolveServerId();
        serverStates.remove(currentServerId);
        current = serverStates.computeIfAbsent(currentServerId, k -> new ServerState());
        pendingBatch.clear();
        clearEphemeralQuery();
        lastBatchFlushTime = 0;
        wasInTerminal = false;
        needsInitialScan = false;
        cacheDirty = false;
        accessCheckScreen = null;
        hoverTickCounter = 0;
        loadFromDisk();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        saveToDisk();
        pendingBatch.clear();
        clearEphemeralQuery();
        accessCheckScreen = null;
    }

    /** Clear cache & pending batch for the current server only. */
    public static void clear() {
        current.cache.clear();
        pendingBatch.clear();
        clearEphemeralQuery();
        lastBatchFlushTime = 0;
        wasInTerminal = false;
        needsInitialScan = false;
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

    private static class ServerState {
        final Map<ItemStack, CachedInfo> cache = new HashMap<>();
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
            if (wasInTerminal || !pendingBatch.isEmpty() || current.cache.size() > 0) {
                wasInTerminal = false;
                needsInitialScan = false;
                pendingBatch.clear();
                current.cache.clear();
                clearEphemeralQuery();
                accessCheckScreen = null;
            }
            return;
        }

        // Only send queries when actually in an AE2 terminal
        if (!canQueryNetwork(mc)) {
            if (wasInTerminal) {
                wasInTerminal = false;
                needsInitialScan = false;
            }
            if (!pendingBatch.isEmpty()) {
                pendingBatch.clear();
                ModLogger.debug("Batch: cleared pending (not in terminal)");
            }
            return;
        }

        // Detect terminal just opened → signal mixin to collect visible items
        if (!wasInTerminal) {
            wasInTerminal = true;
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

        // Flush batch on timer
        long now = System.currentTimeMillis();
        if (now - lastBatchFlushTime >= batchFlushMs() && !pendingBatch.isEmpty()) {
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
     * then send the batch query to the server.
     */
    private static void flushBatch() {
        var countOnly = new ArrayList<ItemStack>();
        var withCraftability = new ArrayList<ItemStack>();
        for (var entry : pendingBatch.entrySet()) {
            var it = entry.getKey();
            boolean queryCraftability = entry.getValue();
            var cached = findCached(it);
            if (cached == null
                    || cached.isExpired(cached.count() == 0 && (!cached.craftabilityKnown() || !cached.craftable()))
                    || (queryCraftability && !cached.craftabilityKnown())) {
                if (queryCraftability) {
                    withCraftability.add(it);
                } else {
                    countOnly.add(it);
                }
            }
        }
        pendingBatch.clear();

        if (countOnly.isEmpty() && withCraftability.isEmpty()) {
            ModLogger.debug("Batch: flush skipped (all items already cached)");
            return;
        }

        try {
            if (!countOnly.isEmpty()) {
                PacketDistributor.sendToServer(new AEBatchQueryPacket(countOnly, false));
            }
            if (!withCraftability.isEmpty()) {
                PacketDistributor.sendToServer(new AEBatchQueryPacket(withCraftability, true));
            }
            ModLogger.debug("Batch: flushed {} count-only items and {} craftability items",
                    countOnly.size(), withCraftability.size());
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
        var key = normalizeKey(stack);
        if (containsMatching(ephemeralKeys, key)) {
            removeMatching(ephemeralCache, key);
            ephemeralCache.put(key, new CachedInfo(count, craftable, craftabilityKnown, System.currentTimeMillis()));
            return;
        }
        current.cache.put(key, new CachedInfo(count, craftable, craftabilityKnown, System.currentTimeMillis()));
        cacheDirty = true;
    }

    /** Remove cache entries so the next getCachedResult returns not-found. */
    public static void invalidateEntries(java.util.Collection<ItemStack> stacks) {
        for (var stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            ephemeralKeys.removeIf(entry -> ItemStack.isSameItemSameComponents(entry, stack));
            ephemeralCache.entrySet().removeIf(entry ->
                    ItemStack.isSameItemSameComponents(entry.getKey(), stack));
            current.cache.entrySet().removeIf(entry ->
                    ItemStack.isSameItemSameComponents(entry.getKey(), stack));
        }
        cacheDirty = true;
    }

    public static void addToTooltip(ItemStack stack, List<ClientTooltipComponent> list) {
        if (stack == null || stack.isEmpty()) return;
        var cached = findCached(stack);
        if (cached == null) return;
        if (cached.count() <= 0 && !cached.craftable()) return;

        if (cached.count() > 0) {
            list.add(EmiTooltipComponents.of(
                    Component.translatable("ae_tooltip.count", formatNetworkAmount(cached.count()))
                            .withStyle(ChatFormatting.GRAY)));
        }
        if (cached.craftabilityKnown() && cached.craftable()) {
            list.add(EmiTooltipComponents.of(
                    Component.translatable("ae_tooltip.craftable")
                            .withStyle(ChatFormatting.GREEN)));
        }
    }

    /**
     * Public result record for badge rendering.
     */
    public record CachedResult(long count, boolean craftable, boolean found) {}

    public static String formatNetworkAmount(long count) {
        if (count <= 0) {
            return "0";
        }
        try {
            return ReadableNumberConverter.format(count, 4);
        } catch (Throwable ignored) {
            return fallbackFormatNetworkAmount(count);
        }
    }

    private static String fallbackFormatNetworkAmount(long count) {
        if (count < 1_000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            return compactAmount(count, 1_000L, "K");
        }
        if (count < 1_000_000_000L) {
            return compactAmount(count, 1_000_000L, "M");
        }
        return compactAmount(count, 1_000_000_000L, "G");
    }

    private static String compactAmount(long count, long unit, String suffix) {
        long whole = count / unit;
        long tenths = (count % unit) * 10 / unit;
        if (whole >= 10 || tenths == 0) {
            return whole + suffix;
        }
        return whole + "." + tenths + suffix;
    }

    /**
     * Public accessor for corner badge rendering. Returns cached AE network info
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
     * Cache lookup. ItemStack.hashCode/equals in 1.21.1 ignores count,
     * so keys stored with copyWithCount(1) are found by any count variant.
     * Falls back to linear scan for collision/component edge cases.
     */
    private static CachedInfo findCached(ItemStack stack) {
        var ephemeral = ephemeralCache.get(stack);
        if (ephemeral != null) return ephemeral;
        for (var entry : ephemeralCache.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                return entry.getValue();
            }
        }

        var direct = current.cache.get(stack);
        if (direct != null) return direct;
        // Fallback: linear scan for hash-colliding or component-mismatched entries
        for (var entry : current.cache.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Quick check if the cache has any entries (avoids iterating visible items). */
    public static boolean hasAnyCached() {
        if (!EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()) return false;
        return !ephemeralCache.isEmpty() || !current.cache.isEmpty();
    }

    private static ItemStack normalizeKey(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    private static boolean containsMatching(Set<ItemStack> stacks, ItemStack target) {
        if (stacks.contains(target)) return true;
        for (var stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, target)) {
                return true;
            }
        }
        return false;
    }

    private static void removeMatching(Map<ItemStack, CachedInfo> cache, ItemStack target) {
        cache.entrySet().removeIf(entry -> ItemStack.isSameItemSameComponents(entry.getKey(), target));
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
