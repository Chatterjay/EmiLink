package org.chatterjay.emiextend.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.chatterjay.emiextend.util.ModLogger;

public final class EmiLinkConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public enum ExtractTrigger {
        SHIFT,
        CONTROL,
        ALT,
        OFF
    }

    // ---- General ----
    public static final ForgeConfigSpec.BooleanValue DEBUG_MODE;

    // ---- Cache ----
    public static final ForgeConfigSpec.LongValue CACHE_TTL_MS;
    public static final ForgeConfigSpec.LongValue NEGATIVE_CACHE_TTL_MS;
    public static final ForgeConfigSpec.LongValue DEBOUNCE_MS;
    public static final ForgeConfigSpec.LongValue BATCH_FLUSH_MS;

    // ---- EMI UI ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_WRAP_BOOK;
    public static final ForgeConfigSpec.BooleanValue WB_FILL_INPUT_GRID;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DRAG_FILL;
    public static final ForgeConfigSpec.BooleanValue BOOKMARK_PRIORITY;
    public static final ForgeConfigSpec.IntValue FAVORITE_PAGE_COUNT;

    // ---- AE / Network Storage ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_AE_NETWORK_LOOKUP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_NETWORK_BADGES;
    public static final ForgeConfigSpec.IntValue NETWORK_BADGE_STYLE;
    public static final ForgeConfigSpec.EnumValue<ExtractTrigger> EXTRACT_MODIFIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AE_DEPOSIT;
    public static final ForgeConfigSpec.EnumValue<ExtractTrigger> DEPOSIT_BATCH_MODIFIER;

    // ---- Quick Craft ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUICK_CRAFT_TAB;
    public static final ForgeConfigSpec.EnumValue<ExtractTrigger> QUICK_CRAFT_MODIFIER;
    public static final ForgeConfigSpec.ConfigValue<String> QUICK_CRAFT_KEY;

    // ---- Inventory ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_BULK_TRANSFER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DISCARD_MATCHING_KEY;

    // ---- Network ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_PACKET_LIMIT;

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

        DEBOUNCE_MS = BUILDER
                .comment("Hover debounce time in milliseconds (50-5000)")
                .translation("emilink.config.cache.debounceMs")
                .defineInRange("debounceMs", 250L, 50L, 5_000L);

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
                .define("enableWrapBook", false);

        WB_FILL_INPUT_GRID = BUILDER
                .comment("When wrap book is enabled, also place the original output item into an empty input slot")
                .translation("emilink.config.emi_ui.wbFillInputGrid")
                .define("wbFillInputGrid", false);

        ENABLE_DRAG_FILL = BUILDER
                .translation("emilink.config.emi_ui.enableDragFill")
                .define("enableDragFill", true);

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

    public static final ForgeConfigSpec SPEC = BUILDER.build();

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
        validateLong(BATCH_FLUSH_MS, "cache.batchFlushMs", 5_000L, 200L, 10_000L);
        validateInt(FAVORITE_PAGE_COUNT, "emi_ui.favoritePageCount", 5, 1, 50);
        validateInt(NETWORK_BADGE_STYLE, "ae_network.networkBadgeStyle", 1, 1, 2);
    }

    private static void validateLong(ForgeConfigSpec.LongValue value, String path, long fallback, long min, long max) {
        long v = value.get();
        if (v < min || v > max) {
            ModLogger.warn("Config '{}' = {} is out of range [{}, {}], falling back to default {}",
                    path, v, min, max, fallback);
            value.set(fallback);
        }
    }

    private static void validateInt(ForgeConfigSpec.IntValue value, String path, int fallback, int min, int max) {
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
