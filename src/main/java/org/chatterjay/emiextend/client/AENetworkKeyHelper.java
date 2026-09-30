package org.chatterjay.emiextend.client;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.emiextend.util.ModLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Maps EMI stacks onto the ItemStack that identifies them inside
 * {@link AENetworkCache}.
 * <p>
 * Plain items keep their own ItemStack identity, so the existing item pipeline
 * (components, BOM durability matching, disk cache) is untouched. AE keys that
 * are not items — fluids, chemicals and any other key type registered through
 * AE2's EMI converters — are wrapped with AE2's own display wrapper
 * ({@code AEKey#wrapForDisplayOrFilter}), which stores the complete key in a
 * component. That keeps a stable, component-equal cache key and lets the server
 * recover the original {@code AEKey} with {@code GenericStack#unwrapItemStack}
 * without any protocol change.
 * <p>
 * AE2 is an optional dependency, so every AE2 type is reached reflectively and
 * a missing class simply disables the non-item path.
 */
public final class AENetworkKeyHelper {

    private AENetworkKeyHelper() {}

    /**
     * A cache/packet key plus the conversion needed to show its raw AE amount.
     * Fluids are stored in mB while players read them in buckets.
     */
    public record NetworkKey(ItemStack stack, long amountPerUnit, String unitSymbol) {

        /** Convert a raw AE amount into the unit players expect (items: unchanged). */
        public long displayCount(long rawCount) {
            if (amountPerUnit <= 1L || rawCount <= 0L) {
                return rawCount;
            }
            return rawCount / amountPerUnit;
        }
    }

    /** Marks a stack that has no AE representation, so it is not resolved twice. */
    private static final NetworkKey UNRESOLVED = new NetworkKey(ItemStack.EMPTY, 1L, null);

    private static final int MAX_MEMOIZED_STACKS = 4096;

    /**
     * EMI rebuilds its index on every search, so memoization uses identity and
     * is bounded to keep long sessions from growing without limit.
     */
    private static final Map<EmiStack, NetworkKey> memo = new IdentityHashMap<>();

    private static boolean reflectionChecked;
    private static boolean reflectionAvailable;
    private static Method toGenericStackMethod;
    private static Method genericStackWhatMethod;
    private static Method wrapForDisplayMethod;
    private static Method wrapInItemStackMethod;
    private static Method amountPerUnitMethod;
    private static Method keyTypeMethod;
    private static Method unitSymbolMethod;
    private static Method convertersMethod;
    private static Constructor<?> genericStackConstructor;
    private static Class<?> aeItemKeyClass;

    /**
     * Resolve an ingredient to a network key, using the first EMI stack that has
     * an AE representation.
     */
    public static NetworkKey resolve(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        if (ingredient instanceof EmiStack stack) {
            return resolve(stack);
        }
        var stacks = ingredient.getEmiStacks();
        if (stacks == null || stacks.isEmpty()) {
            return null;
        }
        for (var stack : stacks) {
            NetworkKey key = resolve(stack);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    /**
     * Resolve a single EMI stack. Returns null when the stack has no AE key and
     * no usable ItemStack — such stacks simply get no network badge.
     */
    public static NetworkKey resolve(EmiStack emiStack) {
        if (emiStack == null || emiStack.isEmpty()) {
            return null;
        }
        NetworkKey cached = memo.get(emiStack);
        if (cached != null) {
            return cached == UNRESOLVED ? null : cached;
        }

        NetworkKey resolved = compute(emiStack);
        if (resolved == null && !negativeIsStable()) {
            // AE2 registers its EMI converters during plugin init. A miss before
            // that point must not be remembered, or the cell would stay blank
            // for the rest of the session.
            return null;
        }
        if (memo.size() >= MAX_MEMOIZED_STACKS) {
            memo.clear();
        }
        memo.put(emiStack, resolved == null ? UNRESOLVED : resolved);
        return resolved;
    }

    private static NetworkKey compute(EmiStack emiStack) {
        ItemStack itemStack = emiStack.getItemStack();
        // Fast path: an item key is already identified by its own ItemStack.
        // Item keys never need AE2 reflection here.
        if (!itemStack.isEmpty() && emiStack.getKeyOfType(Item.class) != null) {
            return new NetworkKey(itemStack, 1L, null);
        }

        NetworkKey generic = resolveGenericKey(emiStack);
        if (generic != null) {
            return generic;
        }
        return itemStack.isEmpty() ? null : new NetworkKey(itemStack, 1L, null);
    }

    /** Convert through AE2's EMI converters and wrap non-item keys. */
    private static NetworkKey resolveGenericKey(EmiStack emiStack) {
        if (!initReflection()) {
            return null;
        }
        try {
            Object genericStack = toGenericStackMethod.invoke(null, emiStack);
            if (genericStack == null) {
                return null;
            }
            Object aeKey = genericStackWhatMethod.invoke(genericStack);
            if (aeKey == null) {
                return null;
            }
            if (aeItemKeyClass.isInstance(aeKey)) {
                ItemStack itemStack = emiStack.getItemStack();
                return itemStack.isEmpty() ? null : new NetworkKey(itemStack, 1L, null);
            }

            ItemStack wrapped;
            if (wrapForDisplayMethod != null) {
                wrapped = (ItemStack) wrapForDisplayMethod.invoke(aeKey);
            } else {
                wrapped = (ItemStack) wrapInItemStackMethod.invoke(null, aeKey, 0L);
            }
            if (wrapped == null || wrapped.isEmpty()) {
                return null;
            }

            long amountPerUnit = 1L;
            String unitSymbol = null;
            try {
                Object unit = amountPerUnitMethod.invoke(aeKey);
                if (unit instanceof Number number && number.longValue() > 0L) {
                    amountPerUnit = number.longValue();
                }
                Object keyType = keyTypeMethod.invoke(aeKey);
                if (keyType != null && unitSymbolMethod != null) {
                    Object symbol = unitSymbolMethod.invoke(keyType);
                    if (symbol instanceof String text && !text.isBlank()) {
                        unitSymbol = text;
                    }
                }
            } catch (Throwable ignored) {
                // Unit information is cosmetic only.
            }
            return new NetworkKey(wrapped, amountPerUnit, unitSymbol);
        } catch (Throwable error) {
            ModLogger.debug("NetworkKey: AE key conversion failed for {}: {}",
                    emiStack.getId(), error.toString());
            return null;
        }
    }

    /**
     * Build an AE {@code GenericStack} holding one unit of the given ingredient.
     * <p>
     * Used by actions that need the real AE key rather than an ItemStack, such as
     * ordering from the EMI sidebar: those must work for fluids and chemicals too,
     * not only for items. Returns null when no AE key can be derived or AE2 is not
     * available.
     */
    @javax.annotation.Nullable
    public static Object singleUnitGenericStack(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        if (ingredient instanceof EmiStack stack) {
            return singleUnitGenericStack(stack);
        }
        for (var stack : ingredient.getEmiStacks()) {
            Object genericStack = singleUnitGenericStack(stack);
            if (genericStack != null) {
                return genericStack;
            }
        }
        return null;
    }

    /** Single unit AE {@code GenericStack} for one EMI stack. */
    @javax.annotation.Nullable
    public static Object singleUnitGenericStack(EmiStack emiStack) {
        if (emiStack == null || emiStack.isEmpty() || !initReflection()) {
            return null;
        }
        try {
            Object genericStack = toGenericStackMethod.invoke(null, emiStack);
            if (genericStack == null) {
                return null;
            }
            Object aeKey = genericStackWhatMethod.invoke(genericStack);
            if (aeKey == null) {
                return null;
            }
            if (genericStackConstructor == null) {
                genericStackConstructor = Class.forName("appeng.api.stacks.GenericStack")
                        .getConstructor(Class.forName("appeng.api.stacks.AEKey"), long.class);
            }
            return genericStackConstructor.newInstance(aeKey, 1L);
        } catch (Throwable error) {
            ModLogger.debug("NetworkKey: GenericStack build failed for {}: {}",
                    emiStack.getId(), error.toString());
            return null;
        }
    }

    /**
     * Whether a negative result can be trusted to stay negative. Only true once
     * AE2 has actually registered at least one EMI stack converter, or when the
     * AE2 side of the conversion is unavailable altogether.
     */
    private static boolean negativeIsStable() {
        if (!reflectionAvailable || convertersMethod == null) {
            return true;
        }
        try {
            return convertersMethod.invoke(null) instanceof java.util.List<?> converters
                    && !converters.isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean initReflection() {
        if (reflectionChecked) {
            return reflectionAvailable;
        }
        reflectionChecked = true;
        try {
            Class<?> helperClass = Class.forName("appeng.integration.modules.emi.EmiStackHelper");
            toGenericStackMethod = helperClass.getMethod("toGenericStack", EmiStack.class);

            Class<?> genericStackClass = Class.forName("appeng.api.stacks.GenericStack");
            genericStackWhatMethod = genericStackClass.getMethod("what");

            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            try {
                wrapForDisplayMethod = aeKeyClass.getMethod("wrapForDisplayOrFilter");
            } catch (NoSuchMethodException missing) {
                wrapInItemStackMethod = genericStackClass.getMethod("wrapInItemStack", aeKeyClass, long.class);
            }

            amountPerUnitMethod = aeKeyClass.getMethod("getAmountPerUnit");
            keyTypeMethod = aeKeyClass.getMethod("getType");
            try {
                unitSymbolMethod = keyTypeMethod.getReturnType().getMethod("getUnitSymbol");
            } catch (NoSuchMethodException ignored) {
                unitSymbolMethod = null;
            }
            aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            try {
                convertersMethod = Class.forName("appeng.api.integrations.emi.EmiStackConverters")
                        .getMethod("getConverters");
            } catch (Throwable ignored) {
                convertersMethod = null;
            }
            reflectionAvailable = true;
        } catch (Throwable error) {
            reflectionAvailable = false;
            ModLogger.debug("NetworkKey: AE stack conversion unavailable: {}", error.toString());
        }
        return reflectionAvailable;
    }
}
