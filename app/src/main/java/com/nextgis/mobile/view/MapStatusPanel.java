package com.nextgis.mobile.view;

import android.content.Context;
import android.util.AttributeSet;
import com.nextgis.maplibui.view.FittedPanelLayout;

/** Reserve map space for navigation/actions before fitting an oversized telemetry block. */
public final class MapStatusPanel extends FittedPanelLayout {
    public MapStatusPanel(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean enlarged = getResources().getConfiguration().fontScale > 1.05f
                || getResources().getDisplayMetrics().densityDpi > android.util.DisplayMetrics.DENSITY_DEVICE_STABLE;
        int footer = enlarged ? Math.round(40 * getResources().getDisplayMetrics().density) : 0;
        if (getPaddingBottom() != footer) setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), footer);
        android.content.res.TypedArray bar = getContext().obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.actionBarSize});
        int toolbar = bar.getDimensionPixelSize(0, 0); bar.recycle();
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
            heightSpec = MeasureSpec.makeMeasureSpec(Math.max(1, Math.min(MeasureSpec.getSize(heightSpec),
                    Math.round(Math.max(0, MeasureSpec.getSize(heightSpec) - toolbar - footer) * .25f) + footer)), MeasureSpec.AT_MOST);
        super.onMeasure(widthSpec, heightSpec);
    }
}
