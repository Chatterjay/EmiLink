package org.chatterjay.emilink.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.slot.CraftingTermSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CraftingTermSlot.class)
public interface CraftingTermSlotInvoker {

    @Invoker(value = "craftItem", remap = false)
    ItemStack emilink$invokeCraftItem(Player player, MEStorage storage, KeyCounter keyCounter);
}
