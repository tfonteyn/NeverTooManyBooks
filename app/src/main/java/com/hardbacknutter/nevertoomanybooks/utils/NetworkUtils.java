/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.NetworkUnavailableException;

public final class NetworkUtils {

    /** Log tag. */
    private static final String TAG = "NetworkUtils";

    private static final Pattern SLASH_PATTERN = Pattern.compile("//");

    /** Timeout for {@link #ping(String)}. */
    private static final int PING_TIMEOUT_MS = 5_000;

    private NetworkUtils() {
    }

    /**
     * Check if we have network access; taking into account whether the user permits
     * metered (i.e. pay-per-usage) networks or not.
     * <p>
     * When running a JUnit test, this method will always return {@code true}.
     *
     * @return {@code true} if the application can access the internet
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread
    public static boolean isNetworkAvailable() {
        if (BuildConfig.DEBUG /* always */) {
            if (Logger.isJUnitTest) {
                return true;
            }
        }

        final ConnectivityManager connMgr = (ConnectivityManager)
                ServiceLocator.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network network = connMgr.getActiveNetwork();
        if (network != null) {
            final NetworkCapabilities nc = connMgr.getNetworkCapabilities(network);
            if (nc != null) {
                // we need internet access.
                final boolean hasInternet =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                // and we need internet access actually working!
                final boolean isValidated =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    final boolean notMetered =
                            nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

                    Log.d(TAG, "getNetworkConnectivity"
                               + "|notMetered=" + notMetered
                               + "|hasInternet=" + hasInternet
                               + "|isConnected=" + isValidated);
                }

                return hasInternet && isValidated
                       && (!connMgr.isActiveNetworkMetered() || allowMeteredNetwork());

            }
        }

        // network unavailable
        return false;
    }

    private static boolean allowMeteredNetwork() {
        return ServiceLocator.getGlobalPreferences()
                             .getBoolean(Prefs.pk_network_allow_metered, false);
    }

    /**
     * Low level check if a url is reachable.
     * <p>
     * url format: "http://some.site.com" or "https://secure.site.com"
     * Any path after the hostname will be ignored.
     * If a port is specified.. it's ignored. Only ports 80/443 are used.
     *
     * @param urlStr url to check
     *
     * @throws NetworkUnavailableException if the network itself is unavailable
     * @throws IOException                 if we cannot reach the site
     */
    @WorkerThread
    public static void ping(@NonNull final String urlStr)
            throws NetworkUnavailableException, UnknownHostException, IOException {

        if (!isNetworkAvailable()) {
            throw new NetworkUnavailableException();
        }

        final String url = urlStr.toLowerCase(Locale.ROOT);
        final int port = url.startsWith("https://") ? 443 : 80;
        final String host = SLASH_PATTERN.split(url)[1].split("/")[0];

        final Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), PING_TIMEOUT_MS);
        sock.close();
    }
}
