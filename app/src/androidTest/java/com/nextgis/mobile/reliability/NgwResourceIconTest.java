package com.nextgis.mobile.reliability;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.INGWResource;
import com.nextgis.maplib.datasource.ngw.ResourceGroup;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.NGIDLoginActivity;
import com.nextgis.maplibui.dialog.NGWResourcesListAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the real recycled row when navigating from accounts into a resource tree. */
@RunWith(AndroidJUnit4.class)
public class NgwResourceIconTest {
    private static class Folder extends ResourceGroup {
        Folder(Connection connection) {
            super(1, connection);
            mName = "First folder";
            mType = Connection.NGWResourceTypeResourceGroup;
        }
    }
    private static class OfflineConnection extends Connection {
        final Folder folder = new Folder(this);
        OfflineConnection() { super("Test account", "test", "test", "https://example.invalid"); }
        @Override public int getChildrenCount() { return 1; }
        @Override public INGWResource getChild(int index) { return index == 0 ? folder : null; }
    }
    private static class Adapter extends NGWResourcesListAdapter {
        Adapter(Activity activity) { super(activity); }
        View accountRow(Connection connection) { return getConnectionView(connection, null); }
        void enter(INGWResource resource, boolean accounts) {
            mCurrentResource = resource;
            setShowAccounts(accounts);
        }
    }
    private static Bitmap render(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 64, 64);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
    private static void assertFolder(Activity activity, View row) {
        ImageView icon = row.findViewById(R.id.ivIcon);
        Bitmap actual = render(icon.getDrawable());
        Bitmap expected = render(ContextCompat.getDrawable(activity, R.drawable.ic_ngw_folder));
        try { assertTrue("Recycled resource row must show its folder icon", actual.sameAs(expected)); }
        finally { actual.recycle(); expected.recycle(); }
    }

    @Test public void firstFolderReplacesAddAccountAndConnectionIcons() {
        try (ActivityScenario<NGIDLoginActivity> scenario = ActivityScenario.launch(NGIDLoginActivity.class)) {
            scenario.onActivity(activity -> {
                OfflineConnection connection = new OfflineConnection();
                Adapter adapter = new Adapter(activity);
                for (Connection previous : new Connection[] { null, connection }) {
                    View recycled = adapter.accountRow(previous);
                    adapter.enter(connection, false);
                    assertSame(connection.folder, adapter.getItem(0));
                    View first = adapter.getView(0, recycled, null);
                    assertSame(recycled, first);
                    assertFolder(activity, first);
                    assertEquals("First folder", ((TextView) first.findViewById(R.id.tvName)).getText().toString());
                }
            });
        }
    }

    @Test public void parentNavigationAlsoRebindsTheFolderIcon() {
        try (ActivityScenario<NGIDLoginActivity> scenario = ActivityScenario.launch(NGIDLoginActivity.class)) {
            scenario.onActivity(activity -> {
                OfflineConnection connection = new OfflineConnection();
                Adapter adapter = new Adapter(activity);
                View recycled = adapter.accountRow(null);
                adapter.enter(connection.folder, true);
                View up = adapter.getView(0, recycled, null);
                assertFolder(activity, up);
                assertEquals(activity.getString(R.string.up_dots),
                        ((TextView) up.findViewById(R.id.tvName)).getText().toString());
            });
        }
    }
}
