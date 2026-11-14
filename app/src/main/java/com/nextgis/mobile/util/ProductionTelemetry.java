package com.nextgis.mobile.util;

import com.nextgis.mobile.BuildConfig;

/** Explicit call boundary inspected independently in the signed APK by Publisher. */
public final class ProductionTelemetry {
    private ProductionTelemetry() { }
    public static boolean enabled() {return !BuildConfig.DEBUG&&!BuildConfig.FIELD_VERIFICATION;}
    public static String usageEndpoint() {return BuildConfig.USAGE_ENDPOINT;}
    public static String usageToken() {return BuildConfig.USAGE_UPLOAD_TOKEN;}
    public static String sentryDsn() {return BuildConfig.SENTRY_DSN;}
    public static String artifactEndpoint() {return BuildConfig.DIAGNOSTIC_ARTIFACT_ENDPOINT;}
    public static String artifactToken() {return BuildConfig.DIAGNOSTIC_ARTIFACT_UPLOAD_TOKEN;}
}
