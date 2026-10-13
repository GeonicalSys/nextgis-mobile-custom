package com.nextgis.mobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.drawerlayout.widget.DrawerLayout;
import java.util.IdentityHashMap;
import java.util.Map;

/** A fixed preferred drawer width must not extend beyond an enlarged-display viewport. */
public final class BoundedDrawerLayout extends DrawerLayout {
    private final Map<View, Float> preferredWidths = new IdentityHashMap<>();
    public BoundedDrawerLayout(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        float density = getResources().getDisplayMetrics().density;
        int available = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight() - Math.round(8 * density);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            if (params.gravity == 0 || params.width <= 0) continue;
            if (!preferredWidths.containsKey(child)) preferredWidths.put(child, params.width / density);
            params.width = Math.max(0, Math.min(Math.round(preferredWidths.get(child) * density),
                    available - params.leftMargin - params.rightMargin));
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
