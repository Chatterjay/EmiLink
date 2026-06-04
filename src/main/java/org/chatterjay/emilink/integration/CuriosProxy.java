package org.chatterjay.emilink.integration;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

public class CuriosProxy {
    private static Boolean loaded;

    private static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("curios");
            ModLogger.info("CuriosProxy: isLoaded={}", loaded);
        }
        return loaded;
    }

    public static boolean hasWirelessTerminal(Player player, Class<?> terminalItemClass) {
        if (!isLoaded()) {
            ModLogger.info("CuriosProxy: Curios not loaded, skipping");
            return false;
        }
        try {
            var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Object lazyOpt = getCuriosInventory.invoke(null, player);

            // Curios 5.14.1 returns LazyOptional<ICuriosItemHandler>, not Optional
            var lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
            var isPresent = (boolean) lazyOptClass.getMethod("isPresent").invoke(lazyOpt);
            if (!isPresent) {
                ModLogger.info("CuriosProxy: getCuriosInventory LazyOptional not present");
                return false;
            }
            var resolveMethod = lazyOptClass.getMethod("resolve");
            Optional<?> opt = (Optional<?>) resolveMethod.invoke(lazyOpt);
            if (opt.isEmpty()) {
                ModLogger.info("CuriosProxy: getCuriosInventory resolved empty");
                return false;
            }

            Object handler = opt.get();
            Method isEquipped = handler.getClass().getMethod("isEquipped", Predicate.class);
            Predicate<ItemStack> predicate = s -> {
                boolean match = !s.isEmpty() && terminalItemClass.isInstance(s.getItem());
                if (match) ModLogger.info("CuriosProxy: found matching item {}", s.getHoverName().getString());
                return match;
            };
            boolean result = (boolean) isEquipped.invoke(handler, predicate);
            return result;
        } catch (Exception e) {
            ModLogger.info("CuriosProxy: exception: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }
}
