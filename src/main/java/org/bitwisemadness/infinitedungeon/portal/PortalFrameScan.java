package org.bitwisemadness.infinitedungeon.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The portal frame scanner, as pure logic.
 *
 * <p>Reimplements vanilla's nether-portal geometry rules parameterised by a
 * frame predicate, because vanilla's own scanner is hard-bound to obsidian.
 * Vanilla's bounds and rules are kept exactly: interior at least 2 wide and 3
 * tall, at most 21 by 21, corners optional, every interior cell empty, and the
 * whole rectangle framed on all four edges.</p>
 *
 * <p>Coordinates are two-dimensional on the portal's plane - the along-axis
 * coordinate (world X for an X-axis portal, world Z for a Z-axis portal) and
 * the height - so the scan is free of Minecraft types and unit-testable. The
 * game-facing igniter maps world positions onto this plane.</p>
 */
public final class PortalFrameScan {

    /** Vanilla's bounds of a nether portal. */
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    /** One cell of the portal plane, as the scanner sees it. */
    public enum Cell {
        /** Part of the frame. */
        FRAME,
        /** Part of the interior: air, or the fire that was just placed. */
        EMPTY,
        /** Anything else, which breaks the portal shape. */
        OTHER
    }

    /** Tests cells of one portal plane by (along-axis, height) coordinates. */
    @FunctionalInterface
    public interface CellTest {

        Cell test(int along, int y);
    }

    /**
     * A validated layout: the interior rectangle in plane coordinates.
     *
     * @param minAlong the lowest along-axis coordinate of the interior
     * @param minY     the lowest height of the interior
     * @param width    the interior width in cells
     * @param height   the interior height in cells
     */
    public record Layout(int minAlong, int minY, int width, int height) {

        /** Every interior cell as {@code [along, y]} pairs, row-major. */
        public List<int[]> interiorCells() {
            List<int[]> cells = new ArrayList<>(width * height);
            for (int y = minY; y < minY + height; y++) {
                for (int along = minAlong; along < minAlong + width; along++) {
                    cells.add(new int[] {along, y});
                }
            }
            return List.copyOf(cells);
        }
    }

    private PortalFrameScan() {
    }

    /**
     * Scans for a valid rectangular portal frame around the start cell, which
     * must be an interior cell (or sit above one). Returns the layout when a
     * frame encloses it.
     */
    public static Optional<Layout> scan(int startAlong, int startY, CellTest test) {
        // Walk down to the lowest interior cell; the block below must be frame.
        int bottom = startY;
        int guard = 0;
        while (guard++ <= MAX_HEIGHT && test.test(startAlong, bottom - 1) == Cell.EMPTY) {
            bottom--;
        }
        if (test.test(startAlong, bottom - 1) != Cell.FRAME) {
            return Optional.empty();
        }

        // Walk the width along the bottom interior row; both ends must be frame.
        int left = startAlong;
        int right = startAlong;
        int width = 1;
        while (width <= MAX_WIDTH && test.test(left - 1, bottom) == Cell.EMPTY) {
            left--;
            width++;
        }
        if (width > MAX_WIDTH || test.test(left - 1, bottom) != Cell.FRAME) {
            return Optional.empty();
        }
        while (width <= MAX_WIDTH && test.test(right + 1, bottom) == Cell.EMPTY) {
            right++;
            width++;
        }
        if (width > MAX_WIDTH || test.test(right + 1, bottom) != Cell.FRAME) {
            return Optional.empty();
        }
        if (width < MIN_WIDTH) {
            return Optional.empty();
        }

        // Walk the height up the leftmost interior column; the top must be frame.
        int top = bottom;
        int rows = 1;
        while (rows <= MAX_HEIGHT && test.test(left, top + 1) == Cell.EMPTY) {
            top++;
            rows++;
        }
        if (rows > MAX_HEIGHT || test.test(left, top + 1) != Cell.FRAME) {
            return Optional.empty();
        }
        if (rows < MIN_HEIGHT) {
            return Optional.empty();
        }

        // The whole rectangle must hold: interior empty, all four edges framed
        // (corners optional), and no ragged columns.
        for (int along = left; along <= left + width - 1; along++) {
            for (int y = bottom; y <= top; y++) {
                if (test.test(along, y) != Cell.EMPTY) {
                    return Optional.empty();
                }
            }
            if (test.test(along, bottom - 1) != Cell.FRAME
                    || test.test(along, top + 1) != Cell.FRAME) {
                return Optional.empty();
            }
        }
        for (int y = bottom; y <= top; y++) {
            if (test.test(left - 1, y) != Cell.FRAME
                    || test.test(right + 1, y) != Cell.FRAME) {
                return Optional.empty();
            }
        }
        return Optional.of(new Layout(left, bottom, width, rows));
    }
}
