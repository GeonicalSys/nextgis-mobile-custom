package com.nextgis.mobile.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.mobile.R;
import com.nextgis.mobile.util.LegacyUnderlayMigrationContract;

import java.util.List;

/** Explicit, user-confirmed export gate hosted only by the installed debug package. */
public class LegacyUnderlayExportActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String caller = getCallingPackage();
        if (!LegacyUnderlayMigrationContract.isDebugSource(this)
                || !LegacyUnderlayMigrationContract.isTrustedGeonicalCaller(this, caller)
                || !LegacyUnderlayMigrationContract.ACTION_EXPORT.equals(getIntent().getAction())) {
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        List<LocalTMSLayer> layers =
                LegacyUnderlayMigrationContract.collectLocalUnderlays(this);
        if (layers.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.legacy_underlay_export_title)
                    .setMessage(R.string.legacy_underlay_export_empty)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finishCanceled())
                    .setOnCancelListener(dialog -> finishCanceled())
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.legacy_underlay_export_title)
                .setMessage(getString(R.string.legacy_underlay_export_confirmation, layers.size()))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finishCanceled())
                .setPositiveButton(R.string.legacy_underlay_export_confirm,
                        (dialog, which) -> returnGrantedLayers(layers, caller))
                .setOnCancelListener(dialog -> finishCanceled())
                .show();
    }

    private void returnGrantedLayers(List<LocalTMSLayer> layers, String caller) {
        Intent result = new Intent();
        ClipData clipData = null;
        for (LocalTMSLayer layer : layers) {
            Uri uri = LegacyUnderlayMigrationContract.buildLayerUri(this, layer);
            grantUriPermission(caller, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (clipData == null) {
                clipData = new ClipData(
                        getString(R.string.legacy_underlay_export_clip_label),
                        new String[]{LegacyUnderlayMigrationContract.MIME_TYPE},
                        new ClipData.Item(uri));
            } else {
                clipData.addItem(new ClipData.Item(uri));
            }
        }
        result.setClipData(clipData);
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void finishCanceled() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
