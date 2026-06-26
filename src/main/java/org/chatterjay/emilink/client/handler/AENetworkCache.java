package org.chatterjay.emilink.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.client.DiskCacheIO;
import org.chatterjay.emilink.integration.AE2Proxy;
import org.chatterjay.emilink.network.packet.s2c.ServerHasModPacket;
import org.chatterjay.emilink.util.ModLogger;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public final class AENetworkCache {
    private static final ConcurrentHashMap<CacheKey, Entry> cache = new ConcurrentHashMap<>();
    private static final String CACHE_FILE = "ae_network_cache.dat";
    private static boolean loaded = false;

    private AENetworkCache() {}

    public static void clear() {
        save();
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

    public static long getCount(ItemStack stack) {
        ensureLoaded();
        if (stack == null || stack.isEmpty()) return -1;
        Entry entry = getIfValid(stack);
        return entry != null ? entry.count : -1;
    }

    @Nullable
    public static Boolean getCraftable(ItemStack stack) {
        ensureLoaded();
        if (stack == null || stack.isEmpty()) return null;
        Entry entry = getIfValid(stack);
        return entry != null ? entry.craftable : null;
    }

    public record CachedResult(long count, boolean craftable, boolean found) {}

    public static CachedResult getCachedResult(ItemStack stack) {
        ensureLoaded();
        if (stack == null || stack.isEmpty()) return new CachedResult(0, false, false);
        Entry entry = getIfValid(stack);
        if (entry != null) return new CachedResult(entry.count, entry.craftable, true);
        return new CachedResult(0, false, false);
    }

    public static boolean hasAEAccess() {
        if (!ServerHasModPacket.serverHasMod) return false;
        var mc = Minecraft.getInstance();
        if (mc.screen != null && AE2Proxy.isMEStorageScreen(mc.screen)) return true;
        if (mc.screen != null && AE2Proxy.isCraftConfirmScreen(mc.screen)) return true;

        var player = mc.player;
        if (player == null) return false;
        for (var item : player.getInventory().items) {
            if (AE2Proxy.isWirelessTerminal(item)) return true;
        }
        if (AE2Proxy.isWirelessTerminal(player.getOffhandItem())) return true;
        return false;
    }

    /** Saves the current cache to disk via DiskCacheIO. */
    public static void save() {
        try {
            CompoundTag root = new CompoundTag();
            ListTag entriesList = new ListTag();
            long now = System.currentTimeMillis();

            for (var mapEntry : cache.entrySet()) {
                CacheKey key = mapEntry.getKey();
                Entry entry = mapEntry.getValue();
                if (now > entry.expiry) continue; // skip expired

                CompoundTag tag = new CompoundTag();
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(key.item);
                if (itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) continue;
                tag.putString("item", itemId.toString());
                tag.putInt("damage", key.damage);
                if (key.tag != null && !key.tag.isEmpty()) {
                    tag.put("tag", key.tag.copy());
                }
                tag.putLong("count", entry.count);
                tag.putBoolean("craftable", entry.craftable);
                tag.putLong("expiry", entry.expiry);
                entriesList.add(tag);
            }

            root.put("entries", entriesList);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(root, dos);
            dos.flush();
            byte[] data = baos.toByteArray();
            DiskCacheIO.save(getCachePath(), data);
            ModLogger.debug("AE network cache saved ({} entries)", entriesList.size());
        } catch (Exception e) {
            ModLogger.warn("AE network cache save failed: {}", e.getMessage());
        }
    }

    /** Loads the cache from disk via DiskCacheIO. */
    public static void load() {
        try {
            Path path = getCachePath();
            byte[] data = DiskCacheIO.load(path);
            if (data == null) return;

            CompoundTag root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(data)));
            if (root == null) return;

            ListTag entriesList = root.getList("entries", Tag.TAG_COMPOUND);
            long now = System.currentTimeMillis();
            int loadedCount = 0;

            for (int i = 0; i < entriesList.size(); i++) {
                CompoundTag tag = entriesList.getCompound(i);
                String itemStr = tag.getString("item");
                ResourceLocation itemId = ResourceLocation.tryParse(itemStr);
                if (itemId == null) continue;
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item == null || item == BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getDefaultKey())) continue;

                int damage = tag.getInt("damage");
                CompoundTag itemTag = tag.contains("tag", Tag.TAG_COMPOUND) ? tag.getCompound("tag") : null;
                long count = tag.getLong("count");
                boolean craftable = tag.getBoolean("craftable");
                long expiry = tag.getLong("expiry");

                if (now > expiry) continue; // already expired

                CacheKey key = new CacheKey(item, damage, itemTag);
                cache.put(key, new Entry(count, craftable, expiry));
                loadedCount++;
            }

            ModLogger.debug("AE network cache loaded ({} entries)", loadedCount);
        } catch (Exception e) {
            ModLogger.warn("AE network cache load failed: {}", e.getMessage());
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    private static Path getCachePath() {
        var mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("emilink").resolve(CACHE_FILE);
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

    private record CacheKey(Item item, int damage, @Nullable CompoundTag tag) {
        static CacheKey from(ItemStack stack) {
            return new CacheKey(stack.getItem(), stack.getDamageValue(), stack.getTag());
        }
    }

    private record Entry(long count, boolean craftable, long expiry) {}
}
