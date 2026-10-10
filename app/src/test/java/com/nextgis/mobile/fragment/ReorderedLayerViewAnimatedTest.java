package com.nextgis.mobile.fragment;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import com.nextgis.maplibui.fragment.LayersListAdapter;
import com.nextgis.maplibui.mapui.MapView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static com.nextgis.maplib.util.Constants.NOT_FOUND;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = {26, 36})
@LooperMode(LooperMode.Mode.PAUSED)
public class ReorderedLayerViewAnimatedTest {
    static class CountingAdapter extends LayersListAdapter {
        int ended;
        CountingAdapter(Context context) { super(null, new MapView(context, null)); }
        @Override public void endDrag() { ended++; }
    }

    static class TestView extends ReorderedLayerViewAnimated {
        CountingAdapter adapter;
        View row;
        TestView(Context context) {
            super(context);
            adapter = new CountingAdapter(context);
        }
        @Override public ListAdapter getAdapter() { return adapter; }
        @Override public View getViewForID(long id) { return row; }
        void drag(boolean waiting) {
            mCellIsMobile = !waiting;
            mIsWaitingForScrollFinish = waiting;
            mIsMobileScrolling = true;
            mMobileItemId = 42;
            mAboveItemId = 41;
            mBelowItemId = 43;
            mActivePointerId = 0;
            mHoverCell = new BitmapDrawable(getResources(), Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888));
            mHoverCellCurrentBounds = new Rect(0, 0, 4, 4);
            mHoverCellOriginalBounds = new Rect(0, 0, 4, 4);
            mCurrentVisibleItemCount = 1;
        }
        void assertReset() {
            assertFalse(mCellIsMobile);
            assertFalse(mIsWaitingForScrollFinish);
            assertFalse(mIsMobileScrolling);
            assertEquals(NOT_FOUND, mMobileItemId);
            assertEquals(NOT_FOUND, mAboveItemId);
            assertEquals(NOT_FOUND, mBelowItemId);
            assertEquals(NOT_FOUND, mActivePointerId);
            assertNull(mHoverCell);
            assertTrue(isEnabled());
        }
        void fling() { mScrollState = AbsListView.OnScrollListener.SCROLL_STATE_FLING; }
        void assertWaiting() {
            assertTrue(mIsWaitingForScrollFinish);
            assertEquals(42, mMobileItemId);
            assertNotNull(mHoverCell);
        }
    }

    @Test public void idleAfterFlingWithDetachedRowResetsDragOnce() {
        TestView view = new TestView(RuntimeEnvironment.getApplication());
        view.drag(true);
        view.onScrollStateChanged(view, AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
        view.assertReset();
        assertEquals(1, view.adapter.ended);
        view.onScrollStateChanged(view, AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
        assertEquals(1, view.adapter.ended);
    }

    @Test public void cancellationRestoresVisibleRowAndClearsWaitingState() {
        TestView view = new TestView(RuntimeEnvironment.getApplication());
        view.drag(true);
        view.row = new View(view.getContext());
        view.row.setVisibility(View.INVISIBLE);
        view.setEnabled(false);
        view.touchEventsCancelled();
        view.assertReset();
        assertEquals(View.VISIBLE, view.row.getVisibility());
        assertEquals(1, view.adapter.ended);
    }

    @Test public void detachedAdapterDoesNotLeaveStaleDrag() {
        TestView view = new TestView(RuntimeEnvironment.getApplication());
        view.drag(false);
        view.adapter = null;
        view.touchEventsEnded();
        view.assertReset();
    }

    @Test public void visibleRowWaitsForScrollInsteadOfCancelling() {
        TestView view = new TestView(RuntimeEnvironment.getApplication());
        view.drag(false);
        view.row = new View(view.getContext());
        view.fling();
        view.touchEventsEnded();
        view.assertWaiting();
        assertEquals(1, view.adapter.ended);
        view.touchEventsCancelled();
    }
}
