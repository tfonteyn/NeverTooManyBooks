package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;


public final class NetworkUtils
        extends AsyncTask<Void, Void, Boolean> {

    @NonNull
    private final InternetCheckCallback mCallback;
    private String mHost;
    private int mPort;

    /**
     * Constructor.
     *
     * @param callback for callback
     */
    public NetworkUtils(@NonNull final InternetCheckCallback callback) {
        mCallback = callback;
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
                if (info != null && info.isConnected()) {
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
    public static boolean isWifiAvailable(@NonNull final Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null) {
                return info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return false;
    }

    /**
     * Check if Google DNS is reachable.
     */
    public void isGoogleAlive() {
        mHost = "8.8.8.8";
        mPort = 53;
        execute();
    }

    /**
     * Check if a specific web site is reachable.
     *
     * @param host   to check; hostname or ip address
     * @param secure use the secure port (or standard port)
     */
    public void isWebSiteAlive(@NonNull final String host,
                               final boolean secure) {
        mHost = host;
        mPort = secure ? 443 : 80;
        execute();
    }

    @Override
    protected Boolean doInBackground(@Nullable final Void... params) {
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(mHost, mPort), 1500);
            sock.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected void onPostExecute(@NonNull final Boolean result) {
        mCallback.hasInternet(result);
    }


    private interface InternetCheckCallback {
        /**
         * Callback reporting actual internet being up.
         *
         * @param isUp <tt>true</tt> if Google is reachable
         */
        void hasInternet(final boolean isUp);
    }

}
