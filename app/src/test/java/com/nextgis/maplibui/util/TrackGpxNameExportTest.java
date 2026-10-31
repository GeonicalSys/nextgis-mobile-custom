package com.nextgis.maplibui.util;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.util.MapUtil;
import com.nextgis.mobile.MainApplication;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.util.Formatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import org.w3c.dom.Document;
import static org.junit.Assert.*;

/** Actual ExportGPXTask worker with owned technical files and cursor-provider doubles.
 * No real track DB, business geometry, FileProvider recipient, async task or native Android run.
 * The independent XML parser must decode the original metadata exactly; text-only cases call
 * actual appendTrack to avoid Windows-only invalid-filename behavior, other cases use full worker.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = TrackGpxNameExportTest.ProbeApplication.class,
        shadows = TrackGpxNameExportTest.OwnedCache.class,
        instrumentedPackages = "com.nextgis.maplib.util")
@LooperMode(LooperMode.Mode.PAUSED)
public class TrackGpxNameExportTest {
    private static final String AUTHORITY = "technical.track.export";
    private static final String GPX = "http://www.topografix.com/GPX/1/1";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    public static class ProbeApplication extends MainApplication {
        @Override public void onCreate() { /* No bootstrap, map, migration or workers. */ }
        @Override public void onTerminate() { }
        @Override public String getAuthority() { return AUTHORITY; }
    }

    @Implements(value = MapUtil.class, isInAndroidSdk = false)
    public static class OwnedCache {
        static File directory;
        @Implementation protected static File prepareTempDir(Context context, String path, Boolean preserve) {
            assertEquals("exported_tracks", path);
            assertEquals(Boolean.FALSE, preserve);
            return directory;
        }
    }

    private static final class DirectTask extends ExportGPXTask {
        DirectTask(Activity host, String id) {
            super(host, "NextGIS Mobile", new ArrayList<>(Arrays.asList(id)), true);
        }
        @Override protected void onProgressUpdate(Integer... ignored) { /* No progress dialog. */ }
        @SuppressWarnings("unchecked") List<File> runWorker() {
            return (List<File>) doInBackground(new Void[0]);
        }
    }

    public static class TechnicalProvider extends ContentProvider {
        String name;
        boolean noPoints;
        int metadataQueries, pointQueries;
        String selectedMetadataId, selectedPointsId;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] args, String order) {
            if (uri.getPathSegments().size() == 1) {
                metadataQueries++;
                assertArrayEquals(new String[]{TrackLayer.FIELD_NAME}, projection);
                assertEquals(TrackLayer.FIELD_ID + " = ?", selection);
                assertEquals(1, args.length);
                selectedMetadataId = args[0];
                MatrixCursor cursor = new MatrixCursor(new String[]{TrackLayer.FIELD_NAME});
                cursor.addRow(new Object[]{name});
                return cursor;
            }
            pointQueries++;
            selectedPointsId = uri.getLastPathSegment();
            assertNull(selection);
            assertNull(args);
            assertEquals(TrackLayer.POINT_ORDER, order);
            MatrixCursor cursor = new MatrixCursor(new String[]{TrackLayer.FIELD_LON,
                    TrackLayer.FIELD_LAT, TrackLayer.FIELD_TIMESTAMP, TrackLayer.FIELD_ELE,
                    TrackLayer.FIELD_SAT, TrackLayer.FIELD_BEARING, TrackLayer.FIELD_SEGMENT,
                    TrackLayer.FIELD_ACCURACY});
            if (!noPoints) {
                // Same technical coordinate scale as existing Core unit fixtures, not business data.
                cursor.addRow(new Object[]{1000d, 1000d, 0L, 5d, 4, 90d, 0, 1.2f});
                cursor.addRow(new Object[]{1001d, 1001d, 1000L, 6d, 5, 91d, 1, 1.3f});
            }
            return cursor;
        }
        @Override public String getType(Uri uri) { return "technical/tracks"; }
        @Override public Uri insert(Uri uri, ContentValues values) { throw new AssertionError("No writes"); }
        @Override public int delete(Uri uri, String selection, String[] args) { throw new AssertionError("No deletes"); }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
            throw new AssertionError("No track mutations");
        }
    }

    private Activity host;
    private TechnicalProvider provider;
    private File runRoot;

    @Before public void prepare() throws Exception {
        assertEquals("Android export encoding requires UTF-8 test JVM", StandardCharsets.UTF_8,
                Charset.defaultCharset());
        runRoot = temporary.newFolder("owned-run");
        OwnedCache.directory = new File(runRoot, "exported_tracks");
        assertTrue(OwnedCache.directory.mkdir());
        host = Robolectric.buildActivity(Activity.class).setup().get();
        provider = new TechnicalProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = AUTHORITY;
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider);
    }

    private List<File> export(String name) {
        provider.name = name;
        return new DirectTask(host, "41").runWorker();
    }

    private File exported(String name) {
        List<File> files = export(name);
        assertEquals("One selected persisted track produces one completed GPX", 1, files.size());
        assertTrue(files.get(0).isFile());
        return files.get(0);
    }

    private Document parse(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try { return factory.newDocumentBuilder().parse(file); }
        catch (Exception error) { fail("GPX must be well-formed XML: " + error.getClass().getSimpleName()); return null; }
    }

    private String decodedName(File file) throws Exception {
        Document document = parse(file);
        assertEquals(1, document.getElementsByTagNameNS(GPX, "name").getLength());
        return document.getElementsByTagNameNS(GPX, "name").item(0).getTextContent();
    }

    // Android permits characters that the Windows host filesystem rejects in a basename.
    // Isolate XML text at the actual appendTrack boundary; worker/filename controls stay below.
    private File appendMetadata(String name) throws Exception {
        File file = new File(OwnedCache.directory, "technical-xml.gpx");
        Files.write(file.toPath(), ("<gpx xmlns=\"" + GPX + "\">").getBytes(StandardCharsets.UTF_8));
        Method append = ExportGPXTask.class.getDeclaredMethod("appendTrack", File.class,
                String.class, StringBuilder.class, Formatter.class, Cursor.class);
        append.setAccessible(true);
        StringBuilder text = new StringBuilder();
        try (Formatter formatter = new Formatter(text);
                Cursor cursor = provider.query(Uri.parse("content://" + AUTHORITY + "/tracks/41"),
                        null, null, null, TrackLayer.POINT_ORDER)) {
            assertTrue(cursor.moveToFirst());
            append.invoke(new DirectTask(host, "41"), file, name, text, formatter, cursor);
        }
        Files.write(file.toPath(), "</gpx>".getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        return file;
    }

    @Test public void xmlMetacharactersAndValidUnicodeDecodeToOriginalName() throws Exception {
        String name = "Технический & <след> \"' > \uD83D\uDE00";
        assertEquals(name, decodedName(appendMetadata(name)));
    }

    @Test public void entityLookingTextIsNeverDecodedTwice() throws Exception {
        String name = "&amp; &#13; &lt;";
        assertEquals(name, decodedName(appendMetadata(name)));
    }

    @Test public void validXmlWhitespaceIncludingCarriageReturnIsPreserved() throws Exception {
        String name = "technical\tline\ncarriage\rreturn";
        assertEquals(name, decodedName(appendMetadata(name)));
    }

    @Test public void anOrdinaryFilenameAndGpxMetadataStayUnchanged() throws Exception {
        File file = exported("technical track");
        assertEquals("technical track.gpx", file.getName());
        assertEquals("technical track", decodedName(file));
        assertEquals(OwnedCache.directory.getCanonicalFile(), file.getCanonicalFile().getParentFile());
    }

    @Test public void relativeTraversalCannotWriteOutsideTheExportDirectory() throws Exception {
        String name = "../outside";
        File file = exported(name);
        assertEquals(OwnedCache.directory.getCanonicalFile(), file.getCanonicalFile().getParentFile());
        assertFalse(new File(runRoot, "outside.gpx").exists());
        assertEquals(name, decodedName(file));
    }

    @Test public void pathSeparatorsChangeOnlyBasenameAndRetainFullGpxName() throws Exception {
        String name = "folder/technical\\track";
        File file = exported(name);
        assertEquals("folder_technical_track.gpx", file.getName());
        assertEquals(OwnedCache.directory.getCanonicalFile(), file.getCanonicalFile().getParentFile());
        assertEquals(name, decodedName(file));
    }

    @Test public void anAbsoluteLookingNameIsContainedAndKeepsItsMetadata() throws Exception {
        String name = "/absolute";
        File file = exported(name);
        assertEquals(OwnedCache.directory.getCanonicalFile(), file.getCanonicalFile().getParentFile());
        assertEquals(name, decodedName(file));
    }

    @Test public void workerReadsOnlyRequestedSharedTrackIdAndPreservesSegments() throws Exception {
        Document document = parse(exported("technical"));
        assertEquals("41", provider.selectedMetadataId);
        assertEquals("41", provider.selectedPointsId);
        assertEquals(1, provider.metadataQueries);
        assertEquals(1, provider.pointQueries);
        assertEquals(1, document.getElementsByTagNameNS(GPX, "trk").getLength());
        assertEquals(2, document.getElementsByTagNameNS(GPX, "trkseg").getLength());
        assertEquals(2, document.getElementsByTagNameNS(GPX, "trkpt").getLength());
        assertTrue(document.getElementsByTagNameNS(GPX, "time").item(0).getTextContent().endsWith("00:00:00Z"));
        assertTrue(document.getElementsByTagNameNS(GPX, "time").item(1).getTextContent().endsWith("00:00:01Z"));
    }

    @Test public void noPersistedPointsDoesNotPublishAnExport() {
        provider.noPoints = true;
        assertTrue(export("technical").isEmpty());
    }

    @Test public void nullLegacyNameStillUsesPriorFilenameWithoutANameElement() throws Exception {
        File file = exported(null);
        assertEquals("null.gpx", file.getName());
        assertEquals(0, parse(file).getElementsByTagNameNS(GPX, "name").getLength());
    }
}
