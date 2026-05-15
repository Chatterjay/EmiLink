package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import org.chatterjay.emiextend.util.ModLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.CuriosProxy;
import org.chatterjay.emiextend.network.packet.c2s.AEQueryPacket;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AENetworkCache {

    private static final Map<String, ServerState> serverStates = new HashMap<>();
    private static String currentServerId = "local";
    private static ServerState current = new ServerState();

    private AENetworkCache() {}

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        tick();
    }

    @SubscribeEvent
    public static void onClientLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        currentServerId = resolveServerId();
        current = serverStates.computeIfAbsent(currentServerId, k -> new ServerState());
        // Reset hover state so first hover isn't mistaken for item-switch debounce
        current.lastHoveredStack = ItemStack.EMPTY;
        current.lastHoverChangeTime = 0;
        current.lastQueryTime = 0;
    }

    /** Clear cache for the current server only. */
    public static void clear() {
        current.cache.clear();
        current.lastHoveredStack = ItemStack.EMPTY;
        current.lastHoverChangeTime = 0;
        current.lastQueryTime = 0;
    }

    /** Clear cache for all servers (e.g. on disconnect or full reset). */
    public static void clearAll() {
        serverStates.clear();
        current = new ServerState();
        currentServerId = "local";
    }

    private static String resolveServerId() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isEmpty()) {
            return serverData.ip;
        }
        return "local";
    }

    private record CachedInfo(long count, boolean craftable, long timestamp) {
        boolean isExpired(boolean negative) {
            long ttl = negative ? EmiLinkConfig.NEGATIVE_CACHE_TTL_MS.get() : EmiLinkConfig.CACHE_TTL_MS.get();
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    private static class ServerState {
        final Map<ItemStack, CachedInfo> cache = new HashMap<>();
        ItemStack lastHoveredStack = ItemStack.EMPTY;
        long lastHoverChangeTime = 0;
        long lastQueryTime = 0;
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

        // Only send queries when actually in an AE2 terminal (server needs open AEBaseMenu)
        if (!canQueryNetwork(mc)) return;

        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) {
            current.lastHoveredStack = ItemStack.EMPTY;
            return;
        }

        var stack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            current.lastHoveredStack = ItemStack.EMPTY;
            return;
        }

        stack = stack.copyWithCount(1);

        if (!ItemStack.isSameItemSameComponents(stack, current.lastHoveredStack)) {
            current.lastHoveredStack = stack;
            current.lastHoverChangeTime = System.currentTimeMillis();
            return;
        }

        long elapsed = System.currentTimeMillis() - current.lastHoverChangeTime;
        if (elapsed < EmiLinkConfig.DEBOUNCE_MS.get()) return;

        if (System.currentTimeMillis() - current.lastQueryTime < EmiLinkConfig.DEBOUNCE_MS.get()) return;

        var cached = findCached(stack);
        if (cached != null) {
            boolean negative = cached.count() == 0 && !cached.craftable();
            if (!cached.isExpired(negative)) return;
        }

        current.lastQueryTime = System.currentTimeMillis();
        if (!BDShortcutHandler.serverHasMod) {
            current.cache.clear();
            return;
        }
        try {
            PacketDistributor.sendToServer(new AEQueryPacket(stack));
        } catch (Exception e) {
            ModLogger.warn("AEQuery: server doesn't have EmiLink, disabling cache");
            current.cache.clear();
        }
    }

    public static void receiveResponse(ItemStack stack, long count, boolean craftable) {
        if (stack == null || stack.isEmpty()) return;
        var key = stack.copyWithCount(1);
        current.cache.put(key, new CachedInfo(count, craftable, System.currentTimeMillis()));
    }

    public static void addToTooltip(ItemStack stack, List<ClientTooltipComponent> list) {
        if (stack == null || stack.isEmpty()) return;
        var cached = findCached(stack.copyWithCount(1));
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

    /** Linear scan with isSameItemSameComponents — avoids ItemStack hashCode mismatches. */
    private static CachedInfo findCached(ItemStack stack) {
        for (var entry : current.cache.entrySet()) {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static boolean hasAEAccess() {
        return hasAEAccess(Minecraft.getInstance());
    }

    private static boolean hasAEAccess(Minecraft mc) {
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
