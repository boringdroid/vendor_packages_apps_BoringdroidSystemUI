package com.android.launcher3.util;

import android.graphics.Rect;
/**
 * Utility object to manage the occupancy in a grid.
 */
public class GridOccupancy {

    private final int mCountX;
    private final int mCountY;

    public final boolean[][] cells;

    public GridOccupancy(int countX, int countY) {
        mCountX = countX;
        mCountY = countY;
        cells = new boolean[countX][countY];
    }

    public void markCells(int cellX, int cellY, int spanX, int spanY, boolean value) {
        if (cellX < 0 || cellY < 0) return;
        for (int x = cellX; x < cellX + spanX && x < mCountX; x++) {
            for (int y = cellY; y < cellY + spanY && y < mCountY; y++) {
                cells[x][y] = value;
            }
        }
    }

    public void markCells(Rect r, boolean value) {
        markCells(r.left, r.top, r.width(), r.height(), value);
    }

    public void markCells(CellAndSpan cell, boolean value) {
        markCells(cell.cellX, cell.cellY, cell.spanX, cell.spanY, value);
    }

    public void clear() {
        markCells(0, 0, mCountX, mCountY, false);
    }
}
