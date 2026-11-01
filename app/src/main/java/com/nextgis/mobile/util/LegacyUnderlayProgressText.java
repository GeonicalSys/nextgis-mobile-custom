package com.nextgis.mobile.util;

import android.content.Context;
import android.text.format.Formatter;
import com.nextgis.mobile.R;

/** Human-readable stages, without presenting received bytes as completed migration. */
public final class LegacyUnderlayProgressText {
    private LegacyUnderlayProgressText() { }

    public static String format(Context context, LegacyUnderlayImporter.Progress progress) {
        int label;
        switch (progress.phase) {
            case RECEIVING: label = R.string.legacy_underlay_phase_receiving; break;
            case FINALIZING: label = R.string.legacy_underlay_phase_finalizing; break;
            case ATTACHING: label = R.string.legacy_underlay_phase_attaching; break;
            case STALLED: label = R.string.legacy_underlay_phase_stalled; break;
            default: label = R.string.legacy_underlay_phase_opening;
        }
        String stage = progress.retrying && progress.phase == LegacyUnderlayImporter.Phase.OPENING
                ? context.getString(R.string.legacy_underlay_import_retrying,
                        progress.sourceIndex, progress.sourceCount) : context.getString(label);
        String bytes = Formatter.formatFileSize(context, progress.bytesRead);
        String counts = progress.tilesRead > 0
                ? context.getString(R.string.legacy_underlay_counts_tiles,
                        progress.sourceIndex, progress.sourceCount, bytes, progress.tilesRead)
                : context.getString(R.string.legacy_underlay_counts_bytes,
                        progress.sourceIndex, progress.sourceCount, bytes);
        return context.getString(R.string.legacy_underlay_phase_progress,
                stage, progress.elapsedMillis / 1000L, counts);
    }
}
