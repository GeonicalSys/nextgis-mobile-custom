package com.nextgis.mobile.util;

import android.content.Context;
import android.os.Vibrator;
import android.view.MotionEvent;
import com.nextgis.maplibui.fragment.CompassFragment;
import com.nextgis.maplibui.util.CompassImage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36}, manifest = Config.NONE, application = android.app.Application.class)
public class CompassTouchPolicyTest {
    static class Compass extends CompassFragment {
        Compass(Context context, Vibrator vibrator, boolean enabled) {
            mVibrator = vibrator; mIsVibrationOn = enabled;
            mCompass = new CompassImage(context); mCompass.layout(0, 0, 200, 200);
        }
        void drag() {
            MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 150, 100, 0);
            MotionEvent move = MotionEvent.obtain(0, 20, MotionEvent.ACTION_MOVE, 100, 50, 0);
            try { assertTrue(onTouch(mCompass, down)); assertTrue(onTouch(mCompass, move)); }
            finally { down.recycle(); move.recycle(); }
            assertEquals(-90f, mCompass.getAngle(), .01f);
        }
    }
    @Test public void missingServiceNeverBlocksRotation() {
        new Compass(RuntimeEnvironment.getApplication(), null, true).drag();
    }
    @Test public void optionalHardwareAndUserPreferenceAreRespected() {
        Context app = RuntimeEnvironment.getApplication();
        Vibrator vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        Shadows.shadowOf(vibrator).setHasVibrator(false);
        new Compass(app, vibrator, true).drag(); assertFalse(Shadows.shadowOf(vibrator).isVibrating());
        Shadows.shadowOf(vibrator).setHasVibrator(true);
        new Compass(app, vibrator, false).drag(); assertFalse(Shadows.shadowOf(vibrator).isVibrating());
        new Compass(app, vibrator, true).drag(); assertTrue(Shadows.shadowOf(vibrator).isVibrating());
    }
}
