package com.nextgis.mobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Reflows map tools into columns from the right edge without shrinking their touch targets. */
public final class MapControlRail extends ViewGroup {
    private final int gap;
    private int cellWidth;
    private int cellHeight;
    private int rows = 1;

    public MapControlRail(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(8 * getResources().getDisplayMetrics().density);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int count = 0;
        cellWidth = cellHeight = Math.round(48 * getResources().getDisplayMetrics().density);
        int natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(natural, natural);
            cellWidth = Math.max(cellWidth, child.getMeasuredWidth());
            cellHeight = Math.max(cellHeight, child.getMeasuredHeight());
            count++;
        }
        int paddingY = getPaddingTop() + getPaddingBottom();
        int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? count * (cellHeight + gap) + paddingY : MeasureSpec.getSize(heightSpec);
        rows = MapControlGrid.rows(count, Math.max(0, height - paddingY), cellHeight, gap);
        int columns = count == 0 ? 0 : (count + rows - 1) / rows;
        int width = columns * cellWidth + Math.max(0, columns - 1) * gap
                + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int right = getWidth() - getPaddingRight() - (index / rows) * (cellWidth + gap);
            int top = getPaddingTop() + (index % rows) * (cellHeight + gap);
            child.layout(right - child.getMeasuredWidth(), top, right,
                    top + child.getMeasuredHeight());
            index++;
        }
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}
