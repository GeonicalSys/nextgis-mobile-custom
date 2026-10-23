package com.nextgis.mobile.reliability;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplibui.fragment.CompassFragment;
import com.nextgis.maplibui.util.CompassImage;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Technical native gesture regression; no location, accounts or working application data. */
@RunWith(AndroidJUnit4.class)
public class CompassTouchTest {
    static class NoVibratorCompass extends CompassFragment {
        NoVibratorCompass(Context context) {
            mVibrator = null; mIsVibrationOn = true;
            mCompass = new CompassImage(context);
            mCompass.layout(0, 0, 200, 200);
            mCompass.setOnTouchListener(this);
        }
        void drag() {
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 150, 100, 0);
            MotionEvent move = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, 100, 50, 0);
            try { assertTrue(mCompass.dispatchTouchEvent(down)); assertTrue(mCompass.dispatchTouchEvent(move)); }
            finally { down.recycle(); move.recycle(); }
            assertEquals(-90f, mCompass.getAngle(), .01f);
        }
    }
    @Test public void draggingCompassWithoutVibratorKeepsRotationAndDoesNotCrash() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VerificationEnvironment.requireIsolated(context);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> new NoVibratorCompass(context).drag());
    }
}
