package org.chatterjay.emiextend.integration;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    /**
     * Forge 1.20.1: EAEP's packets must be sent through EAEP's own SimpleChannel.
     * We attempt to find the channel by common names and reflectively call sendToServer.
     */
    private static boolean sendPacket(ItemStack stack, String packetClassName) {
        if (!isLoaded() || stack == null || stack.isEmpty()) return false;
        try {
            Object genericStack = buildGenericStack(stack);
            if (genericStack == null) return false;
            var clazz = Class.forName(packetClassName);
            var ctor = clazz.getConstructor(genericStack.getClass());
            Object packet = ctor.newInstance(genericStack);

            // Try common EAEP network channel class/field names
            String[] candidateClasses = {
                    "com.extendedae_plus.network.EAEPNetwork",
                    "com.extendedae_plus.network.NetworkHandler",
                    "com.extendedae_plus.network.EAEPNetworking",
                    "com.extendedae_plus.ExtendedAEPlus$Network"
            };
            String[] candidateFields = {"CHANNEL", "INSTANCE", "channel", "instance"};

            for (String clsName : candidateClasses) {
                try {
                    Class<?> cls = Class.forName(clsName);
                    for (String fieldName : candidateFields) {
                        try {
                            Field f = cls.getDeclaredField(fieldName);
                            f.setAccessible(true);
                            Object channel = f.get(null);
                            if (channel != null) {
                                try {
                                    Method send = channel.getClass().getMethod("sendToServer", Object.class);
                                    send.invoke(channel, packet);
                                    return true;
                                } catch (NoSuchMethodException nsme) {
                                    // try alternative signature
                                }
                            }
                        } catch (NoSuchFieldException ignored) {}
                    }
                } catch (ClassNotFoundException ignored) {}
            }
            return false;
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
