package com.nextgis.mobile.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** A single scrolling list for vector objects and tracks; labels keep their full text. */
public final class MapSelectionDialog {
    private MapSelectionDialog() { }

    public static AlertDialog create(Context context, int title, List<String> labels, IntConsumer select) {
        AlertDialog.Builder builder = com.nextgis.maplibui.util.FittedDialogs.compatBuilder(context).setTitle(title);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(builder.getContext(),
                android.R.layout.simple_list_item_1, new ArrayList<>(labels)) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, recycled, parent);
                row.setSingleLine(false);
                row.setMaxLines(Integer.MAX_VALUE);
                row.setEllipsize(null);
                if (row.getLayoutParams() != null) row.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                return row;
            }
        };
        return builder.setAdapter(adapter, (dialog, index) -> select.accept(index))
                .setNegativeButton(com.nextgis.maplibui.R.string.cancel, null).create();
    }
}
