/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.NetworkChecker;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ASyncExecutor;

public class NetworkCheckerImpl
        implements NetworkChecker {

    /** Log tag. */
    private static final String TAG = "NetworkCheckerImpl";

    private static final String PK_NETWORK_ALLOW_METERED = "network.allow.metered";

    /** Timeout for {@link #ping(String, int)}; connection to the DNS server. */
    private static final long DNS_TIMEOUT_MS = 5_000L;

    @NonNull
    private final Supplier<Context> appContextSupplier;

    /**
     * Constructor.
     *
     * @param appContextSupplier deferred supplier for the raw Application Context
     */
    public NetworkCheckerImpl(@NonNull final Supplier<Context> appContextSupplier) {
        this.appContextSupplier = appContextSupplier;
    }

    /**
     * Check if we have network access; taking into account whether the user permits
     * metered (i.e. pay-per-usage) networks or not.
     *
     * @return {@code true} if the application can access the internet
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @AnyThread
    public boolean isNetworkAvailable() {
        final ConnectivityManager connMgr = (ConnectivityManager)
                appContextSupplier.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network network = connMgr.getActiveNetwork();
        if (network != null) {
            // https://developer.android.com/training/basics/network-ops/reading-network-state
            final NetworkCapabilities nc = connMgr.getNetworkCapabilities(network);
            if (nc != null) {

                // Indicates that the network is set up to access the internet.
                // Note that this is about setup and not actual ability to reach public servers.
                final boolean hasInternet =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

                // Indicates the network has been found to provide actual access to
                // the public internet last time it was probed.
                final boolean isValidated =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                // Indicates that the network is not metered.
                //noinspection NegativelyNamedBooleanVariable
                final boolean isNotMetered =
                        nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

                final boolean isMeteredAllowed = PreferenceManager
                        .getDefaultSharedPreferences(appContextSupplier.get())
                        .getBoolean(PK_NETWORK_ALLOW_METERED, true);

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                    LoggerFactory.getLogger()
                                 .d(TAG, "getNetworkCapabilities",
                                    "hasInternet=" + hasInternet
                                    + "|isValidated=" + isValidated
                                    + "|isMetered=" + isNotMetered
                                    + "|isMeteredAllowed=" + isMeteredAllowed
                                 );
                }

                return hasInternet
                       && isValidated
                       && (isNotMetered || isMeteredAllowed);
            }
        }

        // network unavailable
        return false;
    }

    /**
     * Low level check if a url is reachable.
     * <p>
     * A call to {@link #isNetworkAvailable()} should be made before calling this method.
     *
     * @param urlStr      url to check
     * @param timeoutInMs timeout to use for the connect call
     *
     * @throws UnknownHostException   the IP address of a host could not be determined.
     * @throws IOException            if we cannot reach the site
     * @throws SocketTimeoutException on timeouts (both DNS and host itself)
     * @throws MalformedURLException  if the URL does not start with {@code http} or {@code https}
     */
    @WorkerThread
    public void ping(@NonNull final String urlStr,
                     final int timeoutInMs)
            throws UnknownHostException,
                   IOException,
                   SocketTimeoutException,
                   MalformedURLException {

        final URL url = new URL(urlStr.toLowerCase(Locale.ROOT));
        final String host = url.getHost();
        int port = url.getPort();
        if (port == -1) {
            switch (url.getProtocol()) {
                case "https":
                    port = 443;
                    break;

                case "http":
                    port = 80;
                    break;

                default:
                    // should never get here... flw
                    throw new MalformedURLException(urlStr);
            }
        }

        final InetAddress inetAddress = new DNSService().lookup(host, DNS_TIMEOUT_MS);

        //URGENT: there are issues with this Socket connect call lately (2022-09)
        // not sure yet if it's related to the emulator or to Android 12 (13?)
        // For now, pretend that if the DNS lookup went ok... it's all ok...
//        final Socket sock = new Socket();
//        sock.connect(new InetSocketAddress(inetAddress, port), timeoutInMs);
//        sock.close();
    }

    /**
     * Workaround for {@link InetAddress#getByName(String)} which does not support a timeout.
     */
    private static class DNSService {

        @NonNull
        InetAddress lookup(@NonNull final String host,
                           @SuppressWarnings("SameParameterValue") final long timeoutMs)
                throws CancellationException,
                       IOException,
                       SocketTimeoutException,
                       UnknownHostException {

            Future<InetAddress> future = null;
            try {
                future = ASyncExecutor.SERVICE.submit(() -> InetAddress.getByName(host));

                final InetAddress inetAddress = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                // sanity check
                if (inetAddress == null) {
                    throw new UnknownHostException(host);
                }
                return inetAddress;

            } catch (@NonNull final ExecutionException e) {
                final Throwable cause = e.getCause();

                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }

                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().e(TAG, e);
                }
                throw new UnknownHostException(host);

            } catch (@NonNull final RejectedExecutionException | InterruptedException e) {
                if (BuildConfig.DEBUG /* always */) {
                    LoggerFactory.getLogger().e(TAG, e);
                }
                throw new UnknownHostException(host);

            } catch (@NonNull final TimeoutException e) {
                // re-throw as if it's coming from the network call.
                throw new SocketTimeoutException(host);

            } finally {
                // paranoia
                if (future != null) {
                    future.cancel(true);
                }
            }
        }
    }
}
