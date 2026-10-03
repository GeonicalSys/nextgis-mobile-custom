package com.nextgis.mobile.view;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapControlGridTest {
    @Test public void narrowWindowReservesOneSlotForOverflow() {
        assertEquals(3, MapControlGrid.capacity(56, 184, 56, 56, 8));
        assertEquals(2, MapControlGrid.capacity(120, 80, 56, 56, 8));
        assertEquals(0, MapControlGrid.capacity(55, 80, 56, 56, 8));
        assertEquals(0, MapControlGrid.capacity(120, 55, 56, 56, 8));
    }
    @Test public void portraitKeepsAllToolsInOneColumn() {
        assertEquals(5, MapControlGrid.rows(5, 600, 56, 8));
    }

    @Test public void landscapeAndSplitWindowUseAdjacentColumns() {
        assertEquals(3, MapControlGrid.rows(5, 184, 56, 8));
        assertEquals(2, MapControlGrid.rows(5, 183, 56, 8));
        assertEquals(1, MapControlGrid.rows(5, 80, 56, 8));
    }

    @Test public void everyToolFitsAtDifferentDensitiesWithoutOverlap() {
        for (int scale = 1; scale <= 4; scale++) {
            for (int count = 1; count <= 5; count++) {
                for (int height = 56; height < 700; height++) {
                    int rows = MapControlGrid.rows(count, height * scale, 56 * scale, 8 * scale);
                    for (int i = 0; i < count; i++) {
                        int top = (i % rows) * 64 * scale;
                        assertTrue(top + 56 * scale <= height * scale);
                        for (int j = 0; j < i; j++) {
                            assertTrue(i / rows != j / rows || i % rows != j % rows);
                        }
                    }
                }
            }
        }
    }
}
