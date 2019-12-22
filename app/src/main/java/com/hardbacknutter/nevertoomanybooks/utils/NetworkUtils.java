/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class NetworkUtils {

    /** Log tag. */
    private static final String TAG = "NetworkUtils";

    private static final Pattern SLASH_PATTERN = Pattern.compile("//");

    private NetworkUtils() {
    }

    /**
     * Check if we have network access; taking into account whether the user permits
     * metered (i.e. pay-per-usage) networks or not.
     *
     * @param context Application context
     *
     * @return {@code true} if the application can access the internet
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread
    public static boolean isNetworkAvailable(@NonNull final Context context) {

        if (BuildConfig.DEBUG && App.isJUnitTest()) {
            return true;
        }

        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr != null) {
            Network network = connMgr.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities nc = connMgr.getNetworkCapabilities(network);
                if (nc != null) {
                    // we need internet access.
                    boolean hasInternet =
                            nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    // and we need internet access actually working!
                    boolean isValidated =
                            nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                        boolean notMetered =
                                nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

                        Log.d(TAG, "getNetworkConnectivity"
                                   + "|notMetered=" + notMetered
                                   + "|hasInternet=" + hasInternet
                                   + "|isConnected=" + isValidated);
                    }

                    return hasInternet && isValidated
                           && (!connMgr.isActiveNetworkMetered() || allowMeteredNetwork(context));

                }
            }
        }

        // network unavailable
        return false;
    }

    private static boolean allowMeteredNetwork(@NonNull final Context appContext) {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
                                .getBoolean(Prefs.pk_network_allow_metered, false);
    }

    /**
     * Low level check if a url is reachable.
     * <p>
     * url format: "http://some.site.com" or "https://secure.site.com"
     * Any path after the hostname will be ignored.
     * If a port is specified.. it's ignored. Only ports 80/443 are used.
     *
     * @param appContext  Application context
     * @param urlStr      url to check
     * @param timeoutInMs socket connect timeout in milliseconds
     *
     * @throws IOException if we cannot reach the site, or if the network itself is unavailable
     */
    @WorkerThread
    public static void poke(@NonNull final Context appContext,
                            @NonNull final String urlStr,
                            final int timeoutInMs)
            throws IOException {

        if (!isNetworkAvailable(appContext)) {
            throw new IOException("networkUnavailable");
        }

        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
        String url = urlStr.toLowerCase();
        int port = url.startsWith("https://") ? 443 : 80;
        String host = SLASH_PATTERN.split(url)[1].split("/")[0];

        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), timeoutInMs);
        sock.close();
    }
}
