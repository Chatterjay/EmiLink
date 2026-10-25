package org.chatterjay.emiextend.integration;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reflection bridge to ExtendedAE Plus' client-side network snapshot.
 * <p>
 * ExtendedAE Plus 1.6.x syncs the amounts and craftability of every AE key in
 * the player's network to {@code com.extendedae_plus.client.jei.NetworkItemCache}
 * and paints them onto the JEI ingredient list. JEI-only rendering means an EMI
 * player on a server that runs ExtendedAE Plus but not EmiLink would otherwise
 * see nothing, so EmiLink reads that snapshot instead of sending its own
 * queries, and follows ExtendedAE Plus' {@code jeiNetworkOverlayEnabled} client
 * option as the switch for the EMI overlay.
 * <p>
 * ExtendedAE Plus is an optional dependency: every type is reached reflectively
 * and any failure simply disables this bridge. When the server also runs
 * EmiLink the bridge is never consulted — EmiLink keeps using its own cache.
 */
public final class EAEPNetworkBridge {

    private static final String MOD_ID = "extendedae_plus";
    private static final String CACHE_CLASS = "com.extendedae_plus.client.jei.NetworkItemCache";
    private static final String CONFIG_CLASS = "com.extendedae_plus.config.ModConfigs";
    private static final String OVERLAY_TOGGLE_FIELD = "JEI_NETWORK_OVERLAY_ENABLED";

    /** Identity cache: EMI reuses the wrapper stacks it resolved, so this stays small. */
    private static final int MAX_MEMOIZED_KEYS = 4096;
    private static final Map<ItemStack, Object> keyMemo = new IdentityHashMap<>();

    private static Boolean loaded;
    private static boolean reflectionChecked;
    private static boolean reflectionAvailable;

    private static Object cacheInstance;
    private static Method getAmountMethod;
    private static Method isCraftableMethod;
    private static Method isConnectedMethod;

    private static Object overlayToggle;
    private static Method toggleGetMethod;

    private static Method unwrapItemStackMethod;
    private static Method genericStackWhatMethod;
    private static Method aeItemKeyOfMethod;

    /** Private maps of EAEP's cache, read only for diagnostics. */
    private static Field bySerialField;
    private static Field keyToSerialField;


    private EAEPNetworkBridge() {}

    /** Whether ExtendedAE Plus is installed on this client. */
    public static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded(MOD_ID);
        }
        return loaded;
    }

    /**
     * ExtendedAE Plus' own switch for network numbers on ingredients. EMI follows
     * the same option so a player who turned EAEP's overlay off does not get one
     * from EmiLink instead.
     */
    public static boolean isOverlayEnabled() {
        if (!init()) return false;
        if (overlayToggle == null || toggleGetMethod == null) {
            // An EAEP build without the option always renders its overlay.
            return true;
        }
        try {
            return !(toggleGetMethod.invoke(overlayToggle) instanceof Boolean enabled) || enabled;
        } catch (Throwable error) {
            ModLogger.debug("EAEP bridge: overlay toggle unreadable: {}", error.toString());
            return true;
        }
    }

    /** Whether the server actually pushed a network snapshot to this client. */
    public static boolean isServerSynced() {
        if (!init()) return false;
        try {
            return isConnectedMethod.invoke(cacheInstance) instanceof Boolean connected && connected;
        } catch (Throwable error) {
            ModLogger.debug("EAEP bridge: sync state unreadable: {}", error.toString());
            return false;
        }
    }

    /**
     * Number of AE keys EAEP currently holds, or {@code -1} when the diagnostic
     * map cannot be read. A synced but empty snapshot means the server told the
     * client "no network for you" (EAEP syncs only for a linked wireless
     * terminal), which must not be treated as usable data.
     */
    public static int cachedKeyCount() {
        if (!init()) return -1;
        return mapSize(keyToSerialField);
    }

    /** True when EmiLink should render EMI network information from EAEP data. */
    public static boolean isActiveSource() {
        if (!isLoaded() || !isServerSynced() || !isOverlayEnabled()) return false;
        // -1 means "unknown", keep the source active; 0 means the snapshot is
        // empty, so there is nothing to draw.
        return cachedKeyCount() != 0;
    }

    /** Raw AE amount for one network key, or 0 when unknown. */
    public static long amountOf(ItemStack networkKey) {
        Object key = aeKeyOf(networkKey);
        if (key == null) return 0L;
        try {
            return getAmountMethod.invoke(cacheInstance, key) instanceof Number amount ? amount.longValue() : 0L;
        } catch (Throwable error) {
            ModLogger.debug("EAEP bridge: amount lookup failed: {}", error.toString());
            return 0L;
        }
    }

    /** Whether the network has a pattern for one network key. */
    public static boolean isCraftableOf(ItemStack networkKey) {
        Object key = aeKeyOf(networkKey);
        if (key == null) return false;
        try {
            return isCraftableMethod.invoke(cacheInstance, key) instanceof Boolean craftable && craftable;
        } catch (Throwable error) {
            ModLogger.debug("EAEP bridge: craftability lookup failed: {}", error.toString());
            return false;
        }
    }

    /** Drop memoized AE keys; called when the client leaves a server. */
    public static void clearMemo() {
        keyMemo.clear();
    }

    // ---- Diagnostics -----------------------------------------------------

    /** One-line state used by the debug log and the `/emilink eaepstatus` command. */
    public static String describeState() {
        if (!isLoaded()) return "loaded=false";
        if (!init()) return "loaded=true, reflection=unavailable";
        int keys = mapSize(keyToSerialField);
        String state = "synced=" + isServerSynced()
                + ", overlay=" + isOverlayEnabled()
                + ", entries=" + mapSize(bySerialField)
                + ", keys=" + keys;
        if (keys == 0) {
            // An empty snapshot means the server sent a "clear" packet: EAEP only
            // syncs the network while the player carries a linked wireless terminal.
            state += ", hint=empty snapshot (EAEP syncs only with a linked wireless terminal)";
        }
        return state;
    }

    /** How the bridge turns one EmiLink network key into EAEP's AE key. */
    public static String describeLookupKey(ItemStack networkKey) {
        if (networkKey == null || networkKey.isEmpty()) return "stack=empty";
        Object key = aeKeyOf(networkKey);
        return "stack=" + networkKey.getItem() + " aekey=" + (key == null ? "null" : key.toString());
    }

    /** Sample of the AE keys EAEP currently holds, for comparing against our lookups. */
    public static java.util.List<String> sampleCachedKeys(int limit) {
        var result = new java.util.ArrayList<String>();
        if (limit <= 0 || !init() || keyToSerialField == null) return result;
        try {
            Object map = keyToSerialField.get(cacheInstance);
            if (!(map instanceof java.util.Map<?, ?> keys)) return result;
            for (Object key : keys.keySet()) {
                if (result.size() >= limit) break;
                result.add(String.valueOf(key));
            }
        } catch (Throwable error) {
            ModLogger.debug("EAEP bridge: cache sample failed: {}", error.toString());
        }
        return result;
    }

    private static int mapSize(Field field) {
        if (field == null || cacheInstance == null) return -1;
        try {
            return field.get(cacheInstance) instanceof java.util.Map<?, ?> map ? map.size() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Map an EmiLink cache key back to the AE key EAEP indexed. Item keys are
     * rebuilt from their stack; fluids, chemicals and any other key type come
     * back through AE2's display wrapper.
     */
    private static Object aeKeyOf(ItemStack networkKey) {
        if (networkKey == null || networkKey.isEmpty() || !init()) return null;
        Object memoized = keyMemo.get(networkKey);
        if (memoized != null) return memoized;

        Object resolved = computeAeKey(networkKey);
        if (keyMemo.size() >= MAX_MEMOIZED_KEYS) {
            keyMemo.clear();
        }
        keyMemo.put(networkKey, resolved);
        return resolved;
    }

    private static Object computeAeKey(ItemStack networkKey) {
        try {
            Object genericStack = unwrapItemStackMethod.invoke(null, networkKey);
            if (genericStack != null) {
                Object wrappedKey = genericStackWhatMethod.invoke(genericStack);
                if (wrappedKey != null) {
                    return wrappedKey;
                }
            }
            return aeItemKeyOfMethod.invoke(null, networkKey);
        } catch (Throwable error) {
            ModLogger.debug("EAEP bridge: key conversion failed: {}", error.toString());
            return null;
        }
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Boolean config values expose both a primitive and a boxed reader; prefer the primitive one. */
    private static Method resolveToggleReader(Object toggle) {
        for (String name : new String[]{"getAsBoolean", "get"}) {
            try {
                return toggle.getClass().getMethod(name);
            } catch (NoSuchMethodException ignored) {
                // Try the next reader name.
            }
        }
        return null;
    }

    private static boolean init() {
        if (reflectionChecked) return reflectionAvailable;
        reflectionChecked = true;
        if (!isLoaded()) return false;
        try {
            Class<?> cacheClass = Class.forName(CACHE_CLASS);
            Field instanceField = cacheClass.getField("INSTANCE");
            cacheInstance = instanceField.get(null);

            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            getAmountMethod = cacheClass.getMethod("getAmount", aeKeyClass);
            isCraftableMethod = cacheClass.getMethod("isCraftable", aeKeyClass);
            isConnectedMethod = cacheClass.getMethod("isConnected");

            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            unwrapItemStackMethod = genericStackClass.getMethod("unwrapItemStack", ItemStack.class);
            genericStackWhatMethod = genericStackClass.getMethod("what");
            aeItemKeyOfMethod = Class.forName("appeng.api.stacks.AEItemKey")
                    .getMethod("of", ItemStack.class);

            // Diagnostics only: EAEP keeps its snapshot in these private maps.
            bySerialField = findField(cacheClass, "bySerial");
            keyToSerialField = findField(cacheClass, "keyToSerial");

            try {
                Class<?> configClass = Class.forName(CONFIG_CLASS);
                overlayToggle = configClass.getField(OVERLAY_TOGGLE_FIELD).get(null);
                toggleGetMethod = resolveToggleReader(overlayToggle);
            } catch (Throwable missingToggle) {
                overlayToggle = null;
                toggleGetMethod = null;
            }

            reflectionAvailable = cacheInstance != null;
        } catch (Throwable error) {
            reflectionAvailable = false;
            ModLogger.debug("EAEP bridge unavailable: {}", error.toString());
        }
        return reflectionAvailable;
    }
}
