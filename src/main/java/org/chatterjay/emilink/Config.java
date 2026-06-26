package org.chatterjay.emilink;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Emilink.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
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

    // ---- Bookmark Priority ----
    public static final ForgeConfigSpec.BooleanValue BOOKMARK_PRIORITY;

    // ---- Features ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_WRAP_BOOK;
    public static final ForgeConfigSpec.BooleanValue WB_FILL_INPUT_GRID;
    public static final ForgeConfigSpec.BooleanValue ENABLE_NETWORK_BADGES;
    public static final ForgeConfigSpec.IntValue NETWORK_BADGE_STYLE;
    public static final ForgeConfigSpec.ConfigValue<String> EXTRACT_MODIFIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BULK_TRANSFER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AE_DEPOSIT;
    public static final ForgeConfigSpec.ConfigValue<String> DEPOSIT_BATCH_MODIFIER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DRAG_FILL;

    // ---- Network ----
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_PACKET_LIMIT;

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
                .comment("Batch query flush interval in milliseconds (200-10000)")
                .defineInRange("batchFlushMs", 5_000L, 200L, 10_000L);

        BUILDER.pop();
        BUILDER.push("bookmark_priority");

        BOOKMARK_PRIORITY = BUILDER
                .comment("When encoding processing patterns via EMI recipe transfer, " +
                         "prioritize items from the EMI favorites bar")
                .define("bookmarkPriority", true);

        BUILDER.pop();
        BUILDER.push("features");

        ENABLE_WRAP_BOOK = BUILDER
                .comment("Enable wrap processing pattern output as written book (WB mode)")
                .define("enableWrapBook", true);

        WB_FILL_INPUT_GRID = BUILDER
                .comment("When wrap book is enabled, also place the original output item into an empty input slot")
                .define("wbFillInputGrid", false);

        ENABLE_NETWORK_BADGES = BUILDER
                .comment("Show AE network status corner badges on EMI item icons")
                .define("enableNetworkBadges", false);

        NETWORK_BADGE_STYLE = BUILDER
                .comment("Badge rendering style: 1 = filled square, 2 = hollow border")
                .defineInRange("networkBadgeStyle", 1, 1, 2);

        EXTRACT_MODIFIER = BUILDER
                .comment("Modifier for Click-to-extract. SHIFT, CONTROL, ALT, or OFF")
                .define("extractModifier", "SHIFT");

        ENABLE_BULK_TRANSFER = BUILDER
                .comment("Enable Space+Click bulk transfer for regular containers")
                .define("enableBulkTransfer", true);

        ENABLE_AE_DEPOSIT = BUILDER
                .comment("Click EMI sidebar with carried item to deposit into AE")
                .define("enableAeDeposit", true);

        DEPOSIT_BATCH_MODIFIER = BUILDER
                .comment("Modifier for batch deposit (SHIFT, CONTROL, ALT, or OFF)")
                .define("depositBatchModifier", "SHIFT");

        ENABLE_DRAG_FILL = BUILDER
                .comment("Drag an EMI item onto a text field to fill it with the item name")
                .define("enableDragFill", true);

        BUILDER.pop();
        BUILDER.push("network");

        ENABLE_DEBUG_PACKET_LIMIT = BUILDER
                .comment("Limit debug-related packets to 1 per tick")
                .define("enableDebugPacketLimit", true);

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean validated = false;

    private Config() {}

    public static void validate() {
        if (validated) return;
        validated = true;
        validateLong(CACHE_TTL_MS, "cache.cacheTTLMs", 5_000L, 100L, 60_000L);
        validateLong(NEGATIVE_CACHE_TTL_MS, "cache.negativeCacheTTLMs", 10_000L, 100L, 120_000L);
        validateLong(DEBOUNCE_MS, "cache.debounceMs", 250L, 50L, 5_000L);
    }

    public static ExtractTrigger getExtractModifier() {
        try {
            return ExtractTrigger.valueOf(EXTRACT_MODIFIER.get().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return ExtractTrigger.SHIFT;
        }
    }

    public static ExtractTrigger getDepositBatchModifier() {
        try {
            return ExtractTrigger.valueOf(DEPOSIT_BATCH_MODIFIER.get().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return ExtractTrigger.SHIFT;
        }
    }

    private static void validateLong(ForgeConfigSpec.LongValue value, String path, long fallback, long min, long max) {
        long v = value.get();
        if (v < min || v > max) {
            value.set(fallback);
        }
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        validated = false;
        validate();
    }
}
