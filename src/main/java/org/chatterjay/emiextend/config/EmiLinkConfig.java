package org.chatterjay.emiextend.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.chatterjay.emiextend.util.ModLogger;

public final class EmiLinkConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public enum ExtractTrigger {
        SHIFT,
        CONTROL,
        ALT,
        OFF
    }

    public enum SearchHistoryPosition {
        OFF,
        AUTO,
        ABOVE,
        LEFT,
        RIGHT
    }

    // ---- General ----
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    // ---- Cache ----
    public static final ModConfigSpec.LongValue CACHE_TTL_MS;
    public static final ModConfigSpec.LongValue NEGATIVE_CACHE_TTL_MS;
    public static final ModConfigSpec.LongValue BATCH_FLUSH_MS;

    // ---- EMI UI ----
    public static final ModConfigSpec.BooleanValue ENABLE_WRAP_BOOK;
    public static final ModConfigSpec.BooleanValue WB_FILL_INPUT_GRID;
    public static final ModConfigSpec.BooleanValue ENABLE_DRAG_FILL;
    public static final ModConfigSpec.BooleanValue ENABLE_MOB_SEPARATOR;
    public static final ModConfigSpec.BooleanValue BOOKMARK_PRIORITY;
    public static final ModConfigSpec.IntValue FAVORITE_PAGE_COUNT;
    public static final ModConfigSpec.EnumValue<SearchHistoryPosition> SEARCH_HISTORY_POSITION;

    // ---- AE / Network Storage ----
    public static final ModConfigSpec.BooleanValue ENABLE_AE_NETWORK_LOOKUP;
    public static final ModConfigSpec.BooleanValue ENABLE_NETWORK_BADGES;
    public static final ModConfigSpec.BooleanValue ENABLE_CRAFTABLE_NETWORK_BADGES;
    public static final ModConfigSpec.IntValue INITIAL_BADGE_SCAN_LIMIT;
    public static final ModConfigSpec.IntValue NETWORK_BADGE_STYLE;
    public static final ModConfigSpec.EnumValue<ExtractTrigger> EXTRACT_MODIFIER;
    public static final ModConfigSpec.BooleanValue ENABLE_AE_DEPOSIT;
    public static final ModConfigSpec.EnumValue<ExtractTrigger> DEPOSIT_BATCH_MODIFIER;

    // ---- Quick Craft ----
    public static final ModConfigSpec.BooleanValue ENABLE_QUICK_CRAFT_TAB;
    public static final ModConfigSpec.EnumValue<ExtractTrigger> QUICK_CRAFT_MODIFIER;
    public static final ModConfigSpec.ConfigValue<String> QUICK_CRAFT_KEY;

    // ---- Inventory ----
    public static final ModConfigSpec.BooleanValue ENABLE_BULK_TRANSFER;
    public static final ModConfigSpec.BooleanValue ENABLE_DISCARD_MATCHING_KEY;

    // ---- Network ----
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_PACKET_LIMIT;

    static {
        BUILDER.push("general");

        DEBUG_MODE = BUILDER
                .comment("Enable debug logging and debug chat messages")
                .translation("emilink.config.general.debugMode")
                .define("debugMode", false);

        BUILDER.pop();
        BUILDER.push("cache");

        CACHE_TTL_MS = BUILDER
                .comment("AE network cache TTL in milliseconds (100-60000)")
                .translation("emilink.config.cache.cacheTTLMs")
                .defineInRange("cacheTTLMs", 5_000L, 100L, 60_000L);

        NEGATIVE_CACHE_TTL_MS = BUILDER
                .comment("Negative cache (item not found) TTL in milliseconds (100-120000)")
                .translation("emilink.config.cache.negativeCacheTTLMs")
                .defineInRange("negativeCacheTTLMs", 10_000L, 100L, 120_000L);

        BATCH_FLUSH_MS = BUILDER
                .comment("Batch query flush interval in milliseconds (200-10000). " +
                         "How often pending AE queries are batched and sent to the server.")
                .translation("emilink.config.cache.batchFlushMs")
                .defineInRange("batchFlushMs", 5_000L, 200L, 10_000L);

        BUILDER.pop();
        BUILDER.push("emi_ui");

        ENABLE_WRAP_BOOK = BUILDER
                .comment("Enable wrap processing pattern output as written book (WB mode)")
                .translation("emilink.config.emi_ui.enableWrapBook")
                .define("enableWrapBook", true);

        WB_FILL_INPUT_GRID = BUILDER
                .comment("When wrap book is enabled, also place the original output item into an empty input slot")
                .translation("emilink.config.emi_ui.wbFillInputGrid")
                .define("wbFillInputGrid", false);

        ENABLE_DRAG_FILL = BUILDER
                .translation("emilink.config.emi_ui.enableDragFill")
                .define("enableDragFill", true);

        ENABLE_MOB_SEPARATOR = BUILDER
                .comment("Move mob-related EMI index entries to a separated section. " +
                         "Disable by default because it can be expensive in very large modpacks.")
                .translation("emilink.config.emi_ui.enableMobSeparator")
                .define("enableMobSeparator", false);

        BOOKMARK_PRIORITY = BUILDER
                .comment("When encoding processing patterns via EMI recipe transfer, " +
                         "prioritize items from the EMI favorites bar over " +
                         "network-inventory-selected items")
                .translation("emilink.config.emi_ui.bookmarkPriority")
                .define("bookmarkPriority", true);

        FAVORITE_PAGE_COUNT = BUILDER
                .comment("Minimum number of EMI favorites pages kept available")
                .translation("emilink.config.emi_ui.favoritePageCount")
                .defineInRange("favoritePageCount", 5, 1, 50);

        SEARCH_HISTORY_POSITION = BUILDER
                .comment("EMI search history overlay position. " +
                         "OFF disables it; AUTO keeps the adaptive default; " +
                         "ABOVE, LEFT, and RIGHT force a side of the search box.")
                .translation("emilink.config.emi_ui.searchHistoryPosition")
                .defineEnum("searchHistoryPosition", SearchHistoryPosition.AUTO);

        BUILDER.pop();
        BUILDER.push("ae_network");

        ENABLE_AE_NETWORK_LOOKUP = BUILDER
                .comment("Read AE network stored and craftable item counts for EMI tooltips, badges, and quick craft")
                .translation("emilink.config.ae_network.enableAeNetworkLookup")
                .define("enableAeNetworkLookup", true);

        ENABLE_NETWORK_BADGES = BUILDER
                .comment("Show AE network status corner badges on EMI item icons " +
                         "(green=in stock, yellow=craftable only)")
                .translation("emilink.config.ae_network.enableNetworkBadges")
                .define("enableNetworkBadges", false);

        ENABLE_CRAFTABLE_NETWORK_BADGES = BUILDER
                .comment("Also query AE craftability for yellow EMI badges. " +
                         "Disable this on large AE networks to avoid expensive craftable checks when opening terminals.")
                .translation("emilink.config.ae_network.enableCraftableNetworkBadges")
                .define("enableCraftableNetworkBadges", false);

        INITIAL_BADGE_SCAN_LIMIT = BUILDER
                .comment("Maximum EMI sidebar items queried immediately when an AE terminal opens. " +
                         "Set to 0 to disable the initial badge scan.")
                .translation("emilink.config.ae_network.initialBadgeScanLimit")
                .defineInRange("initialBadgeScanLimit", 24, 0, 512);

        NETWORK_BADGE_STYLE = BUILDER
                .comment("Badge rendering style when network badges are enabled: " +
                         "1 = bottom-right 6x6 filled square, " +
                         "2 = top-left 6x6 hollow border")
                .translation("emilink.config.ae_network.networkBadgeStyle")
                .defineInRange("networkBadgeStyle", 1, 1, 2);

        EXTRACT_MODIFIER = BUILDER
                .comment("Modifier for Click-to-extract from AE/BD network. " +
                         "SHIFT=Shift+Click, CONTROL=Ctrl+Click, ALT=Alt+Click, or OFF to disable")
                .translation("emilink.config.ae_network.extractModifier")
                .defineEnum("extractModifier", ExtractTrigger.SHIFT);

        ENABLE_AE_DEPOSIT = BUILDER
                .comment("Click on the EMI sidebar with a carried item to deposit into AE. " +
                         "Requires a wireless terminal or an open AE2 terminal.")
                .translation("emilink.config.ae_network.enableAeDeposit")
                .define("enableAeDeposit", true);

        DEPOSIT_BATCH_MODIFIER = BUILDER
                .translation("emilink.config.ae_network.depositBatchModifier")
                .defineEnum("depositBatchModifier", ExtractTrigger.SHIFT);

        BUILDER.pop();
        BUILDER.push("quick_craft");

        ENABLE_QUICK_CRAFT_TAB = BUILDER
                .translation("emilink.config.quick_craft.enableQuickCraft")
                .define("enableQuickCraftTab", true);

        QUICK_CRAFT_MODIFIER = BUILDER
                .translation("emilink.config.quick_craft.quickCraftModifier")
                .defineEnum("quickCraftModifier", ExtractTrigger.SHIFT);

        QUICK_CRAFT_KEY = BUILDER
                .translation("emilink.config.quick_craft.quickCraftKey")
                .define("quickCraftKey", "C");

        BUILDER.pop();
        BUILDER.push("inventory");

        ENABLE_BULK_TRANSFER = BUILDER
                .comment("Enable Space+Click bulk transfer for regular containers (non-AE, non-BD). " +
                         "Disable if it conflicts with other mods' Space+Click handlers.")
                .translation("emilink.config.inventory.enableBulkTransfer")
                .define("enableBulkTransfer", true);

        ENABLE_DISCARD_MATCHING_KEY = BUILDER
                .comment("Enable Ctrl+Shift+Drop to discard all stacks matching the hovered slot item")
                .translation("emilink.config.inventory.enableDiscardMatchingKey")
                .define("enableDiscardMatchingKey", true);

        BUILDER.pop();
        BUILDER.push("debug");

        ENABLE_DEBUG_PACKET_LIMIT = BUILDER
                .comment("Limit debug-related packets to 1 per tick to prevent congestion")
                .translation("emilink.config.debug.enableDebugPacketLimit")
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
        validateLong(BATCH_FLUSH_MS, "cache.batchFlushMs", 5_000L, 200L, 10_000L);
        validateInt(FAVORITE_PAGE_COUNT, "emi_ui.favoritePageCount", 5, 1, 50);
        validateInt(INITIAL_BADGE_SCAN_LIMIT, "ae_network.initialBadgeScanLimit", 24, 0, 512);
        validateInt(NETWORK_BADGE_STYLE, "ae_network.networkBadgeStyle", 1, 1, 2);
    }

    private static void validateLong(ModConfigSpec.LongValue value, String path, long fallback, long min, long max) {
        long v = value.get();
        if (v < min || v > max) {
            ModLogger.warn("Config '{}' = {} is out of range [{}, {}], falling back to default {}",
                    path, v, min, max, fallback);
            value.set(fallback);
        }
    }

    private static void validateInt(ModConfigSpec.IntValue value, String path, int fallback, int min, int max) {
        int v = value.get();
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
        ModLogger.debug("Configuration reloaded");
    }
}
