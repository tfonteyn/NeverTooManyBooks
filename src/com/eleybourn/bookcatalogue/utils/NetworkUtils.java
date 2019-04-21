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

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;


public final class NetworkUtils {

    /**
     * timeout we allow for a connection to work.
     * Initial tests show the sites we use, connect in less then 200ms.
     */
    private static final int SOCKET_TIMEOUT_MS = 1500;

    private NetworkUtils() {
    }

    /**
     * Check if we have *any* network open. We're not picky, first network that says ok is fine.
     *
     * @return <tt>true</tt> if the application can access the internet
     */
    @AnyThread
    public static boolean isNetworkAvailable(@NonNull final Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            for (Network network : connectivity.getAllNetworks()) {
                NetworkInfo info = connectivity.getNetworkInfo(network);
                if (info != null && info.isConnected()) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.NETWORK) {
                        Logger.debug(NetworkUtils.class, "isNetworkAvailable", info);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sample code not in use right now. Use {@link android.net.NetworkCapabilities}
     * Check for un-metered access for example.
     */
    @Deprecated
    @AnyThread
    public static boolean isWifiOrEthernetAvailable(@NonNull final Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null) {
                return info.isConnected()
                        && ((info.getType() == ConnectivityManager.TYPE_WIFI)
                        || (info.getType() == ConnectivityManager.TYPE_ETHERNET));
            }
        }
        return false;
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
     * @return <tt>true</tt> on success.
     */
    @WorkerThread
    public static boolean isAlive(@NonNull final String site) {

//        String url = site.toLowerCase(LocaleUtils.getPreferredLocal());
//        int port = url.startsWith("https://") ? 443 : 80;
//        String host = url.split("//")[1].split("/")[0];
//        return isAlive(host, port);

        // test internet access instead
        return isGoogleAlive();
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
}
