package org.chatterjay.emilink.network;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AE2GridQueryUtil {
    private AE2GridQueryUtil() {
    }

    public static Object resolveGrid(Class<?> menuClass, Object menu) {
        Object grid = resolveFromActionHost(menuClass, menu);
        if (grid != null) return grid;

        grid = resolveFromActionSource(menuClass, menu);
        if (grid != null) return grid;

        return resolveFromBlockEntity(menuClass, menu);
    }

    public static long queryItemCount(Object grid, Object aeKey) {
        long result = 0;

        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid", "getService");
            if (storageSvc != null) {
                Object inventory = callMethodOnBestMatch(storageSvc, "getInventory");
                if (inventory != null) {
                    Object available = callMethodOnBestMatch(inventory, "getAvailableStacks");
                    if (available != null) {
                        Object raw = getKeyCounterValue(available, aeKey);
                        if (raw instanceof Number n) {
                            result = n.longValue();
                            if (result > 0) return result;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Object storageSvc = callMethodOnBestMatch(grid, "getStorageService", "getStorageGrid");
            if (storageSvc != null) {
                Object cached = callMethodOnBestMatch(storageSvc, "getCachedAvailableStacks", "getAvailableStacks");
                if (cached != null) {
                    Object raw = getKeyCounterValue(cached, aeKey);
                    if (raw instanceof Number n) {
                        result = n.longValue();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public static boolean queryCraftability(Object grid, Object aeKey) {
        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid", "getService");
            if (craftingSvc != null) {
                Object raw = tryCallMethod(craftingSvc.getClass(), craftingSvc, "isCraftable", aeKey);
                if (raw instanceof Boolean b) return b;
            }
        } catch (Exception ignored) {
        }

        try {
            Object craftingSvc = callMethodOnBestMatch(grid, "getCraftingService", "getCraftingGrid");
            if (craftingSvc != null) {
                for (var iface : getAllInterfaces(craftingSvc.getClass())) {
                    if (!iface.getName().contains("Crafting")) continue;
                    for (var paramCls = aeKey.getClass(); paramCls != null; paramCls = paramCls.getSuperclass()) {
                        try {
                            Object raw = iface.getMethod("isCraftable", paramCls).invoke(craftingSvc, aeKey);
                            if (raw instanceof Boolean b) return b;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    public static Object callMethodOnBestMatch(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Object tryCallMethod(Class<?> clazz, Object target, String methodName, Object arg) {
        try {
            return clazz.getMethod(methodName, arg.getClass()).invoke(target, arg);
        } catch (Exception ignored) {
        }

        for (var cls = arg.getClass().getSuperclass(); cls != null; cls = cls.getSuperclass()) {
            try {
                return clazz.getMethod(methodName, cls).invoke(target, arg);
            } catch (Exception ignored) {
            }
        }

        for (var iface : getAllInterfaces(arg.getClass())) {
            try {
                return clazz.getMethod(methodName, iface).invoke(target, arg);
            } catch (Exception ignored) {
            }
        }

        try {
            return clazz.getMethod(methodName, Object.class).invoke(target, arg);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object resolveFromActionHost(Class<?> menuClass, Object menu) {
        try {
            var getActionHost = menuClass.getDeclaredMethod("getActionHost");
            getActionHost.setAccessible(true);
            Object actionHost = getActionHost.invoke(menu);
            if (actionHost == null) return null;

            for (var iface : getAllInterfaces(actionHost.getClass())) {
                if (!iface.getName().contains("ActionHost") && !iface.getName().contains("IActionHost")) continue;
                for (var method : iface.getMethods()) {
                    if (method.getParameterCount() != 0) continue;
                    String retName = method.getReturnType().getName();
                    if (!retName.contains("Grid") && !retName.contains("Node")) continue;
                    Object result = method.invoke(actionHost);
                    Object grid = unwrapGrid(result);
                    if (grid != null) return grid;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object resolveFromActionSource(Class<?> menuClass, Object menu) {
        try {
            Object actionSource = menuClass.getMethod("getActionSource").invoke(menu);
            if (actionSource == null) return null;

            Object machineOpt = actionSource.getClass().getMethod("getMachineSource").invoke(actionSource);
            if (!(machineOpt instanceof java.util.Optional<?> opt) || opt.isEmpty()) return null;

            Object machine = opt.get();
            for (var method : machine.getClass().getMethods()) {
                if (method.getParameterCount() != 0) continue;
                String name = method.getName();
                if (!name.contains("Grid") && !name.contains("grid") && !name.contains("Node")) continue;

                Object result = method.invoke(machine);
                Object grid = unwrapGrid(result);
                if (grid != null) return grid;
                if (result != null && method.getReturnType().getName().contains("IGrid")) return result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object resolveFromBlockEntity(Class<?> menuClass, Object menu) {
        try {
            Object host = menuClass.getMethod("getBlockEntity").invoke(menu);
            if (host == null) return null;

            for (var iface : getAllInterfaces(host.getClass())) {
                if (!iface.getName().contains("Grid") && !iface.getName().contains("Node")) continue;
                try {
                    Method getNode = iface.getMethod("getGridNode");
                    Object grid = unwrapGrid(getNode.invoke(host));
                    if (grid != null) return grid;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object unwrapGrid(Object result) {
        if (result == null) return null;
        try {
            Object grid = result.getClass().getMethod("getGrid").invoke(result);
            if (grid != null) return grid;
        } catch (Exception ignored) {
        }
        return result.getClass().getName().contains("IGrid") ? result : null;
    }

    private static Object getKeyCounterValue(Object keyCounter, Object aeKey) throws Exception {
        Class<?> kc = Class.forName("appeng.api.stacks.KeyCounter");
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        return kc.getMethod("get", aeKeyClass).invoke(keyCounter, aeKey);
    }

    public static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
        var interfaces = new LinkedHashSet<Class<?>>();
        while (clazz != null) {
            for (var iface : clazz.getInterfaces()) {
                collectInterfaces(iface, interfaces);
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }

    private static void collectInterfaces(Class<?> iface, Set<Class<?>> acc) {
        if (acc.add(iface)) {
            for (var parent : iface.getInterfaces()) {
                collectInterfaces(parent, acc);
            }
        }
    }
}
