package org.chatterjay.emiextend.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.chatterjay.emiextend.EmiAE2;
import org.chatterjay.emiextend.integration.AE2Proxy;
import org.chatterjay.emiextend.network.packet.s2c.AEBatchQueryResponsePacket;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Batch AE network query — client sends multiple items at once, server responds
 * with count & craftability for each. Reuses the same query logic as AEQueryPacket.
 */
public record AEBatchQueryPacket(List<ItemStack> stacks) implements CustomPacketPayload {
    public static final Type<AEBatchQueryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EmiAE2.MODID, "ae_batch_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AEBatchQueryPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public void encode(RegistryFriendlyByteBuf buf, AEBatchQueryPacket packet) {
                    buf.writeVarInt(packet.stacks().size());
                    for (var stack : packet.stacks()) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                    }
                }

                @Override
                public AEBatchQueryPacket decode(RegistryFriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    var stacks = new ArrayList<ItemStack>(size);
                    for (int i = 0; i < size; i++) {
                        stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    }
                    return new AEBatchQueryPacket(stacks);
                }
            };

    private void handleInServer(final IPayloadContext context) {
        Player player = context.player();
        if (player == null || stacks == null || stacks.isEmpty()) return;
        if (!AE2Proxy.isLoaded()) return;

        var results = new ArrayList<AEBatchQueryResponsePacket.Entry>();

        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) return;

            Object grid = resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) return;

            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");

            for (var stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;

                long count = 0;
                boolean craftable = false;

                try {
                    Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
                    if (aeKey != null) {
                        count = queryItemCount(grid, aeKey);
                        craftable = queryCraftability(grid, aeKey);
                    }
                } catch (Exception e) {
                    // skip item
                }

                results.add(new AEBatchQueryResponsePacket.Entry(stack, count, craftable));
            }
        } catch (Exception e) {
            ModLogger.debug("AEBatchQuery: error resolving grid: {}", e.getMessage());
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new AEBatchQueryResponsePacket(results));
        }
    }

    // ---- Query logic (mirrors AEQueryPacket) ----

    private static long queryItemCount(Object grid, Object aeKey) {
        long result = 0;

        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid", "getService");
            if (storageSvc != null) {
                Object inventory = callMethodOnBestMatch(storageSvc, "getInventory");
                if (inventory != null) {
                    Object available = callMethodOnBestMatch(inventory, "getAvailableStacks");
                    if (available != null) {
                        Class<?> kc = Class.forName("appeng.api.stacks.KeyCounter");
                        Object raw = kc.getMethod("get", Class.forName("appeng.api.stacks.AEKey")).invoke(available, aeKey);
                        if (raw instanceof Number n) {
                            result = n.longValue();
                            if (result > 0) return result;
                        }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }

        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc != null) {
                Object cached = callMethodOnBestMatch(storageSvc, "getCachedAvailableStacks", "getAvailableStacks");
                if (cached != null) {
                    Class<?> kc = Class.forName("appeng.api.stacks.KeyCounter");
                    Object raw = kc.getMethod("get", Class.forName("appeng.api.stacks.AEKey")).invoke(cached, aeKey);
                    if (raw instanceof Number n) {
                        result = n.longValue();
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }

        return result;
    }

    private static boolean queryCraftability(Object grid, Object aeKey) {
        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid", "getService");
            if (craftingSvc != null) {
                Object raw = tryCallMethod(craftingSvc.getClass(), craftingSvc, "isCraftable", aeKey);
                if (raw instanceof Boolean b) return b;
            }
        } catch (Exception e) { /* ignore */ }

        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid");
            if (craftingSvc != null) {
                for (var iface : getAllInterfaces(craftingSvc.getClass())) {
                    if (!iface.getName().contains("Crafting")) continue;
                    for (var paramCls = aeKey.getClass(); paramCls != null; paramCls = paramCls.getSuperclass()) {
                        try {
                            Object raw = iface.getMethod("isCraftable", paramCls).invoke(craftingSvc, aeKey);
                            if (raw instanceof Boolean b) return b;
                        } catch (NoSuchMethodException e) { /* try next */ }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }

        return false;
    }

    private static Object callMethodOnBestMatch(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Exception e) { /* try next */ }
        }
        return null;
    }

    private static Object tryCallMethod(Class<?> clazz, Object target, String methodName, Object arg) {
        try {
            return clazz.getMethod(methodName, arg.getClass()).invoke(target, arg);
        } catch (Exception e) { /* fall through */ }

        for (var cls = arg.getClass().getSuperclass(); cls != null; cls = cls.getSuperclass()) {
            try {
                return clazz.getMethod(methodName, cls).invoke(target, arg);
            } catch (Exception e2) { /* continue */ }
        }

        for (var iface : getAllInterfaces(arg.getClass())) {
            try {
                return clazz.getMethod(methodName, iface).invoke(target, arg);
            } catch (Exception e2) { /* try next */ }
        }

        try {
            return clazz.getMethod(methodName, Object.class).invoke(target, arg);
        } catch (Exception e2) { /* give up */ }

        return null;
    }

    private static Object resolveGrid(Class<?> menuClass, Object menu) throws Exception {
        try {
            var getActionHost = menuClass.getDeclaredMethod("getActionHost");
            getActionHost.setAccessible(true);
            Object actionHost = getActionHost.invoke(menu);
            if (actionHost != null) {
                for (var iface : getAllInterfaces(actionHost.getClass())) {
                    if (iface.getName().contains("ActionHost") || iface.getName().contains("IActionHost")) {
                        for (var m : iface.getMethods()) {
                            if (m.getParameterCount() != 0) continue;
                            String retName = m.getReturnType().getName();
                            if (retName.contains("Grid") || retName.contains("Node")) {
                                try {
                                    Object result = m.invoke(actionHost);
                                    if (result != null) {
                                        try {
                                            Object grid = result.getClass().getMethod("getGrid").invoke(result);
                                            if (grid != null) return grid;
                                        } catch (NoSuchMethodException e) { /* result may be grid itself */ }
                                    }
                                } catch (Exception e) { /* try next */ }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }

        try {
            Object actionSource = menuClass.getMethod("getActionSource").invoke(menu);
            if (actionSource != null) {
                var getMachine = actionSource.getClass().getMethod("getMachineSource");
                Object machineOpt = getMachine.invoke(actionSource);
                if (machineOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    Object machine = opt.get();
                    for (var m : machine.getClass().getMethods()) {
                        if (m.getParameterCount() != 0) continue;
                        String mn = m.getName();
                        if (!mn.contains("Grid") && !mn.contains("grid") && !mn.contains("Node")) continue;
                        try {
                            Object result = m.invoke(machine);
                            if (result == null) continue;
                            try {
                                Object grid = result.getClass().getMethod("getGrid").invoke(result);
                                if (grid != null) return grid;
                            } catch (NoSuchMethodException e2) {
                                if (m.getReturnType().getName().contains("IGrid")) return result;
                            }
                        } catch (Exception e2) { /* try next */ }
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }

        try {
            Object host = menuClass.getMethod("getBlockEntity").invoke(menu);
            if (host != null) {
                for (var iface : getAllInterfaces(host.getClass())) {
                    if (!iface.getName().contains("Grid") && !iface.getName().contains("Node")) continue;
                    try {
                        Method getNode = iface.getMethod("getGridNode");
                        Object gridNode = getNode.invoke(host);
                        if (gridNode != null) {
                            Object grid = gridNode.getClass().getMethod("getGrid").invoke(gridNode);
                            if (grid != null) return grid;
                        }
                    } catch (NoSuchMethodException e) { /* try next */ }
                }
            }
        } catch (Exception e) { /* ignore */ }

        return null;
    }

    private static java.util.Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var interfaces = new LinkedHashSet<Class<?>>();
        while (clazz != null) {
            for (var iface : clazz.getInterfaces()) {
                collectInterfaces(iface, interfaces);
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> iface, java.util.Set<Class<?>> acc) {
        if (acc.add(iface)) {
            for (var parent : iface.getInterfaces()) {
                collectInterfaces(parent, acc);
            }
        }
    }

    public static void handle(final AEBatchQueryPacket packet, final IPayloadContext context) {
        if (packet != null && context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> packet.handleInServer(context));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
