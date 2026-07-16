package org.chatterjay.emiextend.integration;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

public class CuriosProxy {
    private static Boolean loaded;

    private static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("curios");
        }
        return loaded;
    }

    public static boolean hasWirelessTerminal(Player player, Class<?> terminalItemClass) {
        if (!isLoaded()) return false;
        try {
            var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Optional<?> opt = (Optional<?>) getCuriosInventory.invoke(null, player);
            if (opt.isEmpty()) return false;

            Object handler = opt.get();
            Method isEquipped = handler.getClass().getMethod("isEquipped", Predicate.class);
            Predicate<ItemStack> predicate = s -> !s.isEmpty() && terminalItemClass.isInstance(s.getItem());
            return (boolean) isEquipped.invoke(handler, predicate);
        } catch (Exception e) {
            return false;
        }
    }
}
