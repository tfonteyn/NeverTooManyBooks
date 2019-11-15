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
package com.hardbacknutter.nevertoomanybooks.searches.amazon;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.utils.Throttler;

/**
 * Amazon is now disabled/hidden as it can't work without the proxy from BookCatalogue.
 * FIXME: remove the dependency on that proxy.
 * https://docs.aws.amazon.com/en_pv/AWSECommerceService/latest/DG/becomingAssociate.html
 */
public final class AmazonManager
        implements SearchEngine,
                   SearchEngine.ByText,
                   SearchEngine.ByNativeId,
                   SearchEngine.ByIsbn {

    private static final String TAG = "AmazonManager";

    private static final String UTF_8 = "UTF-8";
    /** Preferences prefix. */
    private static final String PREF_PREFIX = "amazon.";
    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "host.url";
    /** Can only send requests at a throttled speed. */
    @NonNull
    private static final Throttler THROTTLER = new Throttler();
    private static final String SUFFIX_BASE_URL = "/gp/search?index=books";
    private static final String PROXY_URL = "https://bc.theagiledirector.com/getRest_v3.php?";

    /**
     * Constructor.
     */
    public AmazonManager() {
    }

    @NonNull
    public static String getBaseURL(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getString(PREFS_HOST_URL, "https://www.amazon.com");
    }

    /**
     * Start an intent to open the Amazon website.
     *
     * @param context Current context
     * @param author  to search for
     * @param series  to search for
     */
    public static void openWebsite(@NonNull final Context context,
                                   @Nullable final String author,
                                   @Nullable final String series) {

        String cAuthor = cleanupSearchString(author);
        String cSeries = cleanupSearchString(series);

        String extra = "";
        if (!cAuthor.isEmpty()) {
            try {
                extra += "&field-author=" + URLEncoder.encode(cAuthor, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(context, TAG, e, "Unable to add author to URL");
            }
        }

        if (!cSeries.isEmpty()) {
            try {
                extra += "&field-keywords=" + URLEncoder.encode(cSeries, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(context, TAG, e, "Unable to add series to URL");
            }
        }

        String url = getBaseURL(context) + SUFFIX_BASE_URL + extra.trim();
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @NonNull
    private static String cleanupSearchString(@Nullable final String search) {
        if (search == null || search.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(search.length());
        char prev = ' ';
        for (char curr : search.toCharArray()) {
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
                prev = curr;
            } else {
                if (!Character.isWhitespace(prev)) {
                    out.append(' ');
                }
                prev = ' ';
            }
        }
        return out.toString().trim();
    }

    @NonNull
    @Override
    public Bundle searchByNativeId(@NonNull final Context context,
                                   @NonNull final String nativeId,
                                   final boolean fetchThumbnail)
            throws IOException {

        if (!SearchSites.ENABLE_AMAZON_AWS) {
            return new Bundle();
        }

        return fetchBook("isbn=" + nativeId, fetchThumbnail);
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String isbn,
                               final boolean fetchThumbnail)
            throws IOException {

        if (!SearchSites.ENABLE_AMAZON_AWS) {
            return new Bundle();
        }

        return fetchBook("isbn=" + isbn, fetchThumbnail);
    }

    @Override
    @NonNull
    @WorkerThread
    public Bundle search(@NonNull final Context context,
                         @Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        if (!SearchSites.ENABLE_AMAZON_AWS) {
            return new Bundle();
        }

        if (author != null && !author.isEmpty() && title != null && !title.isEmpty()) {
            String query = "author=" + URLEncoder.encode(author, UTF_8)
                           + "&title=" + URLEncoder.encode(title, UTF_8);
            return fetchBook(query, fetchThumbnail);

        } else {
            return new Bundle();
        }

    }

    private Bundle fetchBook(@NonNull final String query,
                             final boolean fetchThumbnail)
            throws IOException {
        Bundle bookData = new Bundle();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        AmazonHandler handler = new AmazonHandler(bookData, fetchThumbnail);

        // See class docs: adding throttling
        THROTTLER.waitUntilRequestAllowed();

        String url = PROXY_URL + query;
        // Get it
        try (TerminatorConnection con = TerminatorConnection.openConnection(url)) {
            SAXParser parser = factory.newSAXParser();
            parser.parse(con.inputStream, handler);
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            // wrap parser exceptions in an IOException
            throw new IOException(e);
        }

        String error = handler.getError();
        if (error != null) {
            throw new IOException(error);
        }
        return bookData;
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context context) {
        return getBaseURL(context);
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.amazon;
    }
}
