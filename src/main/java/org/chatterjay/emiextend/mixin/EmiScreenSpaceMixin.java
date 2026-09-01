package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.client.AENetworkCache;
import org.chatterjay.emiextend.client.NetworkBadgeRenderState;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into EMI's ScreenSpace (sidebar item grid) to draw AE network overlays.
 * <p>
 * Bottom: the AE network item count, left-aligned at the bottom of the 16x16 icon.
 * Top-right: AE-style craftable plus sign when the item has a pattern.
 * <ul>
 *   <li>craftable && count == 0  -> plus on top-right</li>
 *   <li>craftable && count > 0   -> bottom abbreviated count (k/m/g/t), plus on top-right</li>
 *   <li>!craftable && count > 0  -> bottom abbreviated count only</li>
 *   <li>otherwise                -> no overlay</li>
 * </ul>
 */
@Mixin(value = ScreenSpace.class, remap = false)
public abstract class EmiScreenSpaceMixin {

    @Shadow
    private int th;

    @Shadow
    private int[] widths;

    @Shadow
    public abstract List<? extends EmiIngredient> getStacks();

    @Shadow
    public abstract int getX(int col, int row);

    @Shadow
    public abstract int getY(int col, int row);

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void emilink$beginSidebarRender(EmiDrawContext context, int mouseX, int mouseY,
                                             float delta, int scrollOffset, CallbackInfo ci) {
        NetworkBadgeRenderState.beginSidebarRender();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void emilink$endSidebarRender(EmiDrawContext context, int mouseX, int mouseY,
                                           float delta, int scrollOffset, CallbackInfo ci) {
        NetworkBadgeRenderState.endSidebarRender();
    }


    /**
     * Head injection: on terminal open, collect all visible uncached items into the pending
     * batch and flush immediately, so badges show without requiring manual hovering.
     */
    @Inject(method = "render",
            at = @At("HEAD"),
            remap = false)
    private void emilink$initialScan(EmiDrawContext context, int mouseX, int mouseY,
                                     float delta, int scrollOffset, CallbackInfo ci) {
        if (!AE2Proxy.isLoaded() || !EmiLinkConfig.ENABLE_NETWORK_BADGES.get()) return;
        if (!AENetworkCache.hasAEAccess()) return;
        AENetworkCache.requestInitialScanIfNeeded();
        var stacks = getStacks();
        // EMI can render the sidebar before its index has any cells. Keep the
        // one-shot flag pending so the next populated render performs the scan.
        if (stacks.isEmpty() || th <= 0) return;
        if (!AENetworkCache.consumeInitialScanFlag()) return;

        int scanLimit = EmiLinkConfig.INITIAL_BADGE_SCAN_LIMIT.get();
        if (scanLimit <= 0) return;

        int index = scrollOffset;

        int submitted = 0;
        for (int row = 0; row < th; row++) {
            int w = widths[row];
            for (int col = 0; col < w; col++) {
                if (index >= stacks.size()) {
                    if (submitted > 0) {
                        AENetworkCache.flushBatchNow();
                        ModLogger.debug("Initial scan: submitted {} uncached items", submitted);
                    }
                    return;
                }
                EmiIngredient ingredient = stacks.get(index);
                index++;
                ItemStack itemStack = resolveItemStack(ingredient);
                if (itemStack == null) continue;
                if (!AENetworkCache.getCachedResult(itemStack).found()) {
                    AENetworkCache.submitForBadge(itemStack);
                    submitted++;
                    if (submitted >= scanLimit) {
                        AENetworkCache.flushBatchNow();
                        ModLogger.debug("Initial scan: submitted {} uncached items (limited)", submitted);
                        return;
                    }
                }
            }
        }

        if (submitted > 0) {
            AENetworkCache.flushBatchNow();
            ModLogger.debug("Initial scan: submitted {} uncached items", submitted);
        }
    }

    @Inject(method = "render",
            at = @At("RETURN"),
            remap = false)
    private void emilink$renderBadgeOverlay(EmiDrawContext context, int mouseX, int mouseY,
                                            float delta, int scrollOffset, CallbackInfo ci) {
        if (!AE2Proxy.isLoaded() || !EmiLinkConfig.ENABLE_NETWORK_BADGES.get()) return;
        if (!AENetworkCache.hasAEAccess()) return;

        var stacks = getStacks();
        int index = scrollOffset;

        int itemsRendered = 0;
        int cacheHits = 0;
        int craftableItems = 0;

        // ScreenSpace has already drawn its item batch at this point. Flush the
        // vanilla GUI buffer before drawing text so the overlay keeps a stable
        // order relative to EMI's batched item vertices.
        context.raw().flush();
        context.disableDepthTest();
        context.enableBlend();

        // Use the same small-font approach as AE2's slot amount renderer. The
        // GUI coordinates remain in the normal 18px slot space; only the font
        // is scaled, which keeps the overlay aligned at every GUI scale.
        context.push();
        context.matrices().translate(0, 0, 200);
        context.matrices().scale(0.5f, 0.5f, 1f);
        var font = Minecraft.getInstance().font;
        for (int row = 0; row < th; row++) {
            int w = widths[row];
            for (int col = 0; col < w; col++) {
                if (index >= stacks.size()) break;

                int x = getX(col, row) + 1;
                int y = getY(col, row) + 1;
                EmiIngredient ingredient = stacks.get(index++);
                itemsRendered++;

                ItemStack itemStack = resolveItemStack(ingredient);
                if (itemStack == null) continue;

                var result = AENetworkCache.getCachedResult(itemStack);
                if (result.found() && result.count() > 0) {
                    cacheHits++;
                    context.raw().drawString(font, formatShortCount(result.count()),
                            (x + 1) * 2,
                            (y + 11) * 2, 0xFFFFFFFF, true);
                }
                if (result.craftable()) {
                    drawTopRightPlus(context, x, y, hasRecipeFavoriteMarker(ingredient));
                }
                if (result.craftable()) craftableItems++;
            }
        }
        context.pop();
        context.raw().flush();
        context.resetColor();
        context.enableDepthTest();
        context.disableBlend();
        if (badgeLogRateLimit()) {
            ModLogger.debug("Network overlay: evaluated={} cached={} craftable={}",
                    itemsRendered, cacheHits, craftableItems);
        }
    }

    /** Rate limiter for per-frame debug logs — at most once per 2 seconds. */
    private static long lastBadgeLogTime = 0;

    private static boolean badgeLogRateLimit() {
        long now = System.currentTimeMillis();
        if (now - lastBadgeLogTime >= 2000) {
            lastBadgeLogTime = now;
            return true;
        }
        return false;
    }

    private static String formatShortCount(long count) {
        if (count < 1_000) return Long.toString(count);
        if (count < 1_000_000) return formatCompact(count, 1_000L, "k");
        if (count < 1_000_000_000) return formatCompact(count, 1_000_000L, "m");
        if (count < 1_000_000_000_000L) return formatCompact(count, 1_000_000_000L, "g");
        return formatCompact(count, 1_000_000_000_000L, "t");
    }

    private static String formatCompact(long count, long unit, String suffix) {
        long whole = count / unit;
        long tenths = (count % unit) * 10 / unit;
        // Only show one decimal when the whole part is single-digit, to keep width <= 4 chars at 0.5 scale.
        if (whole < 10 && tenths != 0) {
            return whole + "." + tenths + suffix;
        }
        return whole + suffix;
    }

    /**
     * Top-right craftable marker: an AE-style white plus at the top-right corner
     * of the 16x16 icon.
     */
    private static void drawTopRightPlus(EmiDrawContext context, int x, int y,
                                         boolean hasRecipeFavoriteMarker) {
        int scale = 2;
        int ox = (x + 12) * scale;
        // EMI reserves the first 4 pixels in this corner for the recipe-favorite
        // marker. Stack our 3x3 pattern marker directly below it when present.
        int markerY = y + (hasRecipeFavoriteMarker ? 5 : 1);
        int oy = markerY * scale;
        int white = 0xFFFFFFFF;
        // A compact 3x3 white plus. Do not draw a full backing square: its
        // uncovered corner remains as a black block over some item textures.
        context.fill(ox, oy + 2, 6, 2, white);
        context.fill(ox + 2, oy, 2, 6, white);
    }

    private static boolean hasRecipeFavoriteMarker(EmiIngredient ingredient) {
        return ingredient instanceof EmiFavorite favorite
                && favorite.getRecipe() != null
                && !(favorite instanceof EmiFavorite.Craftable);
    }

    /**
     * Extract the first real ItemStack from an EmiIngredient without allocating a stream.
     * Most sidebar items are plain EmiStack instances, checked first.
     */
    private static ItemStack resolveItemStack(EmiIngredient ingredient) {
        if (ingredient instanceof EmiStack es) {
            ItemStack stack = es.getItemStack();
            if (!stack.isEmpty()) return stack;
        }
        for (var es : ingredient.getEmiStacks()) {
            ItemStack stack = es.getItemStack();
            if (!stack.isEmpty()) return stack;
        }
        return null;
    }
}
