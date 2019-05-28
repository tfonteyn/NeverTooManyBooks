package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.Prefs;

public final class NetworkUtils {

    /**
     * timeout we allow for a connection to work.
     * Initial tests show the sites we use, connect in less then 200ms.
     */
    private static final int SOCKET_TIMEOUT_MS = 1500;

    private NetworkUtils() {
    }

    /**
     * Check if we have network access.
     *
     * @return {@code true} if the application can access the internet
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread
    public static boolean isNetworkAvailable() {

        Connectivity nc = getNetworkConnectivity();

        if (isAllowMobileData()) {
            return nc.has(Connectivity.Type.any);
        } else {
            return nc.has(Connectivity.Type.wifi);
        }
    }

    public static boolean isAllowMobileData() {
        return App.getPrefs().getBoolean(Prefs.pk_ui_network_mobile_data, false);
    }

    /**
     * Use {@link android.net.NetworkCapabilities}
     * * Check for un-metered access for example.
     */
    @AnyThread
    public static Connectivity getNetworkConnectivity() {

        ConnectivityManager connMgr = (ConnectivityManager)
                App.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        Connectivity con = new Connectivity();

        for (Network network : connMgr.getAllNetworks()) {
            NetworkInfo networkInfo = connMgr.getNetworkInfo(network);
            switch (networkInfo.getType()) {
                case ConnectivityManager.TYPE_ETHERNET:
                    con.hasEthernet |= networkInfo.isConnected();
                    break;
                case ConnectivityManager.TYPE_WIFI:
                    con.hasWifi |= networkInfo.isConnected();
                    break;
                case ConnectivityManager.TYPE_BLUETOOTH:
                    con.hasBluetooth |= networkInfo.isConnected();
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                    con.hasMobile |= networkInfo.isConnected();
                    break;
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
            Logger.debug(NetworkUtils.class, "getNetworkConnectivity", con);
        }

        return con;
    }

    /**
     * Low level check if a url is reachable.
     * <p>
     * url format: "http://some.site.com" or "https://secure.site.com"
     * Any path after the hostname will be ignored.
     * If a port is specified.. it's ignored. Only ports 80/443 are used.
     * <p>
     * FIXME: this fails if there is a DNS redirect ? using a dumb check on google DNS only for now.
     *
     * @param site url to check,
     *
     * @return {@code true} on success.
     */
    @WorkerThread
    public static boolean isAlive(@NonNull final String site) {

        String url = site.toLowerCase(LocaleUtils.getPreferredLocal());
        int port = url.startsWith("https://") ? 443 : 80;
        String host = url.split("//")[1].split("/")[0];
        return isAlive(host, port);

        // test internet access instead
//        return isGoogleAlive();
    }

    /**
     * Check if Google DNS is reachable.
     */
    @WorkerThread
    public static boolean isGoogleAlive() {
        return isAlive("8.8.8.8", 53);
    }

    @WorkerThread
    public static boolean isAlive(@NonNull final String host,
                                  final int port) {
        try {
            long t;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                t = System.nanoTime();
            }
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
            sock.close();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.debug(NetworkUtils.class, "isAlive",
                             "Site: " + host + ':' + port
                                     + ", took " + (System.nanoTime() - t) + " nano");
            }
            return true;

        } catch (IOException e) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                Logger.warn(NetworkUtils.class, "isAlive",
                            "Site unreachable: " + host + ':' + port + '\n'
                                    + e.getLocalizedMessage());
            }
            return false;
        }
    }

    public static class Connectivity {

        boolean hasEthernet = false;
        boolean hasWifi = false;
        boolean hasBluetooth = false;
        boolean hasMobile = false;

        boolean has(@NonNull final Type type) {
            switch (type) {
                case any:
                    return hasEthernet || hasWifi || hasBluetooth || hasMobile;

                case wifi:
                    return hasEthernet || hasWifi;

                case ethernet:
                    return hasEthernet;
                case bluetooth:
                    return hasBluetooth;
                case mobile:
                    return hasMobile;
                default:
                    return false;
            }
        }

        @Override
        @NonNull
        public String toString() {
            return "Connectivity{" +
                    "hasEthernet=" + hasEthernet +
                    ", hasWifi=" + hasWifi +
                    ", hasBluetooth=" + hasBluetooth +
                    ", hasMobile=" + hasMobile +
                    '}';
        }

        public enum Type {
            any, wifi, ethernet, bluetooth, mobile
        }
    }
}
