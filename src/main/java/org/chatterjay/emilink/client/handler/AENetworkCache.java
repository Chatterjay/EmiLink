package org.chatterjay.emilink.client.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.util.ModLogger;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public final class AENetworkCache {
    private static final ConcurrentHashMap<CacheKey, Entry> cache = new ConcurrentHashMap<>();

    private AENetworkCache() {}

    public static void clear() {
        cache.clear();
        ModLogger.debug("AE network cache cleared");
    }

    public static void receiveResponse(ItemStack stack, long count, boolean craftable) {
        if (stack == null || stack.isEmpty()) return;
        CacheKey key = CacheKey.from(stack);
        long ttl = (count == 0 && !craftable)
                ? Config.NEGATIVE_CACHE_TTL_MS.get()
                : Config.CACHE_TTL_MS.get();
        long expiry = System.currentTimeMillis() + ttl;
        cache.put(key, new Entry(count, craftable, expiry));
        ModLogger.debug("Cached: {} count={} craftable={} ttl={}ms",
                stack.getHoverName().getString(), count, craftable, ttl);
    }

    /**
     * @return cached item count, or -1 if not cached or expired
     */
    public static long getCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        Entry entry = getIfValid(stack);
        return entry != null ? entry.count : -1;
    }

    /**
     * @return cached craftable status, or null if not cached or expired
     */
    @Nullable
    public static Boolean getCraftable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Entry entry = getIfValid(stack);
        return entry != null ? entry.craftable : null;
    }

    @Nullable
    private static Entry getIfValid(ItemStack stack) {
        CacheKey key = CacheKey.from(stack);
        Entry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiry) {
            cache.remove(key, entry);
            return null;
        }
        return entry;
    }

    private record CacheKey(Item item, @Nullable CompoundTag tag) {
        static CacheKey from(ItemStack stack) {
            return new CacheKey(stack.getItem(), stack.getTag());
        }
    }

    private record Entry(long count, boolean craftable, long expiry) {}
}
