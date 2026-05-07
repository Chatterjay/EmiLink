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

    public static boolean openCraftScreen(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
            Object aeKey = ofMethod.invoke(null, stack);
            if (aeKey == null) return false;

            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            Constructor<?> gsCtor = genericStackClass.getConstructor(aeKeyClass, long.class);
            Object genericStack = gsCtor.newInstance(aeKey, 1L);

            var clazz = Class.forName("com.extendedae_plus.network.OpenCraftFromJeiC2SPacket");
            var ctor = clazz.getConstructor(genericStackClass);
            var packet = ctor.newInstance(genericStack);
            PacketDistributor.sendToServer((CustomPacketPayload) packet);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean pullFromNetwork(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
            Object aeKey = ofMethod.invoke(null, stack);
            if (aeKey == null) return false;

            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            Constructor<?> gsCtor = genericStackClass.getConstructor(aeKeyClass, long.class);
            Object genericStack = gsCtor.newInstance(aeKey, 1L);

            var clazz = Class.forName("com.extendedae_plus.network.PullFromJeiOrCraftC2SPacket");
            var ctor = clazz.getConstructor(genericStackClass);
            var packet = ctor.newInstance(genericStack);
            PacketDistributor.sendToServer((CustomPacketPayload) packet);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
