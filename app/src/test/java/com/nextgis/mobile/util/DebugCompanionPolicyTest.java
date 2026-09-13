package com.nextgis.mobile.util;

import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import java.util.Collections;
import static org.junit.Assert.*;

public class DebugCompanionPolicyTest {
    private JSONObject manifest() throws Exception {
        return new JSONObject().put("schemaVersion", 1).put("applicationId", DebugCompanionPolicy.PACKAGE)
                .put("flavor", "debug").put("channel", "debug").put("versionCode", 213).put("versionName", "3.1.2.18")
                .put("apkSize", 1000).put("apkSha256", String.join("", Collections.nCopies(64, "a")))
                .put("signingCertificateSha256", DebugCompanionPolicy.CERTIFICATE).put("minSdk", 26).put("targetSdk", 36)
                .put("publishedAt", "2026-09-13T00:00:00Z")
                .put("apkUrl", "https://apps-geonical.ru/lisa-mobile/debug/releases/213/debug.apk");
    }
    @Test public void acceptsOnlyDebugPackageChannelFlavorAndPinnedCertificate() throws Exception {
        assertEquals(213, new DebugCompanionPolicy.Manifest(manifest(), 36).code);
        for (String key : new String[]{"applicationId", "flavor", "channel", "signingCertificateSha256"}) {
            try { new DebugCompanionPolicy.Manifest(manifest().put(key, "lisa"), 36); fail(key); } catch (IOException expected) { }
        }
    }
    @Test public void rejectsUnsafeOrWrongReleaseUrls() throws Exception {
        for (String url : new String[]{"http://apps-geonical.ru/lisa-mobile/debug/releases/213/debug.apk",
                "https://apps-geonical.ru/lisa-mobile/lisa/releases/213/debug.apk",
                "https://apps-geonical.ru/lisa-mobile/debug/releases/212/debug.apk",
                "https://apps-geonical.ru/lisa-mobile/debug/releases/213/../213/debug.apk",
                "https://apps-geonical.ru/lisa-mobile/debug/releases/213/%2e%2e/a.apk",
                "https://apps-geonical.ru/lisa-mobile/debug/releases/213/debug.apk?next=other",
                "https://other.example/lisa-mobile/debug/releases/213/debug.apk"}) {
            try { new DebugCompanionPolicy.Manifest(manifest().put("apkUrl", url), 36); fail(url); } catch (IOException expected) { }
        }
    }
    @Test public void checksInstalledAndArchiveSignersAndRequiresUpgrade() throws Exception {
        DebugCompanionPolicy.Manifest m = new DebugCompanionPolicy.Manifest(manifest(), 36);
        java.util.Set<String> trusted = Collections.singleton(DebugCompanionPolicy.CERTIFICATE);
        DebugCompanionPolicy.validateApk(m, DebugCompanionPolicy.PACKAGE, "debug", 213, "3.1.2.18", 212, trusted, trusted);
        for (int variant = 0; variant < 4; variant++) {
            try {
                DebugCompanionPolicy.validateApk(m, DebugCompanionPolicy.PACKAGE, "debug", 213,
                        variant == 0 ? "different" : "3.1.2.18", variant == 1 ? 213 : 212,
                        variant == 2 ? Collections.emptySet() : trusted, variant == 3 ? Collections.emptySet() : trusted);
                fail();
            } catch (IOException expected) { }
        }
    }
    @Test public void rejectsBrokenSchemaSizeHashAndSdk() throws Exception {
        for (JSONObject invalid : new JSONObject[]{manifest().put("schemaVersion", 2), manifest().put("apkSize", -1),
                manifest().put("apkSha256", "short"), manifest().put("minSdk", 37), manifest().put("targetSdk", 25)}) {
            try { new DebugCompanionPolicy.Manifest(invalid, 36); fail(); } catch (IOException expected) { }
        }
    }
}
