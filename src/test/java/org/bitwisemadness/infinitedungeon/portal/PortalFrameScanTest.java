package org.bitwisemadness.infinitedungeon.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The frame scanner against vanilla's rules: interior bounds, optional
 * corners, and every way a shape can be invalid. Grids are written top-down
 * like a picture, with y growing upward as the scanner expects.
 */
class PortalFrameScanTest {

    private record Grid(List<String> rows) {

        PortalFrameScan.Cell cell(int along, int y) {
            int index = rows.size() - 1 - y;
            if (index < 0 || index >= rows.size() || along < 0) {
                return PortalFrameScan.Cell.OTHER;
            }
            String row = rows.get(index);
            if (along >= row.length()) {
                return PortalFrameScan.Cell.OTHER;
            }
            return switch (row.charAt(along)) {
                case '#' -> PortalFrameScan.Cell.FRAME;
                case '.' -> PortalFrameScan.Cell.EMPTY;
                default -> PortalFrameScan.Cell.OTHER;
            };
        }
    }

    private static Optional<PortalFrameScan.Layout> scan(Grid grid, int along, int y) {
        return PortalFrameScan.scan(along, y, grid::cell);
    }

    @Test
    void findsTheInteriorOfAValidFrame() {
        List<String> frame = List.of("####", "#..#", "#..#", "#..#", "####");
        assertEquals(Optional.of(new PortalFrameScan.Layout(1, 1, 2, 3)),
                scan(new Grid(frame), 1, 2));
        assertEquals(6, scan(new Grid(frame), 1, 2).orElseThrow().interiorCells().size());
    }

    @Test
    void scansFromAnyInteriorCell() {
        List<String> frame = List.of("####", "#..#", "#..#", "#..#", "####");
        for (int along = 1; along <= 2; along++) {
            for (int y = 1; y <= 3; y++) {
                assertEquals(Optional.of(new PortalFrameScan.Layout(1, 1, 2, 3)),
                        scan(new Grid(frame), along, y), "from " + along + "," + y);
            }
        }
    }

    @Test
    void tooSmallFramesAreRejected() {
        // 3 wide, 3 tall: interior 1x1, below the minimum.
        List<String> tiny = List.of("###", "#.#", "###");
        assertTrue(scan(new Grid(tiny), 1, 1).isEmpty());
    }

    @Test
    void shortColumnsAreRejected() {
        // The right column stops one short, so no rectangle fits.
        List<String> shortColumn = List.of("##..", "#..#", "#..#", "#..#", "####");
        assertTrue(scan(new Grid(shortColumn), 1, 2).isEmpty());
    }

    @Test
    void holesInTheFrameAreRejected() {
        // A missing side block at a middle row.
        List<String> hole = List.of("####", "#..#", "....", "#..#", "####");
        assertTrue(scan(new Grid(hole), 1, 2).isEmpty());
    }

    @Test
    void oversizedFramesAreRejected() {
        // Interior wider than vanilla's maximum.
        int interiorWidth = PortalFrameScan.MAX_WIDTH + 1;
        String edge = "#".repeat(interiorWidth + 2);
        String middle = "#" + ".".repeat(interiorWidth) + "#";
        List<String> big = List.of(edge, middle, middle, middle, edge);
        assertTrue(scan(new Grid(big), 1, 2).isEmpty());
    }
}
