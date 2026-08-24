package org.chatterjay.emiextend.client;

/** Screen bounds that can receive an item name during EMI drag-fill. */
public record DragFillTarget(int x, int y, int width, int height) {
}
