package com.nextgis.mobile.util;

import android.content.Context;

import com.nextgis.maplibui.service.WalkEditService;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.WalkSessionStore;

/** User-confirmed removal of only walk-owned transient state. */
public final class WalkEmergencyReset {
    public static final String KEY_PREFERENCE = "reset_stuck_walk";

    private WalkEmergencyReset() { }

    public static boolean hasState(Context context) {
        return WalkEditService.hasAnyWalkState(context);
    }

    public static boolean reset(Context context) {
        if (context == null) return false;
        WalkSessionStore.Snapshot session = WalkSessionStore.load(context);
        FeatureFormDraftStore.Snapshot form = FeatureFormDraftStore.load(context);
        if (form != null && (form.walkSessionId != null
                || session != null && session.pointId.equals(form.pointSessionId))) {
            FeatureFormDraftStore.clear(context);
        }
        return WalkEditService.emergencyStopAndClearDraft(context);
    }
}
