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
package com.hardbacknutter.nevertoomanybooks.sync.stripinfo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Fetches and parses the user collection list from the site.
 * This includes visiting each page, and parsing the specific collection data.
 * These are NOT full-book data sets.
 */
class ImportCollection {

    private static final String TAG = "ImportCollection";

    /**
     * The collection page only provides a link to the front cover.
     * The back cover (identifier) can only be read from the main book page.
     * IMPORTANT: we *always* parse for the cover, but *ONLY* store the url
     * in a private key. The caller should determine if the cover is actually wanted
     * (and download it) when the collection data is being processed!
     */
    static final String BKEY_FRONT_COVER_URL = TAG + ":front_cover_url";

    private static final String ROW_ID_ATTR = "showIfInCollection-";
    private static final int ROW_ID_ATTR_LEN = ROW_ID_ATTR.length();

    /**
     * param 1: userId
     * param 2: page number
     * param 3: binary flags; see {@link #mFlags}.
     * <p>
     * suffix: "_search_%4s" : with a search-filter... not fully sure what fields are searched.
     * => not supported for now.
     * <p>
     * The suffix "_search-" is always added by the site form; so we add it as well.
     */
    private static final String URL_MY_BOOKS = "/userCollection/index/%1$s/0/%2$d/%3$s_search-";

    /**
     * Filters.
     * <ul>Each flag can be:
     * <li>0: don't care</li>
     * <li>1: Yes</li>
     * <li>2: No</li>
     * </ul>
     * <ul>
     *     <li>1000: books I own ("in bezit")</li>
     *     <li>0100: in wishlist ("verlanglijst")</li>
     *     <li>0010: read ("gelezen")</li>
     *     <li>0001: which I rated ("met score")</li>
     * </ul>
     * <p>
     * Examples: 1020: all books I own but have not yet read. Don't care about wishlist/rating.
     * <p>
     * For now hardcoded to getting all books in the collection.
     * So we get the "owned" books; but we also get the "wanted" books.
     */
    private static final String mFlags = "0000";

    /** Responsible for loading and parsing the web page. */
    @NonNull
    private final JsoupLoader mJsoupLoader;

    /** Internal id from the website; used in the auth Cookie and links. */
    @NonNull
    private final String mUserId;
    @NonNull
    private final SearchEngine mSearchEngine;
    @NonNull
    private final RowParser mRowParser;

    private int mCurrentPage;
    private int mMaxPages = -1;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param userId  as extracted from the auth Cookie.
     */
    @AnyThread
    ImportCollection(@NonNull final Context context,
                     @NonNull final SyncConfig config,
                     @NonNull final String userId) {
        mUserId = userId;
        mSearchEngine = SearchEngineRegistry.getInstance()
                                            .createSearchEngine(SearchSites.STRIP_INFO_BE);
        mJsoupLoader = new JsoupLoader();
        mRowParser = new RowParser(context, config);
    }

    boolean hasMore() {
        // haven't started yet, or there are more pages.
        return mCurrentPage == 0 || mMaxPages > mCurrentPage;
    }

    /**
     * Fetch a single page, with up to 25 books. Use {@link #hasMore()} to loop.
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return list with book-data Bundles
     *
     * @throws IOException on failure
     */
    @SuppressLint("DefaultLocale")
    @WorkerThread
    @Nullable
    List<Bundle> fetchPage(@NonNull final Context context,
                           @NonNull final ProgressListener progressListener)
            throws SearchException, IOException {

        mCurrentPage++;
        if (!hasMore()) {
            throw new SearchException(mSearchEngine.getName(), "Can't fetch more pages");
        }

        progressListener.publishProgress(1, context.getString(
                R.string.progress_msg_loading_page, mCurrentPage));

        final String url = mSearchEngine.getSiteUrl()
                           + String.format(URL_MY_BOOKS, mUserId, mCurrentPage, mFlags);

        final Document currentDocument = mJsoupLoader
                .loadDocument(url, mSearchEngine.createConnectionProducer());

        final Element root = currentDocument.getElementById("collectionContent");
        if (root != null) {
            if (mCurrentPage == 1) {
                parseMaxPages(root, progressListener);
            }
            return parsePage(root);
        }

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "No 'collectionContent' found for url=" + url);
        }

        // The last page did not contain 'collectionContent'.
        // Assume we reached the end.
        return null;
    }

    private void parseMaxPages(@NonNull final Element root,
                               @NonNull final ProgressListener progressListener)
            throws SearchException {
        final Element last = root.select("div.pagination > a").last();
        if (last != null) {
            try {
                mMaxPages = Integer.parseInt(last.text());
                // If the last page has less then 25 books, this is to high... oh well...
                progressListener.setMaxPos(mMaxPages * 25);
                progressListener.setIndeterminate(false);
                progressListener.publishProgress(0, null);
                return;

            } catch (@NonNull final NumberFormatException e) {
                throw new SearchException(mSearchEngine.getName(), "Unable to read page number");
            }
        }

        throw new SearchException(mSearchEngine.getName(), "No page numbers");
    }

    /**
     * Parse a "collectionContent" section consisting of up to 25 book rows.
     *
     * @param root of the section to parse
     *
     * @return list with book data bundles for the page; can be empty
     */
    @NonNull
    @AnyThread
    private List<Bundle> parsePage(@NonNull final Element root) {
        final List<Bundle> collection = new ArrayList<>();

        // showing 'progress' here is pointless as even older devices will be fast.
        for (final Element row : root.select("div.collectionRow")) {
            final Bundle cData = new Bundle();

            parseRow(row, cData);
            if (!cData.isEmpty()) {
                collection.add(cData);
            }
        }
        return collection;
    }

    @VisibleForTesting
    @AnyThread
    void parseRow(@NonNull final Element row,
                  @NonNull final Bundle cData) {
        final String idAttr = row.id();
        // sanity check, each row is normally a book.
        if (!idAttr.isEmpty() && idAttr.startsWith(ROW_ID_ATTR)) {
            try {
                final long externalId = Long.parseLong(idAttr.substring(ROW_ID_ATTR_LEN));
                final Element mine = row.getElementById("stripCollectie-" + externalId);
                if (mine != null) {
                    final long collectionId = Long.parseLong(mine.val());

                    cData.putLong(DBKey.SID_STRIP_INFO, externalId);

                    final Element coverElement =
                            row.selectFirst("figure.stripThumbInnerWrapper > img");
                    if (coverElement != null) {
                        final String src = coverElement.attr("src");
                        if (!src.isEmpty()) {
                            cData.putString(BKEY_FRONT_COVER_URL, src);
                        }
                    }

                    mRowParser.parse(row, cData, collectionId);
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // Make sure we don't return partial data
                cData.clear();
            }
        }
    }

    private static class RowParser
            extends CollectionBaseParser {

        /**
         * Constructor.
         *
         * @param context Current context
         */
        @AnyThread
        RowParser(@NonNull final Context context,
                  @NonNull final SyncConfig config) {
            super(context, config);
        }

        @AnyThread
        public void parse(@NonNull final Element root,
                          @NonNull final Bundle destBundle,
                          @IntRange(from = 1) final long collectionId) {

            mIdOwned = "bezit-" + collectionId;
            mIdRead = "gelezen-" + collectionId;
            mIdWanted = "wishlist-" + collectionId;

            mIdLocation = "locatie-" + collectionId;
            mIdNotes = "opmerking-" + collectionId;
            mIdDateAcquired = "aankoopdatum-" + collectionId;
            mIdRating = "score-" + collectionId;
            mIdEdition = "druk-" + collectionId;
            mIdPricePaid = "prijs-" + collectionId;
            mIdAmount = "aantal-" + collectionId;

            parseFlags(root, destBundle);
            parseDetails(root, destBundle);

            // Add as last one in case of errors thrown
            destBundle.putLong(DBKey.KEY_STRIP_INFO_COLL_ID, collectionId);
        }
    }
}
