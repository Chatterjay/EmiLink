package org.chatterjay.emiextend.mixin;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.helpers.ICraftingGridMenu;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.me.crafting.CraftConfirmMenu;
import net.minecraft.server.level.ServerPlayer;
import org.chatterjay.emiextend.util.AeAutocraftAmountOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuAmountMixin {
    @Redirect(
            method = "openWithCraftingList",
            at = @At(value = "INVOKE",
                    target = "Lappeng/menu/me/crafting/CraftConfirmMenu;planJob(Lappeng/api/stacks/AEKey;ILappeng/api/networking/crafting/CalculationStrategy;)Z"),
            remap = false,
            require = 0
    )
    private static boolean emilink$overrideCraftMissingAmount(CraftConfirmMenu menu,
                                                              AEKey what,
                                                              int amount,
                                                              CalculationStrategy strategy,
                                                              IActionHost terminal,
                                                              ServerPlayer player,
                                                              MenuHostLocator locator,
                                                              List<ICraftingGridMenu.AutoCraftEntry> stacksToCraft) {
        return menu.planJob(what, AeAutocraftAmountOverride.consume(player, amount), strategy);
    }
}
