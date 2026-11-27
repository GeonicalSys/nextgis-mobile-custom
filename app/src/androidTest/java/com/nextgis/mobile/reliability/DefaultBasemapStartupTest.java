package com.nextgis.mobile.reliability;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.RemoteTMSLayer;
import com.nextgis.maplib.map.TMSLayer;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.util.SettingsConstantsUI;
import com.nextgis.mobile.MainApplication;
import com.nextgis.mobile.R;
import com.nextgis.mobile.util.DefaultBasemapInitializer;
import com.nextgis.mobile.util.VerificationEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

/** Observes the real cold worker; the runner clears only the isolated app before this case. */
@RunWith(AndroidJUnit4.class)
public class DefaultBasemapStartupTest {
    @Test(timeout = 120000) public void coldBootstrapExtractsTheRealBundleOffMainWhileMainContinuesToDispatch() throws Exception {
        MainApplication app = (MainApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        assertTrue("Owned emulator only", VerificationEnvironment.isEmulator());
        DefaultBasemapInitializer initializer = app.getDefaultBasemapInitializer();
        DefaultBasemapInitializer.Snapshot status = initializer.snapshot();
        assertTrue("A preseeded workspace cannot certify cold bootstrap", status.scheduled);
        int previousRevision = status.archiveProgressRevision;
        long lastProgress = SystemClock.elapsedRealtime();
        int heartbeats = 0;
        assertMainResponsive(); heartbeats++;
        while (!status.completed) {
            assertMainResponsive(); heartbeats++;
            status = initializer.snapshot();
            assertTrue("Archive progress must be monotonic", status.archiveProgressRevision >= previousRevision);
            if (status.archiveProgressRevision != previousRevision) {
                previousRevision = status.archiveProgressRevision;
                lastProgress = SystemClock.elapsedRealtime();
            }
            assertTrue("Archive made no progress for 30 seconds; revision=" + previousRevision,
                    status.completed || SystemClock.elapsedRealtime() - lastProgress < 30000);
            if (!status.completed) Thread.sleep(200);
        }
        assertMainResponsive(); heartbeats++;
        assertTrue("Main must dispatch repeatedly throughout observation", heartbeats >= 2);
        assertTrue("Real archive callbacks must have advanced", status.archiveProgressRevision > 0);
        assertTrue("Default bundle must complete: " + status.failureType, status.succeeded);
        assertFalse(status.workerWasMainThread); assertTrue(status.mainHeartbeatDuringExtraction);
        assertTrue(status.stagingCleaned);

        int entries = 0, files = 0; byte[] expectedTile = null;
        try (ZipInputStream archive = new ZipInputStream(app.getResources().openRawResource(R.raw.mapnik))) {
            for (ZipEntry entry; (entry = archive.getNextEntry()) != null; archive.closeEntry()) {
                entries++; if (!entry.isDirectory()) files++;
                if (entry.getName().equals("mapnik/0/0/0.png")) {
                    ByteArrayOutputStream tile = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
                    for (int count; (count = archive.read(buffer)) != -1; ) tile.write(buffer, 0, count);
                    expectedTile = tile.toByteArray();
                }
            }
        }
        assertEquals("The production archive must be exercised", 1436, entries);
        assertEquals(files, status.extractedFiles); assertEquals(7, status.publishedRoots);
        assertNotNull(expectedTile);
        MapBase map = app.getMap(); RemoteTMSLayer osm = (RemoteTMSLayer) map.getLayerByPathName(MainApplication.LAYER_OSM);
        assertNotNull(osm); assertSame(osm, map.getLayer(0)); assertFalse(osm.isVisible());
        assertEquals(SettingsConstantsUI.OSM_URL, osm.getURL()); assertEquals(GeoConstants.TMSTYPE_OSM, osm.getTMSType());
        byte[] actualTile = Files.readAllBytes(new File(osm.getPath(), "0/0/0" + TMSLayer.TILE_EXT).toPath());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(expectedTile),
                MessageDigest.getInstance("SHA-256").digest(actualTile));
        assertMainResponsive();
    }

    private static void assertMainResponsive() throws InterruptedException {
        CountDownLatch heartbeat = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(heartbeat::countDown);
        assertTrue("Main must remain responsive during actual background preparation",
                heartbeat.await(5, TimeUnit.SECONDS));
    }
}
