package org.chatterjay.emiextend.client;

import dev.emi.emi.api.render.EmiTooltipComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AE network lines for EMI tooltips.
 * <p>
 * Restores the stored-amount and craftable hints EmiLink showed before the
 * overlay rewrite, and covers every AE key rather than items only: fluids,
 * chemicals and any other key type registered through AE2's EMI converters are
 * resolved by {@link AENetworkKeyHelper} and formatted in the unit a player
 * expects (mB for fluids becomes buckets).
 */
public final class NetworkTooltip {

    private NetworkTooltip() {}

    /**
     * Append the network lines when the active source knows this key. The
     * original list is returned untouched when there is nothing to add, so the
     * render path allocates nothing in the common case.
     */
    public static List<ClientTooltipComponent> append(AENetworkKeyHelper.NetworkKey key,
                                                     List<ClientTooltipComponent> tooltip) {
        if (key == null || tooltip == null || tooltip.isEmpty()) return tooltip;
        if (!NetworkStockSource.tooltipsEnabled()) return tooltip;

        if (NetworkStockSource.isEmiLinkSource()) {
            // Hovering is the earliest signal that a key is worth knowing, and
            // the batch interval throttles how often it actually reaches the
            // server. Fresh cache entries are skipped inside the flush.
            AENetworkCache.submitTooltipQuery(key.stack());
        }

        var entry = NetworkStockSource.lookup(key);
        if (!entry.found() || (entry.count() <= 0L && !entry.craftable())) return tooltip;

        List<ClientTooltipComponent> result = new ArrayList<>(tooltip.size() + 2);
        result.addAll(tooltip);
        if (entry.count() > 0L) {
            result.add(EmiTooltipComponents.of(
                    Component.translatable("ae_tooltip.count", describeAmount(key, entry.count()))
                            .withStyle(ChatFormatting.GRAY)));
        }
        if (entry.craftable()) {
            result.add(EmiTooltipComponents.of(
                    Component.translatable("ae_tooltip.craftable")
                            .withStyle(ChatFormatting.GREEN)));
        }
        return result;
    }

    /**
     * Amount text in AE units. Keys with a larger base unit than one (fluids,
     * chemicals) are shown in that unit with its symbol, so 1500 mB reads as
     * 1.5 kB instead of 1500.
     */
    private static String describeAmount(AENetworkKeyHelper.NetworkKey key, long rawCount) {
        long perUnit = key.amountPerUnit();
        if (perUnit <= 1L) {
            return formatNetworkAmount(rawCount);
        }
        long whole = rawCount / perUnit;
        long tenths = (rawCount % perUnit) * 10 / perUnit;
        String text;
        if (whole <= 0L && tenths <= 0L) {
            text = "<1";
        } else if (whole < 10L && tenths > 0L) {
            text = whole + "." + tenths;
        } else {
            text = formatNetworkAmount(whole);
        }
        String symbol = key.unitSymbol();
        return symbol == null || symbol.isBlank() ? text : text + " " + symbol;
    }

    /** Compact AE-style amount, using AE2's own converter when it is present. */
    public static String formatNetworkAmount(long count) {
        if (count <= 0L) {
            return "0";
        }
        try {
            Class<?> converter = Class.forName("appeng.util.ReadableNumberConverter");
            return (String) converter.getMethod("format", long.class, int.class).invoke(null, count, 4);
        } catch (Throwable ignored) {
            return fallbackFormatNetworkAmount(count);
        }
    }

    private static String fallbackFormatNetworkAmount(long count) {
        if (count < 1_000L) {
            return Long.toString(count);
        }
        if (count < 1_000_000L) {
            return compactAmount(count, 1_000L, "K");
        }
        if (count < 1_000_000_000L) {
            return compactAmount(count, 1_000_000L, "M");
        }
        return compactAmount(count, 1_000_000_000L, "G");
    }

    private static String compactAmount(long count, long unit, String suffix) {
        long whole = count / unit;
        long tenths = (count % unit) * 10 / unit;
        if (whole >= 10 || tenths == 0) {
            return whole + suffix;
        }
        return whole + "." + tenths + suffix;
    }
}
