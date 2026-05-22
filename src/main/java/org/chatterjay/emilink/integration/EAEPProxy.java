package org.chatterjay.emilink.integration;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Constructor;

public class EAEPProxy {
    private static Boolean loaded;

    public static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("extendedae_plus");
        }
        return loaded;
    }

    public static boolean openCraftScreen(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            ModLogger.info("EAEPProxy: openCraftScreen skipped, loaded={} stack={}", isLoaded(), stack);
            return false;
        }
        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
            Object aeKey = ofMethod.invoke(null, stack);
            if (aeKey == null) return false;

            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            Constructor<?> gsCtor = genericStackClass.getDeclaredConstructor(aeKeyClass, long.class);
            Object genericStack = gsCtor.newInstance(aeKey, 1L);

            ModLogger.info("EAEPProxy: openCraftScreen sending packet for {}", stack.getHoverName().getString());
            return sendPacket("com.extendedae_plus.network.crafting.OpenCraftFromJeiC2SPacket",
                    genericStack, genericStackClass);
        } catch (Exception e) {
            ModLogger.info("EAEPProxy: openCraftScreen reflection error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    public static boolean pullFromNetwork(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            ModLogger.info("EAEPProxy: pullFromNetwork skipped, loaded={} stack={}", isLoaded(), stack);
            return false;
        }
        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
            Object aeKey = ofMethod.invoke(null, stack);
            if (aeKey == null) return false;

            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            Constructor<?> gsCtor = genericStackClass.getDeclaredConstructor(aeKeyClass, long.class);
            Object genericStack = gsCtor.newInstance(aeKey, 1L);

            ModLogger.info("EAEPProxy: pullFromNetwork sending packet for {}", stack.getHoverName().getString());
            return sendPacket("com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket",
                    genericStack, genericStackClass);
        } catch (Exception e) {
            ModLogger.info("EAEPProxy: pullFromNetwork reflection error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private static boolean sendPacket(String className, Object genericStack, Class<?> gsClass) {
        try {
            Class<?> packetClass = Class.forName(className);
            Constructor<?> ctor = packetClass.getConstructor(gsClass);
            Object packet = ctor.newInstance(genericStack);

            Object channel = findChannel();
            if (channel != null) {
                channel.getClass().getMethod("sendToServer", Object.class).invoke(channel, packet);
                ModLogger.info("EAEPProxy: sent via channel for {}", className);
                return true;
            }
            ModLogger.info("EAEPProxy: channel not found for {}", className);
        } catch (Exception e) {
            ModLogger.info("EAEPProxy: sendPacket (method 1) error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }

        try {
            // Some EAEP versions use a static send method
            Class<?> packetClass = Class.forName(className);
            Constructor<?> ctor = packetClass.getConstructor(gsClass);
            Object packet = ctor.newInstance(genericStack);

            Class<?> networkHandler = Class.forName("com.extendedae_plus.network.NetworkHandler");
            var sendMethod = networkHandler.getMethod("sendToServer", packetClass);
            sendMethod.invoke(null, packet);
            ModLogger.info("EAEPProxy: sent via NetworkHandler.sendToServer for {}", className);
            return true;
        } catch (Exception e) {
            ModLogger.info("EAEPProxy: sendPacket (method 2) error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private static Object findChannel() {
        String[][] attempts = {
                {"com.extendedae_plus.init.ModNetwork", "CHANNEL"},
                {"com.extendedae_plus.network.NetworkHandler", "CHANNEL"},
                {"com.extendedae_plus.network.NetworkHandler", "INSTANCE"},
                {"com.extendedae_plus.network.EAEPNetworkHandler", "CHANNEL"},
        };
        for (var pair : attempts) {
            try {
                Class<?> cls = Class.forName(pair[0]);
                var field = cls.getDeclaredField(pair[1]);
                field.setAccessible(true);
                Object val = field.get(null);
                if (val != null) return val;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
