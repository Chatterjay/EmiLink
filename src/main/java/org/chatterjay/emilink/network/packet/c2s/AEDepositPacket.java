package org.chatterjay.emilink.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.chatterjay.emilink.Config;
import org.chatterjay.emilink.util.ModLogger;

import java.lang.reflect.Method;
import java.util.function.Supplier;

public class AEDepositPacket {
    private final ItemStack stack;
    private final int slotIndex;

    /**
     * @param stack     the item stack to deposit (prototype for batch mode)
     * @param slotIndex -1 = cursor item, -2 = batch deposit all matching from inventory, >= 0 = specific slot
     */
    public AEDepositPacket(ItemStack stack, int slotIndex) {
        this.stack = stack;
        this.slotIndex = slotIndex;
    }

    public static void encode(AEDepositPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
        buf.writeVarInt(msg.slotIndex);
    }

    public static AEDepositPacket decode(FriendlyByteBuf buf) {
        return new AEDepositPacket(buf.readItem(), buf.readVarInt());
    }

    public static void handle(AEDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> msg.handleInServer(ctx.get()));
        ctx.get().setPacketHandled(true);
    }

    private void handleInServer(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null || stack == null || stack.isEmpty()) return;

        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Object modulate = actionableClass.getField("MODULATE").get(null);
            Object actionSource = actionSourceClass.getMethod("ofPlayer", Player.class).invoke(null, player);

            Object inventory = resolveInventoryFromMenu(player);
            if (inventory == null) {
                inventory = resolveInventoryFromWirelessTerminal(player);
            }
            if (inventory == null) {
                player.sendSystemMessage(Component.translatable("message.emilink.deposit.no_ae_access"));
                ModLogger.info("AEDeposit: no grid available for player {}", player.getName().getString());
                return;
            }

            var insertMethod = inventory.getClass().getMethod("insert", aeKeyClass, long.class, actionableClass, actionSourceClass);

            if (slotIndex == -2) {
                // Batch deposit: find all matching items in inventory
                batchDeposit(player, inventory, aeItemKeyClass, aeKeyClass, insertMethod, modulate, actionSource);
            } else {
                // Single deposit (cursor or specific slot)
                Object aeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
                if (aeKey == null) return;

                long inserted = (long) insertMethod.invoke(inventory, aeKey, (long) stack.getCount(), modulate, actionSource);
                if (inserted <= 0) {
                    player.sendSystemMessage(Component.translatable("message.emilink.deposit.failed", stack.getHoverName()));
                    return;
                }

                if (slotIndex == -1) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                } else if (!player.isCreative() && slotIndex >= 0 && slotIndex < player.getInventory().items.size()) {
                    player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                }

                player.sendSystemMessage(Component.translatable("message.emilink.deposit.success",
                        stack.getHoverName(), inserted));
                ModLogger.info("AEDeposit: deposited {} x{}", stack.getHoverName().getString(), inserted);
            }

            player.containerMenu.broadcastChanges();
        } catch (Exception e) {
            ModLogger.warn("AEDeposit error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            player.sendSystemMessage(Component.translatable("message.emilink.deposit.error"));
        }
    }

    private void batchDeposit(Player player, Object inventory, Class<?> aeItemKeyClass, Class<?> aeKeyClass,
                              Method insertMethod, Object modulate, Object actionSource) throws Exception {
        Object prototypeKey = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, stack);
        if (prototypeKey == null) return;

        long totalInserted = 0;
        var inv = player.getInventory();

        // Check cursor item
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && itemsMatch(carried, stack)) {
            Object key = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, carried);
            long inserted = (long) insertMethod.invoke(inventory, key, (long) carried.getCount(), modulate, actionSource);
            if (inserted > 0) {
                totalInserted += inserted;
                if (inserted >= carried.getCount()) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                } else {
                    carried.shrink((int) inserted);
                }
            }
        }

        // Check inventory slots
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack invStack = inv.getItem(i);
            if (invStack.isEmpty()) continue;
            if (!itemsMatch(invStack, stack)) continue;

            Object key = aeItemKeyClass.getMethod("of", ItemStack.class).invoke(null, invStack);
            long inserted = (long) insertMethod.invoke(inventory, key, (long) invStack.getCount(), modulate, actionSource);
            if (inserted > 0) {
                totalInserted += inserted;
                if (inserted >= invStack.getCount()) {
                    inv.setItem(i, ItemStack.EMPTY);
                } else {
                    invStack.shrink((int) inserted);
                }
            }
        }

        if (totalInserted > 0) {
            player.sendSystemMessage(Component.translatable("message.emilink.deposit.batch_success",
                    stack.getHoverName(), totalInserted));
            ModLogger.info("AEDeposit: batch deposited {} x{}", stack.getHoverName().getString(), totalInserted);
        } else {
            player.sendSystemMessage(Component.translatable("message.emilink.deposit.batch_none",
                    stack.getHoverName()));
        }
    }

    private static boolean itemsMatch(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    private static Object resolveInventoryFromMenu(Player player) {
        try {
            var menu = player.containerMenu;
            Class<?> aeBaseMenuClass = Class.forName("appeng.menu.AEBaseMenu");
            if (!aeBaseMenuClass.isInstance(menu)) return null;

            Object grid = resolveGrid(aeBaseMenuClass, menu);
            if (grid == null) return null;

            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc == null) return null;

            return callMethodOnBestMatch(storageSvc, "getInventory");
        } catch (Exception e) {
            return null;
        }
    }

    private static Object resolveInventoryFromWirelessTerminal(Player player) {
        ModLogger.info("AEDeposit: resolveInventoryFromWirelessTerminal start");
        try {
            ItemStack terminal = findWirelessTerminal(player);
            if (terminal == null || terminal.isEmpty()) {
                ModLogger.info("AEDeposit: resolveInventoryFromWirelessTerminal - no terminal found");
                return null;
            }
            ModLogger.info("AEDeposit: found terminal {} class={}", terminal.getHoverName().getString(), terminal.getItem().getClass().getName());

            java.util.function.Consumer<?> noop = msg -> {};
            Object grid = null;

            // Approach 1: Direct getLinkedGrid call with Level.class
            try {
                ModLogger.info("AEDeposit: trying approach 1 - getLinkedGrid(ItemStack, Level, Consumer)");
                Method m = terminal.getItem().getClass().getMethod("getLinkedGrid",
                        ItemStack.class, net.minecraft.world.level.Level.class, java.util.function.Consumer.class);
                grid = m.invoke(terminal.getItem(), terminal, player.level(), noop);
                ModLogger.info("AEDeposit: approach 1 result = {}", grid == null ? "null" : "FOUND");
            } catch (Exception e) {
                ModLogger.info("AEDeposit: approach 1 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            }

            // Approach 2: Try AbstractWirelessTerminalItem class directly
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 2 - AbstractWirelessTerminalItem");
                    Class<?> parentClass = Class.forName("appeng.items.tools.powered.AbstractWirelessTerminalItem");
                    Method m = parentClass.getMethod("getLinkedGrid",
                            ItemStack.class, net.minecraft.world.level.Level.class, java.util.function.Consumer.class);
                    grid = m.invoke(terminal.getItem(), terminal, player.level(), noop);
                    ModLogger.info("AEDeposit: approach 2 result = {}", grid == null ? "null" : "FOUND");
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 2 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 3: Try different method name
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 3 - getGrid");
                    Method m = terminal.getItem().getClass().getMethod("getGrid",
                            ItemStack.class, net.minecraft.world.level.Level.class);
                    grid = m.invoke(terminal.getItem(), terminal, player.level());
                    ModLogger.info("AEDeposit: approach 3 result = {}", grid == null ? "null" : "FOUND");
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 3 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 4: Try AE API wireless handler
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 4 - AEApi wireless handler");
                    Class<?> aeClass = Class.forName("appeng.api.AE");
                    Object aeInstance = aeClass.getMethod("instance").invoke(null);
                    Object wirelessHandler = aeClass.getMethod("wireless").invoke(aeInstance);
                    Method getGrid = findMethodByParamNames(wirelessHandler.getClass(), "getGrid",
                            new Class<?>[]{ItemStack.class, player.level().getClass(), Player.class});
                    if (getGrid == null) {
                        getGrid = findMethodByParamNames(wirelessHandler.getClass(), "getGrid",
                                new Class<?>[]{ItemStack.class, net.minecraft.world.level.Level.class, Player.class});
                    }
                    if (getGrid != null) {
                        grid = getGrid.invoke(wirelessHandler, terminal, player.level(), player);
                        ModLogger.info("AEDeposit: approach 4 result = {}", grid == null ? "null" : "FOUND");
                    } else {
                        ModLogger.info("AEDeposit: approach 4 - getGrid method not found");
                    }
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 4 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 5: try getGridKey via reflection (works for some AE2 items)
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 5 - getGridKey reflection");
                    Method getGridKey = terminal.getItem().getClass().getMethod("getGridKey", ItemStack.class);
                    Object gridKeyOpt = getGridKey.invoke(terminal.getItem(), terminal);
                    if (gridKeyOpt instanceof java.util.Optional<?> opt && opt.isPresent()) {
                        long gridKey = (Long) opt.get();
                        grid = lookupGridByKey(player, gridKey);
                        ModLogger.info("AEDeposit: approach 5 result = {}", grid == null ? "null" : "FOUND");
                    } else {
                        ModLogger.info("AEDeposit: approach 5 - grid key empty");
                    }
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 5 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 6: Read gridKey directly from NBT tag
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 6 - NBT direct gridKey");
                    var tag = terminal.getTag();
                    if (tag != null) {
                        ModLogger.info("AEDeposit: approach 6 - NBT keys: {}", tag.getAllKeys());
                        if (tag.contains("gridKey", net.minecraft.nbt.Tag.TAG_LONG)) {
                            long gridKey = tag.getLong("gridKey");
                            ModLogger.info("AEDeposit: approach 6 - found gridKey {} in NBT", gridKey);
                            grid = lookupGridByKey(player, gridKey);
                        } else {
                            ModLogger.info("AEDeposit: approach 6 - no gridKey in NBT, checking other tags...");
                            // Dump first-level NBT values for debugging
                            for (String k : tag.getAllKeys()) {
                                var v = tag.get(k);
                                ModLogger.info("AEDeposit:  NBT[{}] = {} ({})", k, v, v.getClass().getSimpleName());
                            }
                        }
                    } else {
                        ModLogger.info("AEDeposit: approach 6 - NBT tag is null");
                    }
                    ModLogger.info("AEDeposit: approach 6 result = {}", grid == null ? "null" : "FOUND");
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 6 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 7: IAEWirelessTerminalItem interface
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 7 - IAEWirelessTerminalItem");
                    Class<?> iface = Class.forName("appeng.api.implementations.items.IAEWirelessTerminalItem");
                    if (iface.isInstance(terminal.getItem())) {
                        Method getGrid = iface.getMethod("getGrid", ItemStack.class, net.minecraft.world.level.Level.class, java.util.function.Consumer.class);
                        grid = getGrid.invoke(terminal.getItem(), terminal, player.level(), noop);
                        ModLogger.info("AEDeposit: approach 7 result = {}", grid == null ? "null" : "FOUND");
                    } else {
                        ModLogger.info("AEDeposit: approach 7 - item does not implement IAEWirelessTerminalItem");
                    }
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 7 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 8: AE2WTLib WUTHandler
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 8 - AE2WTLib WUTHandler");
                    Class<?> wutHandler = Class.forName("de.mari_023.ae2wtlib.wut.WUTHandler");
                    try {
                        Method getGrid = wutHandler.getMethod("getGrid", ItemStack.class, net.minecraft.world.level.Level.class);
                        grid = getGrid.invoke(null, terminal, player.level());
                    } catch (NoSuchMethodException e1) {
                        try {
                            Method getLinkedGrid = wutHandler.getMethod("getLinkedGrid", ItemStack.class, net.minecraft.world.level.Level.class, java.util.function.Consumer.class);
                            grid = getLinkedGrid.invoke(null, terminal, player.level(), noop);
                        } catch (NoSuchMethodException e2) {
                            ModLogger.info("AEDeposit: approach 8 - no suitable method found on WUTHandler");
                        }
                    }
                    ModLogger.info("AEDeposit: approach 8 result = {}", grid == null ? "null" : "FOUND");
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 8 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            // Approach 9: Diagnostic — list all implemented interfaces and grid-related methods
            if (grid == null && Config.DEBUG_MODE.get()) {
                try {
                    ModLogger.info("AEDeposit: DIAG - interfaces of {}:", terminal.getItem().getClass().getName());
                    for (var iface : getAllInterfaces(terminal.getItem().getClass())) {
                        ModLogger.info("AEDeposit:  implements {}", iface.getName());
                    }
                    ModLogger.info("AEDeposit: DIAG - grid-related methods:");
                    for (var m : terminal.getItem().getClass().getMethods()) {
                        String mn = m.getName().toLowerCase(java.util.Locale.ROOT);
                        if (mn.contains("grid") || mn.contains("key") || mn.contains("terminal") || mn.contains("wireless") || mn.contains("link")) {
                            ModLogger.info("AEDeposit:  method: {} -> {}", m.getName(), m.getReturnType().getSimpleName());
                        }
                    }
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: DIAG failed: {}", e.getMessage());
                }
            }

            // Approach 10: Method name scan — look for any grid-returning method on the item
            if (grid == null) {
                try {
                    ModLogger.info("AEDeposit: trying approach 10 - method name scan");
                    for (var m : terminal.getItem().getClass().getMethods()) {
                        if (m.getParameterCount() > 0) continue;
                        String name = m.getName();
                        if (!name.equals("getGrid") && !name.contains("Grid") && !name.contains("grid")) continue;
                        try {
                            Object result = m.invoke(terminal.getItem());
                            if (result != null) {
                                try {
                                    grid = result.getClass().getMethod("getGrid").invoke(result);
                                    if (grid != null) {
                                        ModLogger.info("AEDeposit: approach 10 - got grid via {}.{}", terminal.getItem().getClass().getSimpleName(), name);
                                        break;
                                    }
                                } catch (NoSuchMethodException e3) {
                                    if (m.getReturnType().getName().contains("IGrid")) {
                                        grid = result;
                                        ModLogger.info("AEDeposit: approach 10 - got grid directly via {}", name);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e2) {
                            // skip
                        }
                    }
                    ModLogger.info("AEDeposit: approach 10 result = {}", grid == null ? "null" : "FOUND");
                } catch (Exception e) {
                    ModLogger.info("AEDeposit: approach 10 failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
                }
            }

            if (grid == null) {
                ModLogger.info("AEDeposit: ALL approaches failed - no grid from wireless terminal");
                return null;
            }

            ModLogger.info("AEDeposit: got grid = {} class = {}", grid, grid.getClass().getName());
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            ModLogger.info("AEDeposit: storageSvc = {}", storageSvc == null ? "null" : storageSvc.getClass().getName());
            if (storageSvc == null) return null;

            Object inv = callMethodOnBestMatch(storageSvc, "getInventory");
            ModLogger.info("AEDeposit: inventory = {}", inv == null ? "null" : "FOUND");
            return inv;
        } catch (Exception e) {
            ModLogger.warn("AEDeposit: wireless terminal error: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static Method findMethodByParamNames(Class<?> clazz, String name, Class<?>[] paramTypes) {
        // Try exact match first
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {}

        // Walk the hierarchy and try each param type variant
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (var method : c.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                if (method.getParameterCount() != paramTypes.length) continue;
                boolean match = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (!method.getParameterTypes()[i].isAssignableFrom(paramTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static ItemStack findWirelessTerminal(Player player) {
        try {
            Class<?> wtClass = Class.forName("appeng.items.tools.powered.WirelessTerminalItem");
            var inv = player.getInventory();
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.getItem(i);
                if (wtClass.isInstance(s.getItem())) {
                    ModLogger.info("AEDeposit: findWT - found in inventory slot {}", i);
                    return s;
                }
            }
            if (wtClass.isInstance(player.getOffhandItem().getItem())) {
                ModLogger.info("AEDeposit: findWT - found in offhand");
                return player.getOffhandItem();
            }

            // Check curios
            try {
                var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                var getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                Object lazyOpt = getCuriosInventory.invoke(null, player);
                var lazyOptClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
                if ((boolean) lazyOptClass.getMethod("isPresent").invoke(lazyOpt)) {
                    java.util.Optional<?> opt = (java.util.Optional<?>) lazyOptClass.getMethod("resolve").invoke(lazyOpt);
                    if (opt.isPresent()) {
                        Object handler = opt.get();
                        var curioItemHandler = handler.getClass().getMethod("getCurios");
                        Object curiosMap = curioItemHandler.invoke(handler);
                        if (curiosMap instanceof java.util.Map<?, ?> map) {
                            for (var entry : map.entrySet()) {
                                var stacks = entry.getValue().getClass().getMethod("getStacks").invoke(entry.getValue());
                                var slotInv = stacks.getClass().getMethod("getStackInSlot", int.class);
                                for (int i = 0; i < 100; i++) {
                                    try {
                                        ItemStack s = (ItemStack) slotInv.invoke(stacks, i);
                                        if (!s.isEmpty() && wtClass.isInstance(s.getItem())) {
                                            ModLogger.info("AEDeposit: findWT - found in curio slot {}", i);
                                            return s;
                                        }
                                    } catch (Exception e) { break; }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                ModLogger.info("AEDeposit: findWT - curios check failed: {}", e.getMessage());
            }
        } catch (Exception e) {
            ModLogger.warn("AEDeposit: findWirelessTerminal error: {}", e.getMessage());
        }
        ModLogger.info("AEDeposit: findWT - no terminal found");
        return ItemStack.EMPTY;
    }

    private static Object lookupGridByKey(Player player, long gridKey) throws Exception {
        // Use SecurityStationData to find the security station for this grid key
        Class<?> ssClass = Class.forName("appeng.worlddata.SecurityStationData");
        Object ssData = ssClass.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, player.getServer());
        if (ssData == null) return null;

        var byKeyMethod = ssData.getClass().getMethod("getByKey", long.class);
        Object stationOpt = byKeyMethod.invoke(ssData, gridKey);
        if (!(stationOpt instanceof java.util.Optional<?> opt) || opt.isEmpty()) return null;

        Object station = opt.get();
        for (var iface : getAllInterfaces(station.getClass())) {
            if (iface.getName().contains("ActionHost") || iface.getName().contains("IActionHost")) {
                for (var m : iface.getMethods()) {
                    if (m.getParameterCount() != 0) continue;
                    try {
                        Object node = m.invoke(station);
                        if (node != null) {
                            try {
                                Object grid = node.getClass().getMethod("getGrid").invoke(node);
                                if (grid != null) return grid;
                            } catch (NoSuchMethodException ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
                break;
            }
        }
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
                                        } catch (NoSuchMethodException e) {}
                                    }
                                } catch (Exception e) {}
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {}

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
                        } catch (Exception e2) {}
                    }
                }
            }
        } catch (Exception e) {}

        return null;
    }

    private static Object callMethodOnBestMatch(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                var m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (Exception e) {}
        }
        return null;
    }

    private static java.util.Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var interfaces = new java.util.LinkedHashSet<Class<?>>();
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
}
