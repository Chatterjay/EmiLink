package org.chatterjay.emiextend.client;

import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.integration.EAEPNetworkBridge;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * Decides where the EMI network information comes from.
 * <p>
 * Two sources exist:
 * <ul>
 *   <li><b>EmiLink</b> — the connected server reports its own capability, so
 *   EmiLink queries the AE grid itself and caches the answers. EmiLink's own
 *   options (read network, badges, tooltips) stay in charge.</li>
 *   <li><b>ExtendedAE Plus</b> — the server has no EmiLink but does run
 *   ExtendedAE Plus, which already syncs the whole network to the client. In
 *   that case EmiLink renders the same numbers inside EMI and uses ExtendedAE
 *   Plus' {@code jeiNetworkOverlayEnabled} option as the switch, so the player
 *   configures the overlay in one place.</li>
 * </ul>
 * When the server has EmiLink it always wins, even if ExtendedAE Plus also
 * synced a snapshot.
 */
public final class NetworkStockSource {

    /** One network answer: stored amount, craftability, and whether it is known. */
    public record Entry(long count, boolean craftable, boolean found) {
        static final Entry EMPTY = new Entry(0L, false, false);
    }

    /** The EAEP decision reads a client option through reflection, so it is cached briefly. */
    private static final long EAEP_DECISION_TTL_MS = 250L;

    /** Rate limit for the "EAEP has no answer for this key" diagnostic. */
    private static final long MISS_LOG_INTERVAL_MS = 4_000L;
    private static long lastMissLogAt;
    private static boolean eaepDecisionKnown;
    private static boolean eaepDecision;
    private static long eaepDecisionAt;

    private NetworkStockSource() {}

    /** True when the connected server reported EmiLink's own query protocol. */
    public static boolean isEmiLinkSource() {
        return BDShortcutHandler.serverHasMod;
    }

    /** True when an ExtendedAE Plus snapshot is used instead of EmiLink queries. */
    public static boolean isEaepSource() {
        if (isEmiLinkSource()) return false;
        long now = System.currentTimeMillis();
        if (eaepDecisionKnown && now - eaepDecisionAt < EAEP_DECISION_TTL_MS) {
            return eaepDecision;
        }
        eaepDecision = EAEPNetworkBridge.isActiveSource();
        eaepDecisionKnown = true;
        eaepDecisionAt = now;
        return eaepDecision;
    }

    /**
     * Every entry point checks the config first: NeoForge never loads configs
     * when another mod fails during construction, and reading a value then throws.
     */
    private static boolean configReady() {
        return EmiLinkConfig.isClientConfigLoaded();
    }

    /** Whether the EMI overlay (icon amount + craftable cross) should be drawn. */
    public static boolean overlayEnabled() {
        if (!configReady()) return false;
        if (isEmiLinkSource()) {
            return AE2Proxy.isLoaded()
                    && EmiLinkConfig.ENABLE_NETWORK_BADGES.get()
                    && AENetworkCache.hasAEAccess();
        }
        return isEaepSource();
    }

    /** Whether the overlay may also mark craftable keys. EAEP owns that decision itself. */
    public static boolean craftableOverlayEnabled() {
        if (!configReady()) return false;
        if (isEmiLinkSource()) {
            return overlayEnabled() && EmiLinkConfig.ENABLE_CRAFTABLE_NETWORK_BADGES.get();
        }
        return isEaepSource();
    }

    /** Whether network amounts are appended to EMI tooltips. */
    public static boolean tooltipsEnabled() {
        if (!configReady()) return false;
        if (isEmiLinkSource()) {
            return AE2Proxy.isLoaded()
                    && EmiLinkConfig.ENABLE_AE_NETWORK_LOOKUP.get()
                    && EmiLinkConfig.ENABLE_NETWORK_TOOLTIPS.get()
                    && AENetworkCache.hasAEAccess();
        }
        return isEaepSource();
    }

    /**
     * Stored amount and craftability for one network key, item or not. Keys the
     * active source does not know come back as {@link Entry#EMPTY}.
     */
    public static Entry lookup(AENetworkKeyHelper.NetworkKey key) {
        if (!configReady() || key == null) {
            return Entry.EMPTY;
        }
        if (isEmiLinkSource()) {
            var cached = AENetworkCache.getCachedResult(key.stack());
            return new Entry(cached.count(), cached.craftable(), cached.found());
        }
        if (isEaepSource()) {
            long amount = EAEPNetworkBridge.amountOf(key.stack());
            boolean craftable = EAEPNetworkBridge.isCraftableOf(key.stack());
            if (amount <= 0L && !craftable) {
                logEaepMiss(key);
            }
            return new Entry(amount, craftable, amount > 0L || craftable);
        }
        return Entry.EMPTY;
    }

    /** One-line state of the active source, for debug logs and `/emilink eaepstatus`. */
    public static String describeSourceState() {
        if (isEmiLinkSource()) {
            return "emilink[" + AENetworkCache.describeAccessState() + "]";
        }
        return describe() + "[" + EAEPNetworkBridge.describeState() + "]";
    }

    /**
     * Explains an unanswered EAEP lookup: how the key was converted, what EAEP
     * reports about its snapshot, and a few keys it actually holds. Rate limited
     * because this runs from the render path.
     */
    private static void logEaepMiss(AENetworkKeyHelper.NetworkKey key) {
        long now = System.currentTimeMillis();
        if (now - lastMissLogAt < MISS_LOG_INTERVAL_MS) return;
        lastMissLogAt = now;
        ModLogger.debug("EAEP lookup miss: {} | {} | cachedKeys={}",
                EAEPNetworkBridge.describeLookupKey(key.stack()),
                EAEPNetworkBridge.describeState(),
                EAEPNetworkBridge.sampleCachedKeys(3));
    }

    /** Short source name for debug logs. */
    public static String describe() {
        if (isEmiLinkSource()) return "emilink";
        if (isEaepSource()) return "eaep";
        return "none";
    }
}
