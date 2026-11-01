package com.nextgis.mobile.util;

import android.app.Application;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Fault injection at the real descriptor/session boundary; no sleep or external provider. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LegacyUnderlayTransferSessionTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void localFinalizationKeepsProgressWatchdogUntilSessionClose() throws Exception {
        List<LegacyUnderlayImporter.Progress> progress = new ArrayList<>();
        Object session = session(progress);
        ScheduledExecutorService watchdog = (ScheduledExecutorService) field(session, "watchdog");
        try {
            call(session, "onBytesRead", new Class<?>[]{int.class}, 1024);
            assertEquals(LegacyUnderlayImporter.Phase.RECEIVING, progress.get(progress.size()-1).phase);
            call(session, "tileRead"); call(session, "streamComplete");
            assertEquals(LegacyUnderlayImporter.Phase.FINALIZING, progress.get(progress.size()-1).phase);
            assertFalse("Finalization must not terminate the progress/lease watchdog", watchdog.isShutdown());
            ((AtomicLong) field(session, "lastProgressAt")).set(0L);
            set(session, "lastListenerAt", 0L);
            int before = progress.size(); call(session, "checkForStall");
            assertTrue("A finalization pulse still reaches the listener", progress.size() > before);
            assertFalse("A completed remote stream cannot hit its inactivity timeout",
                    ((AtomicBoolean) field(session, "timedOut")).get());
            LegacyUnderlayImporter.Progress latest = progress.get(progress.size()-1);
            assertEquals(1024, latest.bytesRead); assertEquals(1, latest.tilesRead);
            call(session, "setPhase", new Class<?>[]{LegacyUnderlayImporter.Phase.class}, LegacyUnderlayImporter.Phase.ATTACHING);
            assertEquals(LegacyUnderlayImporter.Phase.ATTACHING, progress.get(progress.size()-1).phase);
        } finally { ((AutoCloseable) session).close(); }
        assertTrue(watchdog.isShutdown());
        int after = progress.size(); call(session, "checkForStall"); assertEquals(after, progress.size());
    }

    @Test public void descriptorReturnedAfterTimeoutIsClosedBeforeAnyLateImport() throws Exception {
        Object session = session(new ArrayList<>());
        LateProvider provider = new LateProvider();
        provider.file = files.newFile("late-stream.bin");
        provider.timeoutDuringOpen = () -> ((AtomicBoolean) uncheckedField(session, "timedOut")).set(true);
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = "late-underlay.test";
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("late-underlay.test", provider);
        try {
            try {
                call(session, "open", new Class<?>[]{Context.class, Uri.class},
                        RuntimeEnvironment.getApplication(), Uri.parse("content://late-underlay.test/source"));
                fail("A late descriptor from a timed-out attempt cannot be consumed");
            } catch (InvocationTargetException expected) {
                assertEquals("TransferStalledException", expected.getCause().getClass().getSimpleName());
            }
            assertNotNull(provider.opened);
            try { provider.opened.getFd(); fail("Late descriptor must already be closed"); }
            catch (IllegalStateException expected) { /* Descriptor ownership is terminal. */ }
        } finally {
            ((AutoCloseable) session).close();
            if (provider.opened != null) provider.opened.close();
        }
    }

    @Test public void inactivityStillReportsStalledAndCancelsRemoteOpen() throws Exception {
        List<LegacyUnderlayImporter.Progress> progress = new ArrayList<>();
        Object session = session(progress);
        try {
            ((AtomicLong) field(session, "lastProgressAt")).set(0L);
            call(session, "checkForStall");
            assertTrue(((AtomicBoolean) field(session, "timedOut")).get());
            assertEquals(LegacyUnderlayImporter.Phase.STALLED, progress.get(progress.size()-1).phase);
            assertTrue(((android.os.CancellationSignal) field(session, "cancellationSignal")).isCanceled());
        } finally { ((AutoCloseable) session).close(); }
    }

    private static Object session(List<LegacyUnderlayImporter.Progress> progress) throws Exception {
        Class<?> type = Class.forName(LegacyUnderlayImporter.class.getName() + "$TransferSession");
        Constructor<?> constructor = type.getDeclaredConstructor(ProjectOperationCoordinator.Lease.class,
                LegacyUnderlayImporter.TransferControl.class, LegacyUnderlayImporter.ProgressListener.class,
                int.class, int.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, new LegacyUnderlayImporter.TransferControl(),
                (LegacyUnderlayImporter.ProgressListener) progress::add, 1, 2, false);
    }
    private static Object call(Object owner, String name) throws Exception { return call(owner, name, new Class<?>[0]); }
    private static Object call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(owner, args);
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static Object uncheckedField(Object owner, String name) {
        try { return field(owner, name); } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    public static class LateProvider extends ContentProvider {
        File file; Runnable timeoutDuringOpen; ParcelFileDescriptor opened;
        @Override public boolean onCreate() { return true; }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            timeoutDuringOpen.run(); opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); return opened;
        }
        @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
        @Override public String getType(Uri u) { return "application/octet-stream"; }
        @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    }
}
