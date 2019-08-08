/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.settings.Prefs;

public final class NetworkUtils {

    /**
     * timeout we allow for a connection to work.
     * Initial tests show the sites we use, connect in less then 200ms.
     */
    private static final int SOCKET_TIMEOUT_MS = 1500;
    private static final Pattern SLASH_PATTERN = Pattern.compile("//");

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

    private static boolean isAllowMobileData() {
        return PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                .getBoolean(Prefs.pk_network_mobile_data, false);
    }

    /**
     * Use {@link NetworkCapabilities}
     * * Check for un-metered access for example.
     */
    @AnyThread
    private static Connectivity getNetworkConnectivity() {

        ConnectivityManager connMgr = (ConnectivityManager)
                                              App.getAppContext()
                                                 .getSystemService(Context.CONNECTIVITY_SERVICE);

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
     *
     * @param site url to check,
     *
     * @return {@code true} on success.
     */
    @WorkerThread
    public static boolean isAlive(@NonNull final String site) {

        String url = site.toLowerCase(LocaleUtils.getPreferredLocale());
        int port = url.startsWith("https://") ? 443 : 80;
        String host = SLASH_PATTERN.split(url)[1].split("/")[0];
        return isAlive(host, port);
    }

    /**
     * Check if Google DNS is reachable.
     */
    @SuppressWarnings("unused")
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

        } catch (@NonNull final IOException e) {
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
            return "Connectivity{"
                   + "hasEthernet=" + hasEthernet
                   + ", hasWifi=" + hasWifi
                   + ", hasBluetooth=" + hasBluetooth
                   + ", hasMobile=" + hasMobile
                   + '}';
        }

        public enum Type {
            any, wifi, ethernet, bluetooth, mobile
        }
    }
}
