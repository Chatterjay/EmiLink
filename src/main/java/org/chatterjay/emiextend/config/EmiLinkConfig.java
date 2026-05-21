package org.chatterjay.emiextend.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.chatterjay.emiextend.util.ModLogger;

public final class EmiLinkConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- General ----
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    // ---- Cache ----
    public static final ModConfigSpec.LongValue CACHE_TTL_MS;
    public static final ModConfigSpec.LongValue NEGATIVE_CACHE_TTL_MS;
    public static final ModConfigSpec.LongValue DEBOUNCE_MS;
    public static final ModConfigSpec.LongValue BATCH_FLUSH_MS;

    // ---- Bookmark Priority ----
    public static final ModConfigSpec.BooleanValue BOOKMARK_PRIORITY;

    // ---- Features ----
    public static final ModConfigSpec.BooleanValue ENABLE_WRAP_BOOK;
    public static final ModConfigSpec.BooleanValue ENABLE_NETWORK_BADGES;
    public static final ModConfigSpec.IntValue NETWORK_BADGE_STYLE;

    // ---- Network ----
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_PACKET_LIMIT;

    static {
        BUILDER.push("general");

        DEBUG_MODE = BUILDER
                .comment("Enable debug logging and debug chat messages")
                .define("debugMode", false);

        BUILDER.pop();
        BUILDER.push("cache");

        CACHE_TTL_MS = BUILDER
                .comment("AE network cache TTL in milliseconds (100-60000)")
                .defineInRange("cacheTTLMs", 5_000L, 100L, 60_000L);

        NEGATIVE_CACHE_TTL_MS = BUILDER
                .comment("Negative cache (item not found) TTL in milliseconds (100-120000)")
                .defineInRange("negativeCacheTTLMs", 10_000L, 100L, 120_000L);

        DEBOUNCE_MS = BUILDER
                .comment("Hover debounce time in milliseconds (50-5000)")
                .defineInRange("debounceMs", 250L, 50L, 5_000L);

        BATCH_FLUSH_MS = BUILDER
                .comment("Batch query flush interval in milliseconds (200-10000). " +
                         "How often pending AE queries are batched and sent to the server.")
                .defineInRange("batchFlushMs", 5_000L, 200L, 10_000L);

        BUILDER.pop();
        BUILDER.push("bookmark_priority");

        BOOKMARK_PRIORITY = BUILDER
                .comment("When encoding processing patterns via EMI recipe transfer, " +
                         "prioritize items from the EMI favorites bar over " +
                         "network-inventory-selected items")
                .define("bookmarkPriority", true);

        BUILDER.pop();
        BUILDER.push("features");

        ENABLE_WRAP_BOOK = BUILDER
                .comment("Enable wrap processing pattern output as written book (WB mode)")
                .define("enableWrapBook", true);

        ENABLE_NETWORK_BADGES = BUILDER
                .comment("Show AE network status corner badges on EMI item icons " +
                         "(green=in stock, yellow=craftable only)")
                .define("enableNetworkBadges", true);

        NETWORK_BADGE_STYLE = BUILDER
                .comment("Badge rendering style when network badges are enabled: " +
                         "1 = bottom-right 6x6 filled square, " +
                         "2 = top-left 6x6 hollow border")
                .defineInRange("networkBadgeStyle", 1, 1, 2);

        BUILDER.pop();
        BUILDER.push("network");

        ENABLE_DEBUG_PACKET_LIMIT = BUILDER
                .comment("Limit debug-related packets to 1 per tick to prevent congestion")
                .define("enableDebugPacketLimit", true);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validated = false;

    private EmiLinkConfig() {}

    /**
     * Validate config values on load. If any value is outside expected range,
     * falls back to spec default and logs a warning. Called once at startup.
     */
    public static void validate() {
        if (validated) return;
        validated = true;

        validateLong(CACHE_TTL_MS, "cache.cacheTTLMs", 5_000L, 100L, 60_000L);
        validateLong(NEGATIVE_CACHE_TTL_MS, "cache.negativeCacheTTLMs", 10_000L, 100L, 120_000L);
        validateLong(DEBOUNCE_MS, "cache.debounceMs", 250L, 50L, 5_000L);
    }

    private static void validateLong(ModConfigSpec.LongValue value, String path, long fallback, long min, long max) {
        long v = value.get();
        if (v < min || v > max) {
            ModLogger.warn("Config '{}' = {} is out of range [{}, {}], falling back to default {}",
                    path, v, min, max, fallback);
            value.set(fallback);
        }
    }

    /** Re-validate on config reload. */
    public static void onReload() {
        validated = false;
        validate();
        ModLogger.info("Configuration reloaded");
    }
}
