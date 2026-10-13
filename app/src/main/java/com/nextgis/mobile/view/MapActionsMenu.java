package com.nextgis.mobile.view;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.nextgis.mobile.R;

/** Collapsed, transparent action slots must pass gestures to the map underneath. */
public final class MapActionsMenu extends FloatingActionsMenu {
    public MapActionsMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !isExpanded()) {
            // The vendored menu uses AnimatorProxy: hidden buttons retain View alpha=1
            // and their expanded hit rectangles. Native alpha/matrix cannot identify them.
            View plus = findViewById(R.id.fab_expand_menu_button);
            if (plus == null || plus.getVisibility() != VISIBLE) return false;
            RectF target = new RectF(0, 0, plus.getWidth(), plus.getHeight());
            plus.getMatrix().mapRect(target);
            target.offset(plus.getLeft() - getScrollX(), plus.getTop() - getScrollY());
            if (!target.contains(event.getX(), event.getY())) return false;
        }
        return super.dispatchTouchEvent(event);
    }
}
