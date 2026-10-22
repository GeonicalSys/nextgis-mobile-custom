package com.nextgis.mobile.util;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28)
public class AppUpdateNetworkGuardTest {
    private Context context;
    private ConnectivityManager connectivity;
    private ShadowConnectivityManager shadow;
    private Network network;
    private NetworkCapabilities capabilities;

    @Before public void connectMobileWithoutRoaming() {
        context = RuntimeEnvironment.getApplication();
        connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        shadow = Shadows.shadowOf(connectivity);
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE, 0, true, true));
        network = connectivity.getActiveNetwork();
        assertNotNull(network);
        capabilities = new NetworkCapabilities();
        Shadows.shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        shadow.setNetworkCapabilities(network, capabilities);
    }

    private Call call() {
        return new OkHttpClient().newCall(new Request.Builder().url("https://update.test/test.apk").build());
    }

    @Test public void roamingCallbackCancelsAlreadyAttachedHttpAndReleasesTheObserver() throws Exception {
        int before = shadow.getNetworkCallbacks().size();
        Call call = call();
        try (AppUpdateManager.NetworkGuard guard = new AppUpdateManager.NetworkGuard(context, () -> false, c -> {})) {
            guard.attach(call);
            guard.check();
            assertFalse(call.isCanceled());
            Shadows.shadowOf(capabilities).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
            shadow.setNetworkCapabilities(network, capabilities);
            for (ConnectivityManager.NetworkCallback callback : shadow.getNetworkCallbacks())
                callback.onCapabilitiesChanged(network, capabilities);
            assertTrue(call.isCanceled());
            assertThrows(IOException.class, guard::check);
        }
        assertEquals(before, shadow.getNetworkCallbacks().size());
    }

    @Test public void networkLossCancelsBlockedHttpRatherThanWaitingForTheNextRead() throws Exception {
        Call call = call();
        try (AppUpdateManager.NetworkGuard guard = new AppUpdateManager.NetworkGuard(context, () -> false, c -> {})) {
            guard.attach(call);
            for (ConnectivityManager.NetworkCallback callback : shadow.getNetworkCallbacks()) callback.onLost(network);
            assertTrue(call.isCanceled());
        }
    }

    @Test public void stopAndNetworkReplacementPreventContinuingOnAnotherTransport() throws Exception {
        AtomicBoolean stopped = new AtomicBoolean();
        Call call = call();
        try (AppUpdateManager.NetworkGuard guard = new AppUpdateManager.NetworkGuard(context, stopped::get, c -> {})) {
            guard.attach(call);
            stopped.set(true);
            assertThrows(IOException.class, guard::check);
            assertTrue(call.isCanceled());
        }
        stopped.set(false);
        call = call();
        try (AppUpdateManager.NetworkGuard guard = new AppUpdateManager.NetworkGuard(context, stopped::get, c -> {})) {
            guard.attach(call);
            shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                    ConnectivityManager.TYPE_WIFI, 0, true, true));
            assertThrows(IOException.class, guard::check);
            assertTrue(call.isCanceled());
        }
    }

    @Test @Config(sdk = 26) public void androidEightUsesTheLegacyRoamingFlag() {
        assertTrue(AppUpdateManager.networkState(context).canDownload());
        NetworkInfo info = connectivity.getNetworkInfo(network);
        org.robolectric.util.ReflectionHelpers.setField(info, "mIsRoaming", true);
        assertFalse(AppUpdateManager.networkState(context).canDownload());
        assertTrue(AppUpdateManager.networkState(context).canRequest());
    }
}
