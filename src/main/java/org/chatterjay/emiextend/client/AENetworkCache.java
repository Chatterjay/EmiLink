package org.chatterjay.emiextend.client;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.CuriosProxy;
import org.chatterjay.emiextend.network.packet.c2s.AEQueryPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.util.ModLogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = EmiAE2.MODID, value = Dist.CLIENT)
public final class AENetworkCache {
    private static final long DEBOUNCE_MS = 250;
    private static final long CACHE_TTL_MS = 20_000;
    private static final long NEGATIVE_CACHE_TTL_MS = 10_000;

    private static final Map<ItemStack, CachedInfo> cache = new HashMap<>();
    private static ItemStack lastHoveredStack = ItemStack.EMPTY;
    private static long lastHoverChangeTime = 0;
    private static long lastQueryTime = 0;

    private AENetworkCache() {}

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        tick();
    }

    private record CachedInfo(long count, boolean craftable, long timestamp) {
        boolean isExpired(boolean negative) {
            long ttl = negative ? NEGATIVE_CACHE_TTL_MS : CACHE_TTL_MS;
            return System.currentTimeMillis() - timestamp > ttl;
        }
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen == null) return;

        if (!hasAEAccess(mc)) return;

        var hovered = EmiApi.getHoveredStack(true);
        if (hovered == null || hovered.isEmpty()) {
            lastHoveredStack = ItemStack.EMPTY;
            return;
        }

        var stack = hovered.getStack().getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            lastHoveredStack = ItemStack.EMPTY;
            return;
        }

        stack = stack.copyWithCount(1);

        if (!ItemStack.isSameItemSameComponents(stack, lastHoveredStack)) {
            lastHoveredStack = stack;
            lastHoverChangeTime = System.currentTimeMillis();
            return;
        }

        long elapsed = System.currentTimeMillis() - lastHoverChangeTime;
        if (elapsed < DEBOUNCE_MS) return;

        if (System.currentTimeMillis() - lastQueryTime < DEBOUNCE_MS) return;

        var cached = cache.get(stack);
        if (cached != null) {
            boolean negative = cached.count() == 0 && !cached.craftable();
            if (!cached.isExpired(negative)) return;
        }

        lastQueryTime = System.currentTimeMillis();
        PacketDistributor.sendToServer(new AEQueryPacket(stack));
        ModLogger.debug("AENetworkCache: querying {}", stack.getHoverName().getString());
    }

    public static void receiveResponse(ItemStack stack, long count, boolean craftable) {
        if (stack == null || stack.isEmpty()) return;
        var key = stack.copyWithCount(1);
        cache.put(key, new CachedInfo(count, craftable, System.currentTimeMillis()));
        ModLogger.debug("AENetworkCache: cached {} → count={} craftable={}",
                stack.getHoverName().getString(), count, craftable);
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
        for (var entry : cache.entrySet()) {
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
