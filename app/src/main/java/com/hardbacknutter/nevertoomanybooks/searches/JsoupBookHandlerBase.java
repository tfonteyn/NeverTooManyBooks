/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

public abstract class JsoupBookHandlerBase
        extends JsoupBase {

    /** The description contains div tags which we remove to make the text shorter. */
    private static final Pattern DIV_PATTERN = Pattern.compile("(\n*\\s*<div>\\s*|\\s*</div>)");
    /** Convert "&amp;" to '&'. */
    private static final Pattern AMPERSAND_PATTERN = Pattern.compile("&amp;", Pattern.LITERAL);
    /** a CR is replaced with a space. */
    private static final Pattern CR_PATTERN = Pattern.compile("\n", Pattern.LITERAL);
    /** accumulate all Authors for this book. */
    protected final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Series for this book. */
    protected final ArrayList<Series> mSeries = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    protected final ArrayList<Publisher> mPublishers = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param context      current context
     * @param searchEngine to use
     */
    protected JsoupBookHandlerBase(@NonNull final Context context,
                                   @NonNull final SearchEngine searchEngine) {
        super(context, searchEngine);
    }

    public boolean isMultiResult() {
        return false;
    }

    /**
     * Fetch a book.
     *
     * @param nativeId       native book ID (as a String)
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    @WorkerThread
    public abstract Bundle fetchByNativeId(@NonNull String nativeId,
                                           @NonNull boolean[] fetchThumbnail,
                                           @NonNull Bundle bookData)
            throws SocketTimeoutException;

    /**
     * Fetch a book.
     *
     * @param url            A fully qualified search url
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     *
     * @throws SocketTimeoutException if the connection times out
     */
    @NonNull
    @WorkerThread
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public Bundle fetchUrl(@NonNull final String url,
                           @NonNull final boolean[] fetchThumbnail,
                           @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        if (loadPage(url) == null || mDoc == null) {
            return bookData;
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        if (isMultiResult()) {
            // prevent looping.
            return bookData;
        }

        return parseDoc(fetchThumbnail, bookData);
    }

    /**
     * Parses the downloaded {@link #mDoc}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @return Bundle with book data, can be empty, but never {@code null}
     *
     * @throws SocketTimeoutException if the connection times out while fetching additional data
     */
    @NonNull
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public abstract Bundle parseDoc(@NonNull boolean[] fetchThumbnail,
                                    @NonNull Bundle bookData)
            throws SocketTimeoutException;

    /**
     * Fetch the cover from the url.
     *
     * @param url      fully qualified url
     * @param bookData Bundle to update
     * @param cIdx     0..n image index
     */
    protected void fetchCover(@NonNull final String url,
                              @NonNull final Bundle bookData,
                              @NonNull final String suffix,
                              @IntRange(from = 0) final int cIdx) {

        final String tmpName = createTempCoverFileName(bookData, suffix, cIdx);
        final String fileSpec = ImageUtils.saveImage(mContext, url, tmpName, mSearchEngine);

        if (fileSpec != null) {
            addCover(bookData, Book.BKEY_FILE_SPEC_ARRAY[cIdx], fileSpec);
        }
    }

    protected void addCover(@NonNull final Bundle bookData,
                            @NonNull final String key,
                            @NonNull final String fileSpec) {
        ArrayList<String> imageList = bookData.getStringArrayList(key);
        if (imageList == null) {
            imageList = new ArrayList<>();
        }
        imageList.add(fileSpec);
        bookData.putStringArrayList(key, imageList);
    }

    @NonNull
    protected String createTempCoverFileName(@NonNull final Bundle bookData,
                                             @NonNull final String suffix,
                                             @IntRange(from = 0) final int cIdx) {
        String name = bookData.getString(DBDefinitions.KEY_ISBN, "");
        if (name.isEmpty()) {
            // just use something...
            name = String.valueOf(System.currentTimeMillis());
        }
        return name + suffix + '_' + cIdx;
    }

    /**
     * Filter a string of all non-digits. Used to clean isbn strings, years... etc.
     *
     * @param s      string to parse
     * @param isIsbn When set will also allow 'X' and 'x'
     *
     * @return stripped string
     */
    @NonNull
    protected String digits(@Nullable final String s,
                            final boolean isIsbn) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // allows an X anywhere instead of just at the end; doesn't really matter.
            if (Character.isDigit(c) || (isIsbn && Character.toUpperCase(c) == 'X')) {
                sb.append(c);
            }
        }
        // ... but let empty Strings here just return.
        return sb.toString();
    }

    protected String cleanText(@NonNull final String textToClean) {
        String text = textToClean.trim();
        // add more rules when needed.
        if (text.contains("&")) {
            text = AMPERSAND_PATTERN.matcher(text).replaceAll(Matcher.quoteReplacement("&"));
        }
        if (text.contains("<div>")) {
            // the div elements only create empty lines, we remove them to save screen space
            text = DIV_PATTERN.matcher(text).replaceAll("");
        }
        if (text.contains("\n")) {
            text = CR_PATTERN.matcher(text).replaceAll(" ").trim();
        }
        return text;
    }
}
