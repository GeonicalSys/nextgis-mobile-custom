package com.nextgis.mobile.util;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

/** Bounded message scrolling with complete fixed titles and actions. */
public final class ScrollableAlertDialogs {
    private ScrollableAlertDialogs() { }

    /** Keep AlertController's bounded message scroll and fixed button panel, without scaling text. */
    public static AlertDialog prepare(AlertDialog dialog) {
        dialog.create();
        android.widget.TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        wrap(title);
        wrap(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        wrap(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
        wrap(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
        android.view.ViewGroup content = dialog.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() == 1
                && content.getChildAt(0) instanceof android.view.ViewGroup) {
            android.view.ViewGroup original = (android.view.ViewGroup) content.getChildAt(0);
            android.view.ViewGroup.LayoutParams outer = original.getLayoutParams();
            MessagePanels panels = new MessagePanels(dialog.getContext());
            panels.setId(original.getId());
            panels.setPadding(original.getPaddingLeft(), original.getPaddingTop(),
                    original.getPaddingRight(), original.getPaddingBottom());
            // AlertDialogLayout reserves only the minimum height of a stacked ButtonBar.
            // Reserve its whole measured height first; only the message may scroll/shrink.
            while (original.getChildCount() > 0) {
                android.view.View panel = original.getChildAt(0);
                android.view.ViewGroup.MarginLayoutParams params = new android.view.ViewGroup.MarginLayoutParams(
                        panel.getLayoutParams());
                original.removeView(panel);
                panels.addView(panel, params);
            }
            content.removeView(original);
            content.addView(panels, outer);
        }
        return dialog;
    }

    private static final class MessagePanels extends android.view.ViewGroup {
        MessagePanels(Context context) { super(context); }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int padding = getPaddingTop() + getPaddingBottom();
            int fixed = padding, widest = 0;
            android.view.View message = null;
            for (int i = 0; i < getChildCount(); i++) {
                android.view.View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                if (child.getId() == androidx.appcompat.R.id.contentPanel) { message = child; continue; }
                measurePanel(child, widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                fixed += heightWithMargins(child);
                widest = Math.max(widest, widthWithMargins(child));
            }
            int total = fixed;
            if (message != null) {
                MarginLayoutParams params = (MarginLayoutParams) message.getLayoutParams();
                int space = Math.max(0, MeasureSpec.getSize(heightSpec) - fixed - params.topMargin - params.bottomMargin);
                int messageSpec = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                        ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                        : MeasureSpec.makeMeasureSpec(space, MeasureSpec.AT_MOST);
                measurePanel(message, widthSpec, messageSpec);
                total += heightWithMargins(message);
                widest = Math.max(widest, widthWithMargins(message));
            }
            setMeasuredDimension(resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec),
                    resolveSize(total, heightSpec));
        }

        private void measurePanel(android.view.View child, int widthSpec, int heightSpec) {
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            child.measure(getChildMeasureSpec(widthSpec, getPaddingLeft() + getPaddingRight()
                    + params.leftMargin + params.rightMargin, params.width), heightSpec);
        }

        private int heightWithMargins(android.view.View child) {
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            return child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
        }

        private int widthWithMargins(android.view.View child) {
            MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
            return child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
        }

        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int y = getPaddingTop();
            for (int i = 0; i < getChildCount(); i++) {
                android.view.View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                MarginLayoutParams params = (MarginLayoutParams) child.getLayoutParams();
                y += params.topMargin;
                int x = getPaddingLeft() + params.leftMargin;
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
                y += child.getMeasuredHeight() + params.bottomMargin;
            }
        }
    }

    private static void wrap(android.widget.TextView text) {
        if (text == null) return;
        text.setSingleLine(false);
        text.setMaxLines(Integer.MAX_VALUE);
        text.setEllipsize(null);
        android.view.ViewGroup.LayoutParams params = text.getLayoutParams();
        if (params != null) params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
    }

}
