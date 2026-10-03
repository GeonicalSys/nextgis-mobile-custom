package com.nextgis.mobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.appcompat.widget.PopupMenu;

import com.nextgis.mobile.R;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Reflows map tools into columns from the right edge without shrinking their touch targets. */
public final class MapControlRail extends ViewGroup {
    private final int gap;
    private int cellWidth;
    private int cellHeight;
    private int rows = 1;
    private final ImageButton overflow;
    private final List<View> displayed = new ArrayList<>();
    private final List<View> hidden = new ArrayList<>();
    private final Map<View, Integer> accessibility = new IdentityHashMap<>();

    public MapControlRail(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(8 * getResources().getDisplayMetrics().density);
        overflow = new ImageButton(context);
        overflow.setImageResource(android.R.drawable.ic_menu_more);
        overflow.setContentDescription(context.getString(R.string.more));
        overflow.setTag("map_control_overflow");
        overflow.setVisibility(GONE);
        overflow.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(getContext(), overflow);
            for (View control : new ArrayList<>(hidden)) {
                menu.getMenu().add(control.getContentDescription())
                        .setEnabled(control.isEnabled())
                        .setOnMenuItemClickListener(item -> {
                            if (control.getParent() == this && control.getVisibility() == VISIBLE)
                                control.performClick();
                            return true;
                        });
            }
            menu.show();
        });
        addView(overflow);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        List<View> controls = new ArrayList<>();
        displayed.clear();
        hidden.clear();
        cellWidth = cellHeight = Math.round(48 * getResources().getDisplayMetrics().density);
        int natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == overflow || child.getVisibility() != VISIBLE) continue;
            child.measure(natural, natural);
            cellWidth = Math.max(cellWidth, child.getMeasuredWidth());
            cellHeight = Math.max(cellHeight, child.getMeasuredHeight());
            controls.add(child);
        }
        int count = controls.size();
        int paddingX = getPaddingLeft() + getPaddingRight();
        int paddingY = getPaddingTop() + getPaddingBottom();
        int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                ? count * cellHeight + Math.max(0, count - 1) * gap + paddingY
                : MeasureSpec.getSize(heightSpec);
        rows = MapControlGrid.rows(count, Math.max(0, height - paddingY), cellHeight, gap);
        int availableWidth = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : Math.max(0, MeasureSpec.getSize(widthSpec) - paddingX);
        int capacity = MapControlGrid.capacity(availableWidth, Math.max(0, height - paddingY),
                cellWidth, cellHeight, gap);
        boolean needsOverflow = count > capacity;
        int directCount = needsOverflow ? Math.max(0, capacity - 1) : count;
        for (int i = 0; i < count; i++) {
            View control = controls.get(i);
            if (i < directCount) {
                displayed.add(control);
                Integer original = accessibility.remove(control);
                if (original != null) control.setImportantForAccessibility(original);
            } else {
                hidden.add(control);
                if (!accessibility.containsKey(control))
                    accessibility.put(control, control.getImportantForAccessibility());
                control.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        overflow.setVisibility(needsOverflow ? VISIBLE : GONE);
        if (needsOverflow) displayed.add(overflow);
        for (View control : displayed) {
            control.measure(MeasureSpec.makeMeasureSpec(Math.min(cellWidth, availableWidth),
                            MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.min(cellHeight, Math.max(0, height - paddingY)),
                            MeasureSpec.EXACTLY));
        }
        int columns = displayed.isEmpty() ? 0 : (displayed.size() + rows - 1) / rows;
        int width = columns * cellWidth + Math.max(0, columns - 1) * gap
                + paddingX;
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int index = displayed.indexOf(child);
            if (index < 0) {
                child.layout(0, 0, 0, 0);
                continue;
            }
            int right = Math.max(0, getWidth() - getPaddingRight()
                    - (index / rows) * (cellWidth + gap));
            int top = Math.min(getPaddingTop() + (index % rows) * (cellHeight + gap), getHeight());
            child.layout(right - child.getMeasuredWidth(), top, right,
                    Math.min(top + child.getMeasuredHeight(), getHeight()));
        }
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}
