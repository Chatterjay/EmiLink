package org.chatterjay.emiextend.client.ae;

import net.minecraft.world.item.ItemStack;

public record BulkAutocraftRequest(ItemStack stack, int amount) {
}
