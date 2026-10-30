package com.nextgis.maplibui.util;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteException;
import com.nextgis.maplib.util.NGException;
import com.nextgis.maplib.util.NgwHttpException;
import com.nextgis.maplib.util.NgwHttpResponseException;
import com.nextgis.maplib.util.HttpResponse;
import java.io.IOException;
import java.util.Locale;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Import presentation only; no Service start, map, SQLite connection, file or HTTP. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE, application=Application.class, qualifiers="ru")
public class LayerFillErrorMessageTest {
    private Context context;
    @Before public void setUp() { context=RuntimeEnvironment.getApplication(); }
    private String show(Exception error) { return LayerFillErrorMessage.forException(context,error); }
    private String generic() { return "Не удалось загрузить слой. Повторите попытку."; }
    private String serverCopy() {
        return "ВебГИС временно не может выполнить запрос. Возможно, ведутся технические работы или сервер перегружен. Попробуйте позже.";
    }
    @Test public void rawHttpPathSqlJsonAndUnknownNgExceptionNeverReachTheUser() {
        for(Exception error:new Exception[]{
                new IOException("NGW feature pull HTTP 503 password=technical https://example.test/private"),
                new SQLiteException("Q:\\private\\map.db no such column internal_column"),
                new JSONException("Unexpected token at https://example.test/private"),
                new NGException("createFromNGW: SQLite table missing columns: [internal_column]"),
                new IllegalStateException("account=technical password=technical")}) {
            assertEquals(generic(),show(error));
        }
    }
    @Test public void missingJsonFieldsAreNotMisreportedAsMissingAccessRights() {
        assertEquals(generic(),show(new JSONException("No value for fields")));
        assertNotEquals(context.getString(com.nextgis.maplib.R.string.error_403),
                show(new JSONException("No value for fields")));
        assertEquals(generic(),show(new JSONException((String)null)));
    }
    @Test public void positivelyTypedServerFailureUsesSharedMaintenanceAndRetryCopy() {
        for(int code:new int[]{429,500,503,599}) {
            assertEquals(serverCopy(),
                    show(new NgwHttpException(code,"<html>secret=technical</html>","Internal server error",
                            "NGW feature pull HTTP "+code+" https://example.test/private")));
            assertEquals(serverCopy(),show(new NgwHttpResponseException(context,
                    new HttpResponse(code,"Internal server error","<html>secret=technical</html>"))));
        }
    }
    @Test public void typedNonServerStatusDoesNotBecomeMaintenanceFromWordsAlone() {
        for(int code:new int[]{401,403,404,408}) {
            assertEquals(generic(),show(new NgwHttpException(code,"ExternalDatabaseError",null,"technical")));
            String owned=com.nextgis.maplib.util.NetworkUtil.getError(context,code);
            assertEquals(owned,show(new NgwHttpResponseException(context,
                    new HttpResponse(code,"technical response","ExternalDatabaseError"))));
            assertNotEquals(serverCopy(),owned);
        }
    }
    @Test public void ownedOfflineLoginRightsAndBadFileExplanationsStayReadable() {
        for(int resource:new int[]{com.nextgis.maplib.R.string.error_network_unavailable,
                com.nextgis.maplib.R.string.error_auth,com.nextgis.maplib.R.string.error_401,
                com.nextgis.maplib.R.string.error_403,com.nextgis.maplib.R.string.error_crs_unsupported,
                com.nextgis.maplib.R.string.error_coordinate_file_invalid,
                com.nextgis.maplib.R.string.error_coordinate_file_empty,
                com.nextgis.maplib.R.string.mbtiles_problem_vector,
                com.nextgis.maplib.R.string.error_geojson_invalid_geometry}) {
            String owned=context.getString(resource);assertEquals(owned,show(new NGException(owned)));
            assertEquals(generic(),show(new NGException(owned+" https://example.test/private")));
        }
    }
    @Test public void onlyNgExceptionCanCarryAnOwnedHumanExplanation() {
        String rights=context.getString(com.nextgis.maplib.R.string.error_403);
        assertEquals(generic(),show(new IOException(rights)));
        assertEquals(rights,show(new NGException(rights)));
    }
    @Test public void explicitPointzMarkerKeepsItsOwnedExplanation() {
        assertEquals(context.getString(com.nextgis.maplibui.R.string.pointz_alert),show(new NGException("POINTZ")));
    }
    @Test public void numericOnlyOwnedCountMismatchStaysWhileInjectedSuffixIsRejected() {
        String owned=context.getString(com.nextgis.maplib.R.string.error_ngw_feature_count_mismatch,12,34);
        assertEquals(owned,show(new NGException(owned)));
        assertEquals(generic(),show(new NGException(owned+" HTTP 503 https://example.test/private")));
    }
    @Test public void coordinateImportUsesItsExistingFallbackAndRetainsKnownValidation() {
        int fallback=com.nextgis.maplib.R.string.error_coordinate_file_import;
        assertEquals(context.getString(fallback),LayerFillErrorMessage.forException(context,
                new IOException("Q:\\private\\coordinates.gpx"),fallback));
        String owned=context.getString(com.nextgis.maplib.R.string.error_coordinate_file_invalid);
        assertEquals(owned,LayerFillErrorMessage.forException(context,new NGException(owned),fallback));
    }
    @Test public void englishCopyKeepsTheSameSafeMeaning() {
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.ENGLISH);
        Context english=context.createConfigurationContext(configuration);
        assertEquals("WebGIS cannot process the request right now. The server may be undergoing maintenance or be overloaded. Try again later.",
                LayerFillErrorMessage.forException(english,new NgwHttpException(503,"secret=technical",null,"technical")));
        assertEquals("Could not load the layer. Please try again.",
                LayerFillErrorMessage.forException(english,new IOException("Q:\\private\\technical.db")));
    }
}
