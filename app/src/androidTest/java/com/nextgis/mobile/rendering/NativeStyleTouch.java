package com.nextgis.mobile.rendering;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.Assert.*;

/** Targeted input only after rotation, layout, scrolling and the real window focus have settled. */
final class NativeStyleTouch {
    private NativeStyleTouch() { }
    static void tap(VectorStyleRenderingTest.Fixture fixture,Supplier<View> target) { tap(fixture,target,false); }
    static void minimum(VectorStyleRenderingTest.Fixture fixture,Supplier<View> target) { tap(fixture,target,true); }
    private static void tap(VectorStyleRenderingTest.Fixture fixture,Supplier<View> target,boolean minimum) {
        AtomicReference<Rect> current=new AtomicReference<>();AtomicReference<String> state=new AtomicReference<>("No target");
        AtomicReference<Integer> minimumX=new AtomicReference<>();
        Rect last=null;long stableSince=0,deadline=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<deadline) {
            fixture.screen.onActivity(activity->{
                View view=target.get();current.set(null);
                if(view==null)return;
                Rect visible=new Rect(),usable=new Rect();view.getWindowVisibleDisplayFrame(usable);
                int[] origin=new int[2];view.getLocationOnScreen(origin);Point display=new Point();view.getDisplay().getRealSize(display);
                Rect physical=new Rect(origin[0],origin[1],origin[0]+view.getWidth(),origin[1]+view.getHeight());
                boolean shown=view.getLocalVisibleRect(visible);
                // Local coordinates avoid treating the dialog's root as the physical screen origin.
                // A tall scrolled control can be tapped in its visible part, as a user would tap it.
                visible.offset(origin[0],origin[1]);
                boolean clipped=shown&&visible.intersect(physical)&&visible.intersect(usable)
                        &&visible.intersect(new Rect(0,0,display.x,display.y));
                state.set("id="+view.getId()+" focus="+view.hasWindowFocus()+" layout="+view.isLayoutRequested()
                        +" physical="+physical+" visible="+visible+" usable="+usable+" display="+display);
                if(!view.isShown()||!view.isEnabled()||!view.hasWindowFocus()||view.isLayoutRequested()||!clipped)return;
                if(minimum) {
                    SeekBar bar=(SeekBar)view;
                    assertNotNull(bar.getThumb());assertNotNull(bar.getProgressDrawable());
                    int x=physical.left+bar.getPaddingLeft()+1;
                    if(!visible.contains(x,(int)visible.exactCenterY()))return;
                    minimumX.set(x);
                }
                current.set(visible);
            });
            Rect next=current.get();long now=SystemClock.uptimeMillis();
            if(next!=null&&next.equals(last)) { if(now-stableSince>=120)break; }
            else {last=next;stableSince=now;}
            SystemClock.sleep(30);
        }
        Rect bounds=current.get();assertNotNull("Target window must settle before a physical touch: "+state.get(),bounds);
        assertTrue("Target bounds must remain stable",SystemClock.uptimeMillis()-stableSince>=120);
        float x=bounds.exactCenterX(),y=bounds.exactCenterY();
        if(minimum)x=minimumX.get();
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        }finally{down.recycle();up.recycle();}
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
