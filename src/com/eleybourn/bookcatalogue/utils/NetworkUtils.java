package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.simpletasks.Terminator;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * ENHANCE: make timeouts configurable; global ? or per {@link SearchSites.Site} ?
 * However, not all connections actually use this method + this is old... do we still need this ?
 */
public final class NetworkUtils {

    /** initial connection time to websites timeout. */
    private static final int CONNECT_TIMEOUT = 30_000;
    /** timeout for requests to  website. */
    private static final int READ_TIMEOUT = 30_000;
    /** kill connections after this delay. */
    private static final int KILL_CONNECT_DELAY = 30_000;
    /** for synchronization. */
    private static final Object LOCK = new Object();

    private NetworkUtils() {
    }

    /**
     * https://developer.android.com/about/versions/marshmallow/android-6.0-changes
     * The Apache HTTP client was removed from 6.0 (although you can use a legacy lib)
     * Recommended is to use the java.net.HttpURLConnection
     * This means com.android.okhttp
     * https://square.github.io/okhttp/
     * <p>
     * 2018-11-22: removal of apache started....
     * <p>
     * Get data from a URL. Makes sure timeout is set to avoid application stalling.
     *
     * @param url URL to retrieve
     *
     * @return InputStream
     */
    @Nullable
    public static InputStream getInputStreamWithTerminator(@NonNull final URL url)
            throws IOException {

        synchronized (LOCK) {

            int retries = 3;
            while (true) {

                final ConnectionInfo connInfo = new ConnectionInfo();

                try {
                    /*
                     * There is a problem with failed timeouts:
                     *   http://thushw.blogspot.hu/2010/10/java-urlconnection-provides-no-fail.html
                     *
                     * So...we are forced to use a background thread to be able to kill it.
                     */

                    // paranoid sanity check
                    URLConnection urlConnection = url.openConnection();
                    if (!(urlConnection instanceof HttpURLConnection)) {
                        return null;
                    }

                    connInfo.connection = (HttpURLConnection) urlConnection;
                    connInfo.connection.setUseCaches(false);
                    connInfo.connection.setDoInput(true);
                    connInfo.connection.setDoOutput(false);
                    connInfo.connection.setConnectTimeout(CONNECT_TIMEOUT);
                    connInfo.connection.setReadTimeout(READ_TIMEOUT);
                    connInfo.connection.setRequestMethod("GET");

                    // close the connection on a background task,
                    // so that we can cancel any runaway timeouts.
                    Terminator.enqueue(new Runnable() {
                        @Override
                        public void run() {
                            if (connInfo.inputStream != null) {
                                if (connInfo.inputStream.isOpen()) {
                                    try {
                                        connInfo.inputStream.close();
                                        connInfo.connection.disconnect();
                                    } catch (IOException e) {
                                        Logger.error(e);
                                    }
                                }
                            } else {
                                connInfo.connection.disconnect();
                            }
                        }
                    }, KILL_CONNECT_DELAY);

                    connInfo.inputStream =
                            new StatefulBufferedInputStream(connInfo.connection.getInputStream());

                    if (connInfo.connection != null
                            && connInfo.connection.getResponseCode() >= 300) {
                        Logger.error("URL lookup failed: "
                                             + connInfo.connection.getResponseCode()
                                             + ' ' + connInfo.connection.getResponseMessage()
                                             + ", URL: " + url);
                        return null;
                    }

                    return connInfo.inputStream;

                } catch (java.net.UnknownHostException e) {
                    Logger.error(e);
                    retries--;
                    if (retries-- == 0) {
                        throw e;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    if (connInfo.connection != null) {
                        connInfo.connection.disconnect();
                    }
                } catch (InterruptedIOException e) {
                    Logger.info(Terminator.class,
                                "InterruptedIOException: " + e.getLocalizedMessage());
                    if (connInfo.connection != null) {
                        connInfo.connection.disconnect();
                    }
                } catch (Exception e) {
                    Logger.error(e);

                    if (connInfo.connection != null) {
                        connInfo.connection.disconnect();
                    }
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Check if we have *any* network open. We're not picky, first network that says ok is fine.
     *
     * @return <tt>true</tt> if the application can access the internet
     */
    public static boolean isNetworkAvailable(@NonNull final Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            for (Network network : connectivity.getAllNetworks()) {
                NetworkInfo info = connectivity.getNetworkInfo(network);
                if (info != null && info.getState() == NetworkInfo.State.CONNECTED) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class ConnectionInfo {

        @Nullable
        HttpURLConnection connection;
        @Nullable
        StatefulBufferedInputStream inputStream;
    }

    public static class StatefulBufferedInputStream
            extends BufferedInputStream
            implements Closeable {

        private boolean mIsOpen = true;

        StatefulBufferedInputStream(@NonNull final InputStream in) {
            super(in);
        }

        @Override
        @CallSuper
        public void close()
                throws IOException {
            try {
                super.close();
            } finally {
                mIsOpen = false;
            }
        }

        public boolean isOpen() {
            return mIsOpen;
        }
    }
}
