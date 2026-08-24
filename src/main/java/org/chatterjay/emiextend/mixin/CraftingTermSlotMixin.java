package org.chatterjay.emiextend.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.storage.MEStorage;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import appeng.util.Platform;
import appeng.util.inv.PlayerInternalInventory;
import org.chatterjay.emiextend.util.EmiCraftHelper;

@Mixin(CraftingTermSlot.class)
public class CraftingTermSlotMixin {

    @Shadow(remap = false)
    private MEStorage storage;

    @Inject(method = "doClick", at = @At("HEAD"), remap = false, cancellable = true)
    private void emilink$handleSingleCraft(InventoryAction action, Player player, CallbackInfo ci) {
        if (!EmiCraftHelper.checkSingleCraft()) return;
        boolean allowNetworkOverflow = EmiCraftHelper.checkSingleCraftToNetwork();
        EmiCraftHelper.clear();

        var all = storage.getAvailableStacks();
        ItemStack result = ((CraftingTermSlotInvoker) this).emilink$invokeCraftItem(player, storage, all);

        if (!result.isEmpty()) {
            int outputCount = result.getCount();
            String outputName = result.getHoverName().getString();
            // Count a real craft even when every destination is full and the
            // remainder has to be dropped. Storage failure must not stop the
            // BOM state machine or make the consumed ingredients disappear.
            EmiCraftHelper.markSingleCraftProduced();
            ItemStack extra = new PlayerInternalInventory(player.getInventory()).addItems(result);
            int inventoryInserted = outputCount - extra.getCount();
            int networkInserted = 0;

            // BOM outputs prefer the player's inventory. Only the overflow is
            // offered to AE, so a full AE network is not a reason to block
            // crafting while the inventory still has room.
            if (allowNetworkOverflow && !extra.isEmpty()) {
                long inserted = storage.insert(
                        AEItemKey.of(extra),
                        extra.getCount(),
                        Actionable.MODULATE,
                        appeng.api.networking.security.IActionSource.ofPlayer(player));
                int insertedCount = (int) Math.max(0,
                        Math.min((long) extra.getCount(), Math.min(Integer.MAX_VALUE, inserted)));
                extra.shrink(insertedCount);
                networkInserted = insertedCount;
                org.chatterjay.emiextend.util.ModLogger.debug(
                        "AE_EMI_CTRL_CRAFT overflow item={} requested={} inserted={} remaining={} player={}",
                        outputName, outputCount, insertedCount,
                        extra.getCount(), player.getGameProfile().getName());
            }
            int beforeCarried = extra.getCount();
            extra = emilink$addToCarried(player, extra);
            int cursorInserted = beforeCarried - extra.getCount();
            int dropped = extra.getCount();
            EmiCraftHelper.markSingleCraftDelivered(outputCount - dropped);
            org.chatterjay.emiextend.util.ModLogger.debug(
                    "AE_EMI_CTRL_CRAFT output-route item={} produced={} inventory={} network={} cursor={} dropped={} player={}",
                    outputName, outputCount, inventoryInserted,
                    networkInserted, cursorInserted, dropped, player.getGameProfile().getName());
            if (!extra.isEmpty()) {
                org.chatterjay.emiextend.util.ModLogger.debug(
                        "AE_EMI_CTRL_CRAFT dropping undelivered output item={} count={} player={}",
                        outputName, extra.getCount(), player.getGameProfile().getName());
                Platform.spawnDrops(player.level(), player.blockPosition(), java.util.List.of(extra));
            }
            player.containerMenu.broadcastChanges();
        }

        ci.cancel();
    }

    /**
     * Keep a real crafted result on the menu cursor when the player inventory
     * cannot accept it. This is used as temporary BOM storage; it never
     * creates an item because the stack came directly from craftItem().
     */
    private static ItemStack emilink$addToCarried(Player player, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(stack.copy());
            return ItemStack.EMPTY;
        }
        if (!ItemStack.isSameItemSameComponents(carried, stack)) {
            return stack;
        }

        int room = Math.max(0, carried.getMaxStackSize() - carried.getCount());
        int moved = Math.min(room, stack.getCount());
        if (moved > 0) {
            carried.grow(moved);
            stack.shrink(moved);
        }
        return stack;
    }
}
