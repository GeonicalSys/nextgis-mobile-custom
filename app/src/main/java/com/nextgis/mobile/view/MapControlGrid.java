package com.nextgis.mobile.view;

/** Geometry shared by the rail and its viewport-boundary regression tests. */
final class MapControlGrid {
    private MapControlGrid() { }

    static int rows(int count, int height, int cellHeight, int gap) {
        return Math.max(1, Math.min(count, (height + gap) / (cellHeight + gap)));
    }
}
