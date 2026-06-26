package org.chatterjay.emilink.client.handler;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.Emilink;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.network.NetworkHandler;
import org.chatterjay.emilink.network.packet.c2s.AEBatchQueryPacket;
import org.chatterjay.emilink.network.packet.s2c.ServerHasModPacket;
import org.chatterjay.emilink.util.ModLogger;

import javax.annotation.Nullable;
import java.util.*;

@Mod.EventBusSubscriber(modid = Emilink.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AENetworkCache {

    private static final Map<String, ServerState> serverStates = new HashMap<>();
    private static String currentServerId = "local";
    private static ServerState current = new ServerState();

    private static final Set<ItemStack> pendingBatch = new HashSet<>();
    private static long lastBatchFlushTime = 0;

    private static boolean wasInTerminal = false;
    private static boolean needsInitialScan = false;

    private static Screen accessCheckScreen = null;
    private static boolean accessCheckResult = false;

    private static int hoverTickCounter = 0;
    private static final int HOVER_SAMPLE_INTERVAL = 10;

    private AENetworkCache() {}

    private record CachedInfo(long count, boolean craftable, long timestamp) {
        boolean isExpired(boolean negative) {
            long ttl = negative ? Config.NEGATIVE_CACHE_TTL_MS.get() : Config.CACHE_TTL_MS.get();
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    private static class ServerState {
        final Map<ItemStack, CachedInfo> cache = new HashMap<>();
    }

    // ---- Batch query -------------------------------------------------------

    public static void submitForBatch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        pendingBatch.add(stack.copyWithCount(1));
    }

    public static boolean consumeInitialScanFlag() {
        if (needsInitialScan) {
            needsInitialScan = false;
            return true;
        }
        return false;
    }

    public static void flushBatchNow() {
        if (!pendingBatch.isEmpty()) {
            lastBatchFlushTime = System.currentTimeMillis();
            flushBatch();
        }
    }

    // ---- Events ------------------------------------------------------------

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
        lastBatchFlushTime = 0;
        wasInTerminal = false;
        needsInitialScan = false;
        accessCheckScreen = null;
        hoverTickCounter = 0;
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingBatch.clear();
        accessCheckScreen = null;
    }

    // ---- Public API --------------------------------------------------------

    public static void clear() {
        current.cache.clear();
        pendingBatch.clear();
        lastBatchFlushTime = 0;
        wasInTerminal = false;
        needsInitialScan = false;
        accessCheckScreen = null;
        hoverTickCounter = 0;
    }

    public static void clearAll() {
        serverStates.clear();
        current = new ServerState();
        currentServerId = "local";
        pendingBatch.clear();
        lastBatchFlushTime = 0;
        wasInTerminal = false;
        needsInitialScan = false;
        accessCheckScreen = null;
        hoverTickCounter = 0;
    }

    public static void receiveResponse(ItemStack stack, long count, boolean craftable) {
        if (stack == null || stack.isEmpty()) return;
        var key = stack.copyWithCount(1);
        current.cache.put(key, new CachedInfo(count, craftable, System.currentTimeMillis()));
    }

    public static long getCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        var cached = findCached(stack);
        return cached != null ? cached.count() : -1;
    }

    @Nullable
    public static Boolean getCraftable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var cached = findCached(stack);
        return cached != null ? cached.craftable() : null;
    }

    public record CachedResult(long count, boolean craftable, boolean found) {}

    public static CachedResult getCachedResult(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CachedResult(0, false, false);
        var cached = findCached(stack);
        if (cached != null) return new CachedResult(cached.count(), cached.craftable(), true);
        return new CachedResult(0, false, false);
    }

    public static void addToTooltip(ItemStack stack, List<ClientTooltipComponent> list) {
        if (stack == null || stack.isEmpty()) return;
        var cached = findCached(stack);
        if (cached == null) return;
        if (cached.count() <= 0 && !cached.craftable()) return;

        if (cached.count() > 0) {
            list.add(EmiTooltipComponents.of(
                    Component.translatable("ae_tooltip.count", cached.count())
                            .withStyle(ChatFormatting.GRAY)));
        }
        if (cached.craftable()) {
            list.add(EmiTooltipComponents.of(
                    Component.translatable("ae_tooltip.craftable")
                            .withStyle(ChatFormatting.GREEN)));
        }
    }

    public static boolean hasAEAccess() {
        var mc = Minecraft.getInstance();
        if (mc.screen == accessCheckScreen) return accessCheckResult;
        accessCheckScreen = mc.screen;
        accessCheckResult = computeAEAccess(mc);
        return accessCheckResult;
    }

    public static boolean hasAnyCached() {
        return !current.cache.isEmpty();
    }

    // ---- Internal ----------------------------------------------------------

    private static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) return;

        if (!canQueryNetwork(mc)) {
            if (wasInTerminal) {
                wasInTerminal = false;
                needsInitialScan = false;
            }
            if (!pendingBatch.isEmpty()) {
                pendingBatch.clear();
            }
            return;
        }

        if (!wasInTerminal) {
            wasInTerminal = true;
            needsInitialScan = true;
            lastBatchFlushTime = 0;
        }

        if (!ServerHasModPacket.serverHasMod) {
            current.cache.clear();
            pendingBatch.clear();
            return;
        }

        if (++hoverTickCounter % HOVER_SAMPLE_INTERVAL == 0) {
            var hovered = EmiApi.getHoveredStack(true);
            if (hovered != null && !hovered.isEmpty()) {
                var stack = hovered.getStack().getEmiStacks().stream()
                        .map(EmiStack::getItemStack)
                        .filter(s -> !s.isEmpty())
                        .findFirst()
                        .orElse(ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    pendingBatch.add(stack.copyWithCount(1));
                }
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastBatchFlushTime >= Config.BATCH_FLUSH_MS.get() && !pendingBatch.isEmpty()) {
            flushBatch();
            lastBatchFlushTime = now;
        }
    }

    private static void flushBatch() {
        var toQuery = new ArrayList<ItemStack>();
        for (var it : pendingBatch) {
            var cached = findCached(it);
            if (cached == null || cached.isExpired(cached.count() == 0 && !cached.craftable())) {
                toQuery.add(it);
            }
        }
        pendingBatch.clear();

        if (toQuery.isEmpty()) return;

        try {
            NetworkHandler.sendToServer(new AEBatchQueryPacket(toQuery));
            ModLogger.debug("Batch: flushed {} items", toQuery.size());
        } catch (Exception e) {
            ModLogger.warn("AEQuery: server missing EmiLink, disabling cache");
            current.cache.clear();
        }
    }

    private static boolean canQueryNetwork(Minecraft mc) {
        if (!AE2Proxy.isLoaded()) return false;
        if (mc.player == null) return false;
        if (mc.screen == null) return false;
        return AE2Proxy.isMEStorageScreen(mc.screen) || AE2Proxy.isCraftConfirmScreen(mc.screen);
    }

    private static String resolveServerId() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isEmpty()) {
            return serverData.ip;
        }
        return "local";
    }

    @Nullable
    private static CachedInfo findCached(ItemStack stack) {
        var key = stack.copyWithCount(1);
        var direct = current.cache.get(key);
        if (direct != null) {
            if (direct.isExpired(direct.count() == 0 && !direct.craftable())) {
                current.cache.remove(key);
                return null;
            }
            return direct;
        }
        return null;
    }

    private static boolean computeAEAccess(Minecraft mc) {
        if (!AE2Proxy.isLoaded()) return false;
        var player = mc.player;
        if (player == null) return false;

        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) return true;
        if (mc.screen != null && AE2Proxy.isCraftConfirmScreen(mc.screen)) return true;

        for (var item : player.getInventory().items) {
            if (AE2Proxy.isWirelessTerminal(item)) return true;
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) return true;
        return false;
    }

    public static void save() {
        // no-op: cache persistence removed
    }
}
