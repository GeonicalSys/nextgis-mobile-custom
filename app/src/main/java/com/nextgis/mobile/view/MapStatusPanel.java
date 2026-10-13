package com.nextgis.mobile.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import com.nextgis.mobile.R;
import com.nextgis.maplibui.view.FittedPanelLayout;

/** Reserve map space for navigation/actions before fitting an oversized telemetry block. */
public final class MapStatusPanel extends FittedPanelLayout {
    public MapStatusPanel(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        android.content.res.TypedArray bar = getContext().obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.actionBarSize});
        int toolbar = bar.getDimensionPixelSize(0, 0); bar.recycle();
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED)
            heightSpec = MeasureSpec.makeMeasureSpec(Math.max(1, Math.min(MeasureSpec.getSize(heightSpec),
                    Math.round(Math.max(0, MeasureSpec.getSize(heightSpec) - toolbar) * .25f))), MeasureSpec.AT_MOST);
        super.onMeasure(widthSpec, heightSpec);
        // Keep the ordinary two-row arrangement. When the coordinates need more
        // space, group the short zoom/GPS/accuracy values instead of isolating zoom.
        ViewGroup coordinates = findViewById(R.id.status_coordinates);
        ViewGroup metrics = findViewById(R.id.status_metrics);
        View zoom = findViewById(R.id.tv_zoom);
        if (coordinates != null && metrics != null && zoom != null) {
            int used = itemWidth(findViewById(R.id.tv_latitude)) + itemWidth(findViewById(R.id.tv_longitude))
                    + itemWidth(zoom) + Math.round(8 * getResources().getDisplayMetrics().density);
            int available = coordinates.getMeasuredWidth() - coordinates.getPaddingLeft() - coordinates.getPaddingRight();
            ViewGroup destination = used <= available ? coordinates : metrics;
            if (zoom.getParent() != destination) {
                ViewGroup.LayoutParams params = zoom.getLayoutParams();
                ((ViewGroup) zoom.getParent()).removeView(zoom);
                destination.addView(zoom, destination == coordinates ? destination.getChildCount() : 0, params);
                super.onMeasure(widthSpec, heightSpec);
            }
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        View latitude = findViewById(R.id.tv_latitude), longitude = findViewById(R.id.tv_longitude);
        View zoom = findViewById(R.id.tv_zoom);
        boolean zoomWithCoordinates = zoom.getParent() == latitude.getParent() && zoom.getVisibility() == VISIBLE;
        if (sameRow(latitude, longitude) && (!zoomWithCoordinates || sameRow(latitude, zoom))) {
            ViewGroup row = (ViewGroup) latitude.getParent();
            int start = row.getPaddingLeft(), width = row.getWidth() - start - row.getPaddingRight();
            int zoomSpace = zoomWithCoordinates ? zoom.getWidth()
                    + Math.round(16 * getResources().getDisplayMetrics().density) : 0;
            int cell = Math.max(0, (width - zoomSpace) / 2);
            if (latitude.getWidth() <= cell && longitude.getWidth() <= cell) {
                centerInCell(latitude, start, cell);
                centerInCell(longitude, start + cell, cell);
                if (zoomWithCoordinates) centerInCell(zoom, start + 2 * cell, zoomSpace);
            }
        }
        View source = findViewById(R.id.tv_source), accuracy = findViewById(R.id.tv_accuracy);
        if (sameRow(source, accuracy) && zoom.getParent() != source.getParent()) {
            ViewGroup row = (ViewGroup) source.getParent();
            int cell = (row.getWidth() - row.getPaddingLeft() - row.getPaddingRight()) / 2;
            if (source.getWidth() <= cell && accuracy.getWidth() <= cell) {
                centerInCell(source, row.getPaddingLeft(), cell);
                centerInCell(accuracy, row.getPaddingLeft() + cell, cell);
            }
        }
    }

    private static int itemWidth(View view) {
        if (view == null || view.getVisibility() == GONE) return 0;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        return view.getMeasuredWidth() + params.leftMargin + params.rightMargin;
    }

    private static boolean sameRow(View first, View second) {
        return first != null && second != null && first.getVisibility() == VISIBLE
                && second.getVisibility() == VISIBLE && first.getParent() == second.getParent()
                && Math.abs((first.getTop() + first.getBottom()) - (second.getTop() + second.getBottom())) <= 2;
    }

    private static void centerInCell(View view, int start, int width) {
        int left = start + (width - view.getWidth()) / 2;
        view.layout(left, view.getTop(), left + view.getWidth(), view.getBottom());
    }
}
