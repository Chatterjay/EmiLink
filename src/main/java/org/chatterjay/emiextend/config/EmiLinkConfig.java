package org.chatterjay.emiextend.config;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.chatterjay.emiextend.util.ModLogger;

/**
 * EmiLink configuration.
 * <p>
 * The options are split by the side that reads them:
 * <ul>
 *   <li><b>Client</b> ({@code config/emilink-client.toml}) — everything that
 *   shapes the EMI UI, the network overlay and tooltips, the client-side cache
 *   and the key bindings. This is where almost all options live.</li>
 *   <li><b>Server</b> ({@code config/emilink-server.toml}) — the server-side log
 *   switch. EmiLink answers the client's queries on the server, so the only
 *   server-side behaviour a player may want to change is how much it logs.</li>
 * </ul>
 * Both files are registered in {@code EmiAE2}; values that are not loaded on the
 * current side are simply never read (every reader is guarded).
 */
public final class EmiLinkConfig {
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();

    public enum SearchHistoryPosition {
        OFF,
        AUTO,
        ABOVE,
        LEFT,
        RIGHT
    }

    // ---- General (client) ----
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    // ---- Server ----
    public static final ModConfigSpec.BooleanValue SERVER_DEBUG_MODE;

    // ---- Cache (client) ----
    public static final ModConfigSpec.LongValue CACHE_TTL_MS;
    public static final ModConfigSpec.LongValue NEGATIVE_CACHE_TTL_MS;
    public static final ModConfigSpec.LongValue BATCH_FLUSH_MS;

    // ---- EMI UI (client) ----
    public static final ModConfigSpec.BooleanValue ENABLE_WRAP_BOOK;
    public static final ModConfigSpec.BooleanValue WB_FILL_INPUT_GRID;
    public static final ModConfigSpec.BooleanValue ENABLE_DRAG_FILL;
    public static final ModConfigSpec.BooleanValue ENABLE_MOB_SEPARATOR;
    public static final ModConfigSpec.BooleanValue BOOKMARK_PRIORITY;
    public static final ModConfigSpec.IntValue FAVORITE_PAGE_COUNT;
    public static final ModConfigSpec.EnumValue<SearchHistoryPosition> SEARCH_HISTORY_POSITION;
    public static final ModConfigSpec.BooleanValue TAKE_OVER_EMI_SEARCH;
    public static final ModConfigSpec.BooleanValue ENABLE_COPY_HOVERED_STACK_ID;
    public static final ModConfigSpec.BooleanValue ENABLE_FAVORITE_DRAG_SELECT;
    public static final ModConfigSpec.ConfigValue<String> FAVORITE_DRAG_SELECT_MODIFIER;
    public static final ModConfigSpec.ConfigValue<String> FAVORITE_PROTECTION_BORDER_COLOR;
    public static final ModConfigSpec.IntValue FAVORITE_PROTECTION_BORDER_OPACITY;
    public static final ModConfigSpec.ConfigValue<String> FAVORITE_PROTECTION_BACKGROUND_COLOR;
    public static final ModConfigSpec.IntValue FAVORITE_PROTECTION_BACKGROUND_OPACITY;
    public static final ModConfigSpec.BooleanValue ENABLE_BATCH_MARK;
    public static final ModConfigSpec.ConfigValue<String> BATCH_MARK_BORDER_COLOR;
    public static final ModConfigSpec.IntValue BATCH_MARK_BORDER_OPACITY;
    public static final ModConfigSpec.ConfigValue<String> BATCH_MARK_BACKGROUND_COLOR;
    public static final ModConfigSpec.IntValue BATCH_MARK_BACKGROUND_OPACITY;
    public static final ModConfigSpec.BooleanValue ENABLE_BATCH_MARK_FILL;
    public static final ModConfigSpec.BooleanValue SHARED_EDGE_SPLIT;

    // ---- AE network (client) ----
    public static final ModConfigSpec.BooleanValue ENABLE_AE_NETWORK_LOOKUP;
    public static final ModConfigSpec.BooleanValue EXPOSE_NETWORK_INVENTORY_TO_EMI;
    public static final ModConfigSpec.BooleanValue ENABLE_NETWORK_BADGES;
    public static final ModConfigSpec.BooleanValue ENABLE_NETWORK_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue ENABLE_CRAFTABLE_NETWORK_BADGES;
    public static final ModConfigSpec.IntValue INITIAL_BADGE_SCAN_LIMIT;
    public static final ModConfigSpec.ConfigValue<String> EXTRACT_MODIFIER;
    public static final ModConfigSpec.BooleanValue ENABLE_AE_DEPOSIT;
    public static final ModConfigSpec.ConfigValue<String> DEPOSIT_BATCH_MODIFIER;

    // ---- Quick craft (client) ----
    public static final ModConfigSpec.BooleanValue ENABLE_QUICK_CRAFT_TAB;
    public static final ModConfigSpec.IntValue QUICK_CRAFT_BATCHES_PER_TICK;

    // ---- Inventory (client) ----
    public static final ModConfigSpec.BooleanValue ENABLE_BULK_TRANSFER;
    public static final ModConfigSpec.BooleanValue ENABLE_DISCARD_MATCHING_KEY;

    public static boolean isAutomaticWorkbenchCraftingEnabled() {
        return ENABLE_QUICK_CRAFT_TAB.get();
    }

    static {
        CLIENT.translation("emilink.configuration.general").push("general");
        DEBUG_MODE = CLIENT
                .comment("Enable debug logging and debug chat messages")
                .translation("emilink.config.general.debugMode")
                .define("debugMode", false);
        CLIENT.pop();

        CLIENT.translation("emilink.configuration.emi_ui").push("emi_ui");

        ENABLE_MOB_SEPARATOR = CLIENT
                .comment("Move mob-related EMI index entries to a separated section. " +
                         "Disable by default because it can be expensive in very large modpacks.")
                .translation("emilink.config.emi_ui.enableMobSeparator")
                .define("enableMobSeparator", false);

        CLIENT.translation("emilink.configuration.emi_ui.recipe").push("recipe");

        ENABLE_WRAP_BOOK = CLIENT
                .comment("Enable wrap processing pattern output as written book (WB mode)")
                .translation("emilink.config.emi_ui.enableWrapBook")
                .define("enableWrapBook", true);

        WB_FILL_INPUT_GRID = CLIENT
                .comment("Only used when wrap book is enabled. Also place the original output " +
                         "item into an empty input slot.")
                .translation("emilink.config.emi_ui.wbFillInputGrid")
                .define("wbFillInputGrid", false);

        ENABLE_DRAG_FILL = CLIENT
                .comment("Drag an EMI stack onto a text field to fill in its name, " +
                         "Alt + drag to fill in its mod id.")
                .translation("emilink.config.emi_ui.enableDragFill")
                .define("enableDragFill", true);

        BOOKMARK_PRIORITY = CLIENT
                .comment("When encoding processing patterns via EMI recipe transfer, " +
                         "prioritize items from the EMI favorites bar over " +
                         "network-inventory-selected items")
                .translation("emilink.config.emi_ui.bookmarkPriority")
                .define("bookmarkPriority", true);

        CLIENT.pop();

        CLIENT.translation("emilink.configuration.emi_ui.search").push("search");

        SEARCH_HISTORY_POSITION = CLIENT
                .comment("EMI search history overlay position. " +
                         "OFF disables it; AUTO keeps the adaptive default; " +
                         "ABOVE, LEFT, and RIGHT force a side of the search box.")
                .translation("emilink.config.emi_ui.searchHistoryPosition")
                .defineEnum("searchHistoryPosition", SearchHistoryPosition.AUTO);

        TAKE_OVER_EMI_SEARCH = CLIENT
                .comment("Run EMI's item search from EmiLink instead of letting EMI's own search " +
                         "worker run. This was a workaround for stale results leaving empty slots " +
                         "in the EMI sidebar. Disabled by default, because taking over cancels " +
                         "EMI's search method and thereby silences every other EMI search addon " +
                         "(query syntax such as #tag / @mod, creative tab sync) and makes their " +
                         "searches return nothing.")
                .translation("emilink.config.emi_ui.takeOverEmiSearch")
                .define("takeOverEmiSearch", false);

        ENABLE_COPY_HOVERED_STACK_ID = CLIENT
                .comment("Copy the hovered EMI stack id to clipboard with Ctrl+C. " +
                         "Disabled by default to avoid conflicting with normal text copy.")
                .translation("emilink.config.emi_ui.enableCopyHoveredStackId")
                .define("enableCopyHoveredStackId", false);

        CLIENT.pop();

        CLIENT.translation("emilink.configuration.emi_ui.favorites").push("favorites");

        FAVORITE_PAGE_COUNT = CLIENT
                .comment("Minimum number of EMI favorites pages kept available")
                .translation("emilink.config.emi_ui.favoritePageCount")
                .defineInRange("favoritePageCount", 5, 1, 50);

        ENABLE_FAVORITE_DRAG_SELECT = CLIENT
                .comment("Drag-select over EMI sidebars while holding the modifier key to batch-favorite stacks and protect them from being unfavorited by the A key")
                .translation("emilink.config.emi_ui.enableFavoriteDragSelect")
                .define("enableFavoriteDragSelect", true);

        FAVORITE_DRAG_SELECT_MODIFIER = CLIENT
                .comment("Only used when favorite drag-select is enabled. Modifier + left-drag " +
                         "toggles favorite protection: unprotected selections are protected, " +
                         "protected selections are unprotected.")
                .translation("emilink.config.emi_ui.favoriteDragSelectModifier")
                .define("favoriteDragSelectModifier", "ALT");

        FAVORITE_PROTECTION_BORDER_COLOR = CLIENT
                .comment("RGB hex color for protected favorite borders, for example FFD700 or #FFD700")
                .translation("emilink.config.emi_ui.favoriteProtectionBorderColor")
                .define("favoriteProtectionBorderColor", "FFD700");

        FAVORITE_PROTECTION_BORDER_OPACITY = CLIENT
                .comment("Opacity of protected favorite borders, from 0 to 255")
                .translation("emilink.config.emi_ui.favoriteProtectionBorderOpacity")
                .defineInRange("favoriteProtectionBorderOpacity", 230, 0, 255);

        FAVORITE_PROTECTION_BACKGROUND_COLOR = CLIENT
                .comment("RGB hex color for the active favorite drag-selection background, for example FFD700 or #FFD700")
                .translation("emilink.config.emi_ui.favoriteProtectionBackgroundColor")
                .define("favoriteProtectionBackgroundColor", "FFD700");

        FAVORITE_PROTECTION_BACKGROUND_OPACITY = CLIENT
                .comment("Opacity of the active favorite drag-selection background, from 0 to 255")
                .translation("emilink.config.emi_ui.favoriteProtectionBackgroundOpacity")
                .defineInRange("favoriteProtectionBackgroundOpacity", 36, 0, 255);

        ENABLE_BATCH_MARK = CLIENT
                .comment("Enable modifier + right-drag batch marking of EMI sidebar stacks. " +
                         "Dropping one marked stack onto a filter slot fills the remaining marks " +
                         "into the following slots, in the order they were selected.")
                .translation("emilink.config.emi_ui.enableBatchMark")
                .define("enableBatchMark", true);

        ENABLE_BATCH_MARK_FILL = CLIENT
                .comment("Only used when batch marking is enabled. After dropping one marked " +
                         "stack onto a filter slot, fill the remaining marks into the following " +
                         "slots. Disable to only mark the dropped slot.")
                .translation("emilink.config.emi_ui.enableBatchMarkFill")
                .define("enableBatchMarkFill", true);

        BATCH_MARK_BORDER_COLOR = CLIENT
                .comment("RGB hex color for batch-marked outlines, for example 00C8FF or #00C8FF")
                .translation("emilink.config.emi_ui.batchMarkBorderColor")
                .define("batchMarkBorderColor", "00C8FF");

        BATCH_MARK_BORDER_OPACITY = CLIENT
                .comment("Opacity of batch-marked outlines, from 0 to 255")
                .translation("emilink.config.emi_ui.batchMarkBorderOpacity")
                .defineInRange("batchMarkBorderOpacity", 230, 0, 255);

        BATCH_MARK_BACKGROUND_COLOR = CLIENT
                .comment("RGB hex color for the batch-mark background, for example 00C8FF or #00C8FF")
                .translation("emilink.config.emi_ui.batchMarkBackgroundColor")
                .define("batchMarkBackgroundColor", "00C8FF");

        BATCH_MARK_BACKGROUND_OPACITY = CLIENT
                .comment("Opacity of the batch-mark background, from 0 to 255")
                .translation("emilink.config.emi_ui.batchMarkBackgroundOpacity")
                .defineInRange("batchMarkBackgroundOpacity", 36, 0, 255);

        SHARED_EDGE_SPLIT = CLIENT
                .comment("Draw a border shared by favorite protection and batch marking half in " +
                         "each colour so both outlines connect. Disable to draw shared borders in " +
                         "the batch mark colour only.")
                .translation("emilink.config.emi_ui.sharedEdgeSplit")
                .define("sharedEdgeSplit", true);

        CLIENT.pop();
        CLIENT.pop();

        CLIENT.translation("emilink.configuration.ae_network").push("ae_network");

        ENABLE_AE_NETWORK_LOOKUP = CLIENT
                .comment("Master switch for reading AE network contents: stored amounts, " +
                         "craftability, badges, tooltips, quick craft and the EMI craftables view. " +
                         "When disabled no ME queries are sent at all.")
                .translation("emilink.config.ae_network.enableAeNetworkLookup")
                .define("enableAeNetworkLookup", true);

        EXPOSE_NETWORK_INVENTORY_TO_EMI = CLIENT
                .comment("Force AE2 to expose the whole ME network inventory to EMI, so EMI's " +
                         "craftables sidebar, missing material hints and recipe availability " +
                         "include network contents. Disabled by default, which respects AE2's " +
                         "own provideNetworkInventoryToEmi option. On a large network enabling " +
                         "this makes EMI rebuild its craftable list for every stored stack, " +
                         "which can make terminals stutter.")
                .translation("emilink.config.ae_network.exposeNetworkInventoryToEmi")
                .define("exposeNetworkInventoryToEmi", false);

        CLIENT.translation("emilink.configuration.ae_network.cache").push("cache");

        CACHE_TTL_MS = CLIENT
                .comment("AE network cache TTL in milliseconds (100-60000)")
                .translation("emilink.config.cache.cacheTTLMs")
                .defineInRange("cacheTTLMs", 5_000L, 100L, 60_000L);

        NEGATIVE_CACHE_TTL_MS = CLIENT
                .comment("Negative cache (item not found) TTL in milliseconds (100-120000)")
                .translation("emilink.config.cache.negativeCacheTTLMs")
                .defineInRange("negativeCacheTTLMs", 5_000L, 100L, 120_000L);

        BATCH_FLUSH_MS = CLIENT
                .comment("Batch query flush interval in milliseconds (100-10000). " +
                         "How often pending AE queries are batched and sent to the server.")
                .translation("emilink.config.cache.batchFlushMs")
                .defineInRange("batchFlushMs", 5_000L, 100L, 10_000L);

        CLIENT.pop();

        CLIENT.translation("emilink.configuration.ae_network.badges").push("badges");

        ENABLE_NETWORK_BADGES = CLIENT
                .comment("Show AE network overlays on EMI item icons: bottom stock count and top-right craftable cross")
                .translation("emilink.config.ae_network.enableNetworkBadges")
                .define("enableNetworkBadges", false);

        ENABLE_CRAFTABLE_NETWORK_BADGES = CLIENT
                .comment("Only used when network badges are enabled. Also query AE craftability " +
                         "for the top-right cross. Disable this on large AE networks to show only " +
                         "stock counts.")
                .translation("emilink.config.ae_network.enableCraftableNetworkBadges")
                .define("enableCraftableNetworkBadges", false);

        INITIAL_BADGE_SCAN_LIMIT = CLIENT
                .comment("Only used when network badges are enabled. Maximum EMI sidebar items " +
                         "queried immediately when an AE terminal opens. Set to 0 to disable the " +
                         "initial scan.")
                .translation("emilink.config.ae_network.initialBadgeScanLimit")
                .defineInRange("initialBadgeScanLimit", 24, 0, 512);

        CLIENT.pop();

        CLIENT.translation("emilink.configuration.ae_network.tooltip").push("tooltip");

        ENABLE_NETWORK_TOOLTIPS = CLIENT
                .comment("Show stored amount and craftability of AE keys in EMI tooltips, " +
                         "including fluids, chemicals and other non-item keys")
                .translation("emilink.config.ae_network.enableNetworkTooltips")
                .define("enableNetworkTooltips", true);

        CLIENT.pop();

        CLIENT.translation("emilink.configuration.ae_network.interaction").push("interaction");

        EXTRACT_MODIFIER = CLIENT
                .comment("Modifier for Click-to-extract from AE/BD network. Any key or mouse button; OFF disables.")
                .translation("emilink.config.ae_network.extractModifier")
                .define("extractModifier", "SHIFT");

        ENABLE_AE_DEPOSIT = CLIENT
                .comment("Click on the EMI sidebar with a carried item to deposit into AE. " +
                         "Requires a wireless terminal or an open AE2 terminal.")
                .translation("emilink.config.ae_network.enableAeDeposit")
                .define("enableAeDeposit", true);

        DEPOSIT_BATCH_MODIFIER = CLIENT
                .comment("Only used when depositing from the EMI sidebar is enabled. Modifier for " +
                         "depositing all matching items. Any key or mouse button; OFF disables.")
                .translation("emilink.config.ae_network.depositBatchModifier")
                .define("depositBatchModifier", "SHIFT");

        CLIENT.pop();
        CLIENT.pop();

        CLIENT.translation("emilink.configuration.quick_craft").push("quick_craft");

        ENABLE_QUICK_CRAFT_TAB = CLIENT
                .comment("Enable recursive BOM automatic workbench crafting")
                .translation("emilink.config.quick_craft.enableQuickCraft")
                .define("enableQuickCraftTab", true);

        QUICK_CRAFT_BATCHES_PER_TICK = CLIENT
                .comment("Maximum BOM crafting batches processed in one client tick (1-256)")
                .translation("emilink.config.quick_craft.batchesPerTick")
                .defineInRange("batchesPerTick", 64, 1, 256);

        CLIENT.pop();

        CLIENT.translation("emilink.configuration.inventory").push("inventory");

        ENABLE_BULK_TRANSFER = CLIENT
                .comment("Enable Space+Click bulk transfer for regular containers (non-AE, non-BD). " +
                         "Disable if it conflicts with other mods' Space+Click handlers.")
                .translation("emilink.config.inventory.enableBulkTransfer")
                .define("enableBulkTransfer", true);

        ENABLE_DISCARD_MATCHING_KEY = CLIENT
                .comment("Enable Ctrl+Shift+Drop to discard all stacks matching the hovered slot item")
                .translation("emilink.config.inventory.enableDiscardMatchingKey")
                .define("enableDiscardMatchingKey", true);

        CLIENT.pop();

        SERVER.translation("emilink.configuration.general").push("general");

        SERVER_DEBUG_MODE = SERVER
                .comment("Enable EmiLink debug logging on the server side. " +
                         "The client has its own debug switch in emilink-client.toml.")
                .translation("emilink.config.general.serverDebugMode")
                .define("debugMode", false);

        SERVER.pop();
    }

    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();
    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    private static boolean validated = false;
    private static volatile boolean clientLoaded;

    private EmiLinkConfig() {}

    /**
     * Whether the client config has been loaded. Every value read throws before
     * that point, and NeoForge never loads configs at all when another mod fails
     * during construction, so the client-side entry points check this first.
     */
    public static boolean isClientConfigLoaded() {
        return clientLoaded;
    }

    /** Called by NeoForge when one of the config files finishes loading. */
    public static void onLoad(ModConfig.Type type) {
        if (type == ModConfig.Type.CLIENT) {
            clientLoaded = true;
            validated = false;
            validate();
        }
    }

    /** Called by NeoForge when one of the config files is reloaded at runtime. */
    public static void onReload(ModConfig.Type type) {
        if (type == ModConfig.Type.CLIENT) {
            clientLoaded = true;
            validated = false;
            validate();
        }
        ModLogger.debug("Configuration reloaded ({})", type);
    }

    /**
     * Validate the client values on load. If any value is outside the expected
     * range it falls back to the spec default and logs a warning. Called once
     * per client config load.
     */
    public static void validate() {
        if (validated) return;
        validated = true;

        validateLong(CACHE_TTL_MS, "ae_network.cache.cacheTTLMs", 5_000L, 100L, 60_000L);
        validateLong(NEGATIVE_CACHE_TTL_MS, "ae_network.cache.negativeCacheTTLMs", 5_000L, 100L, 120_000L);
        validateLong(BATCH_FLUSH_MS, "ae_network.cache.batchFlushMs", 5_000L, 100L, 10_000L);
        validateInt(FAVORITE_PAGE_COUNT, "emi_ui.favorites.favoritePageCount", 5, 1, 50);
        validateInt(INITIAL_BADGE_SCAN_LIMIT, "ae_network.badges.initialBadgeScanLimit", 24, 0, 512);
        validateInt(QUICK_CRAFT_BATCHES_PER_TICK, "quick_craft.batchesPerTick", 64, 1, 256);
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

    /** Raw modifier string normalized for display / key resolution. */
    public static String getFavoriteDragSelectKeyName() {
        try {
            String v = FAVORITE_DRAG_SELECT_MODIFIER.get();
            if (v == null) return "ALT";
            String t = v.trim();
            return t.isEmpty() ? "ALT" : t;
        } catch (Exception e) {
            return "ALT";
        }
    }

    public static String getExtractModifierKeyName() {
        try {
            String v = EXTRACT_MODIFIER.get();
            if (v == null) return "SHIFT";
            String t = v.trim();
            return t.isEmpty() ? "SHIFT" : t;
        } catch (Exception e) { return "SHIFT"; }
    }

    public static String getDepositBatchModifierKeyName() {
        try {
            String v = DEPOSIT_BATCH_MODIFIER.get();
            if (v == null) return "SHIFT";
            String t = v.trim();
            return t.isEmpty() ? "SHIFT" : t;
        } catch (Exception e) { return "SHIFT"; }
    }

    public static int getFavoriteProtectionBorderArgb() {
        return toArgb(FAVORITE_PROTECTION_BORDER_COLOR.get(),
                FAVORITE_PROTECTION_BORDER_OPACITY.get(), 0xFFD700);
    }

    public static int getFavoriteProtectionBackgroundArgb() {
        return toArgb(FAVORITE_PROTECTION_BACKGROUND_COLOR.get(),
                FAVORITE_PROTECTION_BACKGROUND_OPACITY.get(), 0xFFD700);
    }

    public static int getBatchMarkBorderArgb() {
        return toArgb(BATCH_MARK_BORDER_COLOR.get(),
                BATCH_MARK_BORDER_OPACITY.get(), 0x00C8FF);
    }

    public static int getBatchMarkBackgroundArgb() {
        return toArgb(BATCH_MARK_BACKGROUND_COLOR.get(),
                BATCH_MARK_BACKGROUND_OPACITY.get(), 0x00C8FF);
    }

    private static int toArgb(String value, int opacity, int fallbackRgb) {
        int rgb = fallbackRgb;
        if (value != null) {
            String normalized = value.trim();
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
                normalized = normalized.substring(2);
            }
            if (normalized.length() == 6) {
                try {
                    rgb = Integer.parseInt(normalized, 16) & 0xFFFFFF;
                } catch (NumberFormatException ignored) {
                    // Keep the default color for invalid config input.
                }
            }
        }
        return ((Math.max(0, Math.min(255, opacity)) & 0xFF) << 24) | rgb;
    }
}
