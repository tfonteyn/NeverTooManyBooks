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
package com.hardbacknutter.nevertoomanybooks.searches.googlebooks;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TerminatorConnection;

/**
 * ENHANCE: Get editions via http://books.google.com/books/feeds/volumes?q=editions:ISBN0380014300
 */
public final class GoogleBooksManager
        implements SearchEngine,
                   SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "googlebooks.";

    /** Type: {@code String}. */
    private static final String PREFS_HOST_URL = PREF_PREFIX + "host.url";
    private static final Pattern SPACE_PATTERN = Pattern.compile(" ", Pattern.LITERAL);

    @NonNull
    public static String getBaseURL(@NonNull final Context appContext) {
        return PreferenceManager.getDefaultSharedPreferences(appContext)
                                .getString(PREFS_HOST_URL, "https://books.google.com");
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context localizedAppContext,
                               @NonNull final String isbn,
                               final boolean fetchThumbnail)
            throws IOException {
        // %3C  <
        // %3E  >
        String url =
                getBaseURL(localizedAppContext) + "/books/feeds/volumes?q=ISBN%3C" + isbn + "%3E";
        return fetchBook(localizedAppContext, url, fetchThumbnail);
    }

    @NonNull
    @Override
    @WorkerThread
    public Bundle search(@NonNull final Context localizedAppContext,
                         @Nullable final String isbn,
                         @Nullable final String author,
                         @Nullable final String title,
                         @Nullable final /* not supported */ String publisher,
                         final boolean fetchThumbnail)
            throws IOException {

        // %2B  +
        // %3A  :
        if (author != null && !author.isEmpty()
            && title != null && !title.isEmpty()) {
            String url = getBaseURL(localizedAppContext)
                         + "/books/feeds/volumes?q="
                         + "intitle%3A" + encodeSpaces(title)
                         + "%2B"
                         + "inauthor%3A" + encodeSpaces(author);
            return fetchBook(localizedAppContext, url, fetchThumbnail);

        } else {
            return new Bundle();
        }
    }

    private Bundle fetchBook(@NonNull final Context appContext,
                             @NonNull final String url,
                             final boolean fetchThumbnail)
            throws IOException {
        Bundle bookData = new Bundle();

        SAXParserFactory factory = SAXParserFactory.newInstance();

        // The main handler can return multiple books ('entry' elements)
        GoogleBooksHandler handler = new GoogleBooksHandler();

        // The entry handler takes care of an individual book ('entry')
        GoogleBooksEntryHandler entryHandler =
                new GoogleBooksEntryHandler(bookData, fetchThumbnail);

        String oneBookUrl;
        try {
            SAXParser parser = factory.newSAXParser();

            // get the booklist
            try (TerminatorConnection con = TerminatorConnection.openConnection(appContext, url)) {
                parser.parse(con.getInputStream(), handler);
            }

            ArrayList<String> urlList = handler.getUrlList();
            if (!urlList.isEmpty()) {
                // only using the first one found, maybe future enhancement?
                oneBookUrl = urlList.get(0);
                try (TerminatorConnection con =
                             TerminatorConnection.openConnection(appContext, oneBookUrl)) {
                    parser.parse(con.getInputStream(), entryHandler);
                }
            }
        } catch (@NonNull final ParserConfigurationException | SAXException e) {
            // wrap parser exceptions in an IOException
            throw new IOException(e);
        }

        return bookData;
    }

    @NonNull
    @Override
    public String getUrl(@NonNull final Context appContext) {
        return getBaseURL(appContext);
    }

    @StringRes
    @Override
    public int getNameResId() {
        return R.string.google_books;
    }

    /**
     * replace spaces with %20.
     *
     * @param s String to encode
     *
     * @return encodes string
     */
    private String encodeSpaces(@NonNull final CharSequence s) {
//        return URLEncoder.encode(s, "UTF-8");
        return SPACE_PATTERN.matcher(s).replaceAll(Matcher.quoteReplacement("%20"));
    }
}
