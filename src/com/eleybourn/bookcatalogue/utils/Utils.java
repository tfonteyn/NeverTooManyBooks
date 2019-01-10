/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ENHANCE: make timeouts configurable; global ? or per {@link SearchSites.Site} ?
 * However, not all connections actually use this method + this is old... do we still need this ?
 */
public final class Utils {

    /** initial connection time to websites timeout. */
    public static final int CONNECT_TIMEOUT = 30_000;
    /** timeout for requests to  website. */
    public static final int READ_TIMEOUT = 30_000;
    /** kill connections after this delay. */
    public static final int KILL_CONNECT_DELAY = 30_000;
    /** for synchronization. */
    private static final Object LOCK = new Object();

    private Utils() {
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

                    connInfo.inputStream = new StatefulBufferedInputStream(
                            connInfo.connection.getInputStream());

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
     * @return <tt>true</tt> if the application can access the internet
     */
    public static boolean isNetworkAvailable(@NonNull final Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] info = connectivity.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    /**
     * Passed a list of Objects, remove duplicates based on the toString result.
     * <p>
     * ENHANCE: Add author_aliases table to allow further pruning
     * (eg. Joe Haldeman == Jow W Haldeman).
     * ENHANCE: Add series_aliases table to allow further pruning
     * (eg. 'Amber Series' <==> 'Amber').
     *
     * @param db   Database connection to lookup IDs
     * @param list List to clean up
     */
    public static <T extends ItemWithIdFixup> boolean pruneList(@NonNull final CatalogueDBAdapter db,
                                                                @Nullable final List<T> list) {
        Objects.requireNonNull(list);

        Map<String, Boolean> names = new HashMap<>();
        @SuppressLint("UseSparseArrays")
        Map<Long, Boolean> ids = new HashMap<>();

        // We have to go forwards through the list because 'first item' is important,
        // but we also can't delete things as we traverse if we are going forward. So
        // we build a list of items to delete.
        ArrayList<Integer> toDelete = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            T item = list.get(i);
            Long id = item.fixupId(db);
            String name = item.toString().trim().toUpperCase();

            // Series special case - same name different series number.
            // This means different series positions will have the same ID but will have
            // different names; so ItemWithIdFixup contains the 'isUniqueById()' method.
            if (ids.containsKey(id) && !names.containsKey(name) && !item.isUniqueById()) {
                ids.put(id, true);
                names.put(name, true);
            } else if (names.containsKey(name) || (id != 0 && ids.containsKey(id))) {
                toDelete.add(i);
            } else {
                ids.put(id, true);
                names.put(name, true);
            }
        }
        for (int i = toDelete.size() - 1; i >= 0; i--) {
            list.remove(toDelete.get(i).intValue());
        }
        return toDelete.size() > 0;
    }

    /**
     * Only does web & email links. Most likely all we'll ever need.
     *
     * @param html Partial HTML
     *
     * @return Spannable with all links
     *
     * @see #linkifyHtml(String, int)
     */
    @NonNull
    public static Spannable linkifyHtml(@NonNull final String html) {
        return linkifyHtml(html, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
    }

    /**
     * Linkify partial HTML. Linkify methods remove all spans before building links, this
     * method preserves them.
     * <p>
     * See:
     * http://stackoverflow.com/questions/14538113/using-linkify-addlinks-combine-with-html-fromhtml
     *
     * @param html        Partial HTML
     * @param linkifyMask Linkify mask to use in Linkify.addLinks
     *
     * @return Spannable with all links
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static Spannable linkifyHtml(@NonNull final String html,
                                        final int linkifyMask) {
        // Get the spannable HTML
        Spanned text = Html.fromHtml(html);
        // Save the span details for later restoration
        URLSpan[] currentSpans = text.getSpans(0, text.length(), URLSpan.class);

        // Build an empty spannable then add the links
        SpannableString buffer = new SpannableString(text);
        Linkify.addLinks(buffer, linkifyMask);

        // Add back the HTML spannable's
        for (URLSpan span : currentSpans) {
            int end = text.getSpanEnd(span);
            int start = text.getSpanStart(span);
            buffer.setSpan(span, start, end, 0);
        }
        return buffer;
    }

    /**
     * Format a number of bytes in a human readable form.
     */
    @NonNull
    public static String formatFileSize(final float space) {
        if (space < 3072) {
            // Show 'bytes' if < 3k
            return String.format(BookCatalogueApp.getResourceString(R.string.bytes),
                                 space);
        } else if (space < 250 * 1024) {
            // Show Kb if less than 250kB
            return String.format(BookCatalogueApp.getResourceString(R.string.kilobytes),
                                 space / 1024);
        } else {
            // Show MB otherwise...
            return String.format(BookCatalogueApp.getResourceString(R.string.megabytes),
                                 space / (1024 * 1024));
        }
    }

    /**
     * Convert a array of objects to a csv string fit for user displaying.
     *
     * @param list with items. toString() will be used to make the item displayable.
     *
     * @return Resulting string
     */
    @NonNull
    public static <T> String toDisplayString(@NonNull final List<T> list) {
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder details = new StringBuilder(list.get(0).toString().trim());
        for (int i = 1; i < list.size(); i++) {
            details.append(", ").append(list.get(i).toString().trim());
        }
        return details.toString();
    }

    public interface ItemWithIdFixup {

        long fixupId(@NonNull final CatalogueDBAdapter db);

        boolean isUniqueById();
    }

    private static class ConnectionInfo {

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
