package org.chatterjay.emiextend.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.client.AENetworkCache;
import org.chatterjay.emiextend.config.EmiLinkConfig;
import org.chatterjay.emiextend.util.ModLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into EMI's ScreenSpace (sidebar item grid) to draw a single AE network
 * status badge on item icons — a bottom-right 6x6 square.
 *
 *   green  = in stock
 *   yellow = craftable only
 */
@Mixin(targets = "dev.emi.emi.screen.EmiScreenManager$ScreenSpace")
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

    /**
     * Head injection: on terminal open, collect all visible uncached items into the pending
     * batch and flush immediately, so badges show without requiring manual hovering.
     */
    @Inject(method = "render",
            at = @At("HEAD"),
            remap = false)
    private void emilink$initialScan(EmiDrawContext context, int mouseX, int mouseY,
                                     float delta, int scrollOffset, CallbackInfo ci) {
        if (!EmiLinkConfig.ENABLE_NETWORK_BADGES.get()) return;
        if (!AENetworkCache.hasAEAccess()) return;
        if (!AENetworkCache.consumeInitialScanFlag()) return;

        var stacks = getStacks();
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
                    AENetworkCache.submitForBatch(itemStack);
                    submitted++;
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
        if (!EmiLinkConfig.ENABLE_NETWORK_BADGES.get()) return;
        if (!AENetworkCache.hasAEAccess()) return;
        // Skip entirely when nothing is cached — avoids iterating visible items
        if (!AENetworkCache.hasAnyCached()) return;

        var stacks = getStacks();
        int index = scrollOffset;

        int itemsRendered = 0;
        int cacheHits = 0;
        boolean drawnAny = false;

        for (int row = 0; row < th; row++) {
            int w = widths[row];
            for (int col = 0; col < w; col++) {
                if (index >= stacks.size()) {
                    if (drawnAny) ModLogger.debug("Badge summary: evaluated={} cached={}", itemsRendered, cacheHits);
                    return;
                }

                int x = getX(col, row) + 1;
                int y = getY(col, row) + 1;

                EmiIngredient ingredient = stacks.get(index);
                index++;
                itemsRendered++;

                // Fast path: extract ItemStack without stream allocation
                ItemStack itemStack = resolveItemStack(ingredient);
                if (itemStack == null) continue;

                var result = AENetworkCache.getCachedResult(itemStack);
                if (!result.found()) continue;
                // Skip negative cache (not in stock, not craftable) — show nothing
                if (result.count() <= 0 && !result.craftable()) continue;
                cacheHits++;
                drawnAny = true;

                drawBadge(context, x, y, result.count() > 0);
            }
        }

        if (drawnAny) ModLogger.debug("Badge summary: evaluated={} cached={}", itemsRendered, cacheHits);
    }

    /**
     * Draw the AE network status badge according to the current style config.
     *
     * @param context draw context
     * @param x       top-left X of the item slot (+1 already applied)
     * @param y       top-left Y of the item slot (+1 already applied)
     * @param inStock true if items are available, false if craftable only
     */
    private static void drawBadge(EmiDrawContext context, int x, int y, boolean inStock) {
        int color = inStock ? 0xFF00CC00 : 0xFFFFCC00;
        if (EmiLinkConfig.NETWORK_BADGE_STYLE.get() == 2) {
            // Style 2: top-left 6x6 hollow border
            // 1-pixel border at x+1,y+1, inner 4x4 transparent
            context.fill(x + 1, y + 1, 6, 1, color);     // top
            context.fill(x + 1, y + 1, 1, 6, color);     // left
            context.fill(x + 6, y + 1, 1, 6, color);     // right
            context.fill(x + 1, y + 6, 6, 1, color);     // bottom
        } else {
            // Style 1 (default): bottom-right 6x6 filled square
            context.fill(x + 11, y + 11, 6, 6, color);
        }
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
        // Fallback for composite ingredients (tag, fluid, etc.)
        for (var es : ingredient.getEmiStacks()) {
            ItemStack stack = es.getItemStack();
            if (!stack.isEmpty()) return stack;
        }
        return null;
    }
}
