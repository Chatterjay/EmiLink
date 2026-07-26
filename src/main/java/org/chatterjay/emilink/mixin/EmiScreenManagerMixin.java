package org.chatterjay.emilink.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.client.handler.AENetworkCache;
import org.chatterjay.emilink.client.handler.EmiInteractionHandler;
import org.chatterjay.emilink.client.search.SearchHistoryOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {
    @Shadow
    private static int lastMouseX;
    @Shadow
    private static int lastMouseY;
    @Shadow
    public static EmiIngredient pressedStack;
    @Shadow
    public static EmiIngredient draggedStack;
    @Shadow
    public static dev.emi.emi.screen.widget.EmiSearchWidget search;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private static void emilink$renderSearchHistory(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        SearchHistoryOverlay.render(context.raw(), mouseX, mouseY);
    }

    @Inject(method = "keyPressed", at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (EmiInteractionHandler.onKeyPressed(keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(true);
            return;
        }

        // Drag-fill: when dragging an EMI item, fill text fields with item name
        if (draggedStack != null && !draggedStack.isEmpty()
                && Config.ENABLE_DRAG_FILL.get()) {
            var mc = Minecraft.getInstance();
            if (mc.screen != null) {
                var text = getDragFillText(draggedStack);
                if (text != null) {
                    ItemStack icon = emilink$getDragFillIcon(draggedStack);
                    if (search.isMouseOver(mouseX, mouseY)) {
                        search.setValue(text);
                        search.setCursorPosition(text.length());
                        SearchHistoryOverlay.remember(text, icon);
                        pressedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                        draggedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                        cir.setReturnValue(true);
                        return;
                    }
                    for (var child : mc.screen.children()) {
                        if (child instanceof EditBox eb && eb.isMouseOver((int) mouseX, (int) mouseY)) {
                            eb.setValue(text);
                            eb.setCursorPosition(text.length());
                            SearchHistoryOverlay.remember(text, icon);
                            pressedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                            draggedStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                }
            }
        }

        // If EMI has an active drag operation, skip our handlers and let EMI process it
        // (e.g. creative mode drag-to-delete on sidebar, or recipe fill)
        if (draggedStack != null && !draggedStack.isEmpty()) {
            return;
        }

        if (EmiInteractionHandler.onMouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$clickSearchHistory(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (SearchHistoryOverlay.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$scrollSearchHistory(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (SearchHistoryOverlay.mouseScrolled(mouseX, mouseY, amount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$favoritesBeforeUnderlyingGui(int mouseX, int mouseY, boolean checkSidebar,
                                                             boolean includeBatches,
                                                             CallbackInfoReturnable<EmiStackInteraction> cir) {
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(EmiStackInteraction.EMPTY);
            return;
        }
        var hovered = emilink$getFavoriteSidebarStack(mouseX, mouseY);
        if (hovered != null) {
            cir.setReturnValue(hovered);
        }
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void emilink$onGetHoveredStack(int mouseX, int mouseY, boolean checkSidebar, boolean includeBatches, CallbackInfoReturnable<dev.emi.emi.api.stack.EmiStackInteraction> cir) {
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            cir.setReturnValue(dev.emi.emi.api.stack.EmiStackInteraction.EMPTY);
            return;
        }

        if (cir.getReturnValue() != null && !cir.getReturnValue().isEmpty()) return;

        var screen = Minecraft.getInstance().screen;
        if (screen == null) return;

        try {
            var method = screen.getClass().getMethod("getStackUnderMouse", double.class, double.class);
            Object swb = method.invoke(screen, (double) mouseX, (double) mouseY);
            if (swb == null) return;

            Object genericStack = swb.getClass().getMethod("stack").invoke(swb);
            if (genericStack == null) return;

            Object what = genericStack.getClass().getMethod("what").invoke(genericStack);
            if (what == null) return;

            try {
                Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
                if (aeItemKeyClass.isInstance(what)) {
                    var itemStack = (net.minecraft.world.item.ItemStack) aeItemKeyClass.getMethod("toStack").invoke(what);
                    if (!itemStack.isEmpty()) {
                        cir.setReturnValue(new dev.emi.emi.api.stack.EmiStackInteraction(dev.emi.emi.api.stack.EmiStack.of(itemStack)));
                        return;
                    }
                }
            } catch (Exception ignored) {}

            try {
                var idMethod = what.getClass().getMethod("getId");
                Object id = idMethod.invoke(what);
                if (id instanceof net.minecraft.resources.ResourceLocation rl) {
                    for (var es : dev.emi.emi.api.EmiApi.getIndexStacks()) {
                        if (rl.equals(es.getId())) {
                            cir.setReturnValue(new dev.emi.emi.api.stack.EmiStackInteraction(es));
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    @Redirect(method = "renderCurrentTooltip",
              at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/stack/EmiIngredient;getTooltip()Ljava/util/List;"),
              require = 0)
    private static List<ClientTooltipComponent> emilink$addAeTooltipInfo(EmiIngredient hov) {
        var list = hov.getTooltip();
        if (list == null) return null;
        // Inject AE network count/craftable info into tooltip for hovered items
        var first = hov.getEmiStacks().stream().findFirst().orElse(null);
        if (first != null) {
            var itemStack = first.getItemStack();
            if (!itemStack.isEmpty()) {
                AENetworkCache.addToTooltip(itemStack, list);
            }
        }
        return list;
    }

    @Inject(method = "renderCurrentTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private static void emilink$hideTooltipBehindSearchHistory(EmiDrawContext context, int mouseX, int mouseY, float delta, dev.emi.emi.screen.EmiScreenBase base, CallbackInfo ci) {
        if (SearchHistoryOverlay.isMouseOver(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    private static String getDragFillText(EmiIngredient stack) {
        if (stack == null || stack.isEmpty()) return null;
        var first = stack.getEmiStacks().stream().findFirst().orElse(null);
        if (first == null) return null;
        if (net.minecraft.client.gui.screens.Screen.hasAltDown()) {
            var id = first.getId();
            return id != null ? "@" + id.getNamespace() : null;
        }
        return first.getName().getString();
    }

    private static ItemStack emilink$getDragFillIcon(EmiIngredient stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        return stack.getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(itemStack -> !itemStack.isEmpty())
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }

    private static EmiStackInteraction emilink$getFavoriteSidebarStack(int mouseX, int mouseY) {
        var panel = EmiScreenManager.getHoveredPanel(mouseX, mouseY);
        if (panel == null || panel.getType() != SidebarType.FAVORITES || !panel.isVisible()) return null;
        var space = panel.getHoveredSpace(mouseX, mouseY);
        if (space == null || space.getType() != SidebarType.FAVORITES || space.pageSize <= 0) return null;
        if (!space.contains(mouseX, mouseY) || mouseX < space.tx || mouseY < space.ty) return null;

        int local = space.getRawOffset((mouseX - space.tx) / 18, (mouseY - space.ty) / 18);
        if (local < 0) return null;
        int index = local;
        if (space == panel.space) {
            index += space.pageSize * panel.page;
        }
        var stacks = space.getStacks();
        if (index < 0 || index >= stacks.size()) {
            return EmiStackInteraction.EMPTY;
        }
        EmiIngredient hovered = stacks.get(index);
        if (hovered == null || hovered.isEmpty()) {
            return EmiStackInteraction.EMPTY;
        }
        if (hovered instanceof EmiFavorite favorite) {
            return new EmiScreenManager.SidebarEmiStackInteraction(hovered, space, favorite.getRecipe(), true);
        }
        return new EmiScreenManager.SidebarEmiStackInteraction(hovered, space);
    }
}
