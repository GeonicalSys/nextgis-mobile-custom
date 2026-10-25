package com.nextgis.mobile.rendering;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
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
    static void tap(VectorStyleRenderingTest.Fixture fixture,Supplier<View> target) { tap(fixture,activity -> target.get(),false); }
    static void tap(VectorStyleRenderingTest.Fixture fixture,int id) { tap(fixture,activity -> activity.findViewById(id),false); }
    static void minimum(VectorStyleRenderingTest.Fixture fixture,Supplier<View> target) { tap(fixture,activity -> target.get(),true); }
    private static void tap(VectorStyleRenderingTest.Fixture fixture,
                            java.util.function.Function<com.nextgis.mobile.activity.MainActivity,View> target,boolean minimum) {
        long deadline=SystemClock.uptimeMillis()+15000;
        while(true) {
        AtomicReference<Rect> current=new AtomicReference<>();AtomicReference<String> state=new AtomicReference<>("No target");
        AtomicReference<float[]> minimumPoint=new AtomicReference<>();
        Rect last=null;long stableSince=0;
        while(SystemClock.uptimeMillis()<deadline) {
            fixture.screen.onActivity(activity->{
                View view=target.apply(activity);current.set(null);
                if(view==null)return;
                Rect visible=new Rect(),usable=new Rect();view.getWindowVisibleDisplayFrame(usable);
                Point display=new Point();view.getDisplay().getRealSize(display);
                Rect physical=new Rect();screen(view,new RectF(0,0,view.getWidth(),view.getHeight())).roundOut(physical);
                boolean shown=view.getLocalVisibleRect(visible);
                Rect localVisible=new Rect(visible);
                // Include FittedPanelLayout's parent scale, scrolling and the real window origin.
                screen(view,new RectF(visible)).roundOut(visible);
                boolean clipped=shown&&visible.intersect(physical)&&visible.intersect(usable)
                        &&visible.intersect(new Rect(0,0,display.x,display.y));
                state.set("id="+view.getId()+" focus="+view.hasWindowFocus()+" layout="+view.isLayoutRequested()
                        +" physical="+physical+" visible="+visible+" usable="+usable+" display="+display);
                if(!view.isShown()||!view.isEnabled()||!view.hasWindowFocus()||view.isLayoutRequested()||!clipped)return;
                if(minimum) {
                    SeekBar bar=(SeekBar)view;
                    assertNotNull(bar.getThumb());assertNotNull(bar.getProgressDrawable());
                    float[] point={bar.getPaddingLeft()+1,localVisible.exactCenterY()};screen(view,point);
                    if(!visible.contains((int)point[0],(int)point[1]))return;
                    minimumPoint.set(point);
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
        if(minimum){x=minimumPoint.get()[0];y=minimumPoint.get()[1];}
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);
        boolean downAccepted=false;
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            downAccepted=true;
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        }catch(IllegalArgumentException error){
            String message=String.valueOf(error.getMessage());
            if(downAccepted || !message.contains("Targeted input event injection") || !message.contains("owned by uid"))throw error;
            // A rejected DOWN reached no window. Wait/re-measure and use the same UID-guarded API.
            if(SystemClock.uptimeMillis()>=deadline)throw new AssertionError("Owned window remained occluded: "+state.get(),error);
            SystemClock.sleep(100);continue;
        }finally{down.recycle();up.recycle();}
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return;
        }
    }
    private static RectF screen(View view,RectF rectangle){
        View current=view;
        while(current.getParent() instanceof View){
            View parent=(View)current.getParent();current.getMatrix().mapRect(rectangle);
            rectangle.offset(current.getLeft()-parent.getScrollX(),current.getTop()-parent.getScrollY());current=parent;
        }
        int[] origin=new int[2];current.getLocationOnScreen(origin);rectangle.offset(origin[0],origin[1]);return rectangle;
    }
    private static void screen(View view,float[] point){
        View current=view;
        while(current.getParent() instanceof View){
            View parent=(View)current.getParent();current.getMatrix().mapPoints(point);
            point[0]+=current.getLeft()-parent.getScrollX();point[1]+=current.getTop()-parent.getScrollY();current=parent;
        }
        int[] origin=new int[2];current.getLocationOnScreen(origin);point[0]+=origin[0];point[1]+=origin[1];
    }
}
