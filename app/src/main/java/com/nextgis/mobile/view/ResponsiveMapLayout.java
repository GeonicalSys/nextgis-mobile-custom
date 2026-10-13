package com.nextgis.mobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import com.nextgis.mobile.R;

/** Update overlay space from the measured window, including rotations that retain the inflated views. */
public final class ResponsiveMapLayout extends RelativeLayout {
    public ResponsiveMapLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean compact = MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED && MeasureSpec.getSize(heightSpec) < dp(320);
        View compass = findViewById(R.id.fl_compass), hud = findViewById(R.id.gnss_hud);
        int compassSize = dp(compact ? 24 : 64);
        if (compass != null) { compass.getLayoutParams().width = compassSize; compass.getLayoutParams().height = compassSize; }
        if (hud != null) hud.getLayoutParams().height = compassSize;
        View panels = findViewById(R.id.map_panels_stack);
        if (panels != null) {
            LayoutParams params = (LayoutParams) panels.getLayoutParams();
            params.topMargin = params.bottomMargin = dp(compact ? 4 : 12);
        }
        View menu = findViewById(R.id.multiple_actions), point = findViewById(R.id.add_point_by_tap);
        boolean creation = (menu != null && menu.getVisibility() == VISIBLE) || (point != null && point.getVisibility() == VISIBLE);
        int creationHeight = creation ? dp(compact ? 56 : 80) : 0;
        View row = findViewById(R.id.map_action_row), host = findViewById(R.id.creation_actions_host);
        if (row != null) row.getLayoutParams().height = creationHeight;
        if (host != null) host.getLayoutParams().height = creationHeight;
        View rail = findViewById(R.id.map_control_rail);
        if (rail != null) rail.setPadding(dp(compact ? 4 : 8), dp(compact ? 4 : 8), dp(compact ? 4 : 8), dp(compact ? 4 : 8));
        super.onMeasure(widthSpec, heightSpec);
    }
}
