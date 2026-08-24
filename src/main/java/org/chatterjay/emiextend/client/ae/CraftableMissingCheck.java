package org.chatterjay.emiextend.client.ae;

public record CraftableMissingCheck(String ingredients, String missingSlots, String craftableSlots,
                                    boolean anyMissing, boolean anyCraftable) {
}
