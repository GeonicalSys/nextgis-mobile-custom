package com.nextgis.mobile.util;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Separate identity policy: it cannot relax the self-updater's package/flavor checks. */
public final class DebugCompanionPolicy {
    public static final String PACKAGE = "com.nextgis.mobile.debug";
    public static final String CERTIFICATE = "6ce7749e935e80243623f3c5fa146a972d5dd34f1f146b52597dd7f0dff98c71";
    public static final String MANIFEST_URL = "https://apps-geonical.ru/lisa-mobile/debug/manifest.json";
    public static final class Manifest {
        public final JSONObject json;
        public final long code, size;
        public final String version, url, hash;
        public Manifest(JSONObject json, int sdk) throws IOException {
            try {
                this.json = new JSONObject(json.toString());
                code = json.getLong("versionCode"); size = json.getLong("apkSize");
                version = json.getString("versionName"); url = json.getString("apkUrl"); hash = json.getString("apkSha256");
                if (json.getInt("schemaVersion") != 1 || !PACKAGE.equals(json.getString("applicationId"))
                        || !"debug".equals(json.getString("flavor")) || !"debug".equals(json.getString("channel"))
                        || code <= 0 || version.trim().isEmpty() || size <= 0 || size > 1024L * 1024 * 1024
                        || !hash.matches("(?i)[0-9a-f]{64}")
                        || !CERTIFICATE.equalsIgnoreCase(json.getString("signingCertificateSha256"))
                        || json.getInt("minSdk") <= 0 || json.getInt("minSdk") > sdk
                        || json.getInt("targetSdk") < json.getInt("minSdk")
                        || json.getString("publishedAt").trim().isEmpty()) throw new IOException("Invalid companion manifest");
                validateUrl(url, code);
            } catch (org.json.JSONException e) { throw new IOException("Invalid companion manifest", e); }
        }
    }
    private DebugCompanionPolicy() { }
    static void validateUrl(String value, long code) throws IOException {
        try {
            URI uri = new URI(value);
            String prefix = "/lisa-mobile/debug/releases/" + code + "/";
            String path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || !"apps-geonical.ru".equals(uri.getHost())
                    || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || path == null
                    || !uri.normalize().equals(uri) || path.contains("%") || !path.startsWith(prefix)
                    || !path.substring(prefix.length()).matches("[A-Za-z0-9._-]+\\.apk"))
                throw new IOException("Companion URL outside debug release directory");
        } catch (java.net.URISyntaxException e) { throw new IOException("Invalid companion URL", e); }
    }
    static void validateApk(Manifest manifest, String pkg, String flavor, long code, String version,
                            long installedCode, Set<String> archiveSigners, Set<String> installedSigners) throws IOException {
        Set<String> expected = java.util.Collections.singleton(CERTIFICATE);
        if (!PACKAGE.equals(pkg) || !"debug".equals(flavor) || manifest.code != code || code <= installedCode
                || !manifest.version.equals(version) || !expected.equals(archiveSigners) || !expected.equals(installedSigners))
            throw new IOException("Companion APK identity mismatch");
    }
}
