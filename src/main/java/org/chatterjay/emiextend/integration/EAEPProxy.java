package org.chatterjay.emiextend.integration;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;

public class EAEPProxy {
    private static Boolean loaded;

    private static boolean isLoaded() {
        if (loaded == null) {
            var modList = ModList.get();
            loaded = modList != null && modList.isLoaded("extendedae_plus");
        }
        return loaded;
    }

    /** Build an AEItemKey then wrap in a GenericStack, all via reflection. */
    private static Object buildGenericStack(ItemStack stack) throws Exception {
        Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
        var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
        Object aeKey = ofMethod.invoke(null, stack);
        if (aeKey == null) return null;

        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
        Constructor<?> gsCtor = genericStackClass.getConstructor(aeKeyClass, long.class);
        return gsCtor.newInstance(aeKey, 1L);
    }

    private static boolean sendPacket(ItemStack stack, String packetClassName) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            Object genericStack = buildGenericStack(stack);
            if (genericStack == null) return false;
            var clazz = Class.forName(packetClassName);
            var ctor = clazz.getConstructor(genericStack.getClass());
            var packet = ctor.newInstance(genericStack);
            PacketDistributor.sendToServer((CustomPacketPayload) packet);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openCraftScreen(ItemStack stack) {
        return sendPacket(stack, "com.extendedae_plus.network.OpenCraftFromJeiC2SPacket");
    }

    public static boolean pullFromNetwork(ItemStack stack) {
        return sendPacket(stack, "com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket");
    }
}
