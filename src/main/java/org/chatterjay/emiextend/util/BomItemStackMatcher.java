package org.chatterjay.emiextend.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Item matching used by the BOM bookkeeping layer.
 *
 * AE2 keeps each durability value as a separate item key, while a normal
 * crafting ingredient accepts any valid durability value. Keep all other
 * components exact and ignore only the vanilla damage component for
 * damageable items.
 */
public final class BomItemStackMatcher {

    private BomItemStackMatcher() {
    }

    public static boolean matches(ItemStack actual, ItemStack template) {
        if (actual == null || template == null || actual.isEmpty() || template.isEmpty()) {
            return false;
        }
        if (ItemStack.isSameItemSameComponents(actual, template)) {
            return true;
        }
        if (actual.getItem() != template.getItem()
                || (!actual.isDamageableItem() && !template.isDamageableItem())) {
            return false;
        }

        ItemStack actualWithoutDamage = actual.copy();
        ItemStack templateWithoutDamage = template.copy();
        actualWithoutDamage.remove(DataComponents.DAMAGE);
        templateWithoutDamage.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(actualWithoutDamage, templateWithoutDamage);
    }
}
