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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;
import com.hardbacknutter.nevertoomanybooks.network.TerminatorConnection;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

/**
 * Fetches and parses the user collection list from the site.
 * This includes visiting each page, and parsing the specific collection data.
 * These are NOT full-book data sets.
 *
 * Ratings as suggested by the (dutch) site.
 * 10 - Subliem!
 * 9 - Uitstekend
 * 8 - Zeer goed
 * 7 - Bovengemiddeld
 * 6 - Goed, maar niet bijzonder
 * 5 - Matig
 * 4 - Zwak
 * 3 - Zeer zwak
 * 2 - Bijzonder zwak
 * 1 - Waarom is deze strip ooit gemaakt?
 */
public class ImportCollection {

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
    /** A full page contains 25 rows. */
    private static final int MAX_PAGE_SIZE = 25;

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

    @Nullable
    private final Bookshelf mWishListBookshelf;

    private final SearchEngineConfig mSEConfig;

    /**
     * Constructor.
     *
     * @param userId            as extracted from the auth Cookie.
     * @param wishListBookshelf mapped bookshelf
     */
    ImportCollection(@NonNull final String userId,
                     @Nullable final Bookshelf wishListBookshelf) {
        mUserId = userId;
        mWishListBookshelf = wishListBookshelf;

        mSEConfig = SearchEngineRegistry.getInstance().getByEngineId(SearchSites.STRIP_INFO_BE);
        mJsoupLoader = new JsoupLoader();
    }

    /**
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return list with book-data Bundles
     *
     * @throws IOException on failure
     */
    @SuppressLint("DefaultLocale")
    @WorkerThread
    @NonNull
    public ArrayList<Bundle> fetch(@NonNull final Context context,
                                   @NonNull final ProgressListener progressListener)
            throws IOException {

        final ArrayList<Bundle> collection = new ArrayList<>();

        final Function<String, Optional<TerminatorConnection>> conCreator = (String u) -> {
            try {
                final TerminatorConnection con = new TerminatorConnection(u)
                        .setConnectTimeout(mSEConfig.getConnectTimeoutInMs())
                        .setReadTimeout(mSEConfig.getReadTimeoutInMs())
                        .setThrottler(mSEConfig.getThrottler());

                return Optional.of(con);
            } catch (@NonNull final IOException ignore) {
                return Optional.empty();
            }
        };

        Document document;
        List<Bundle> pageList;
        int page = 0;
        int maxPages = -1;
        do {
            pageList = null;
            page++;
            progressListener.publishProgress(1, context.getString(
                    R.string.progress_msg_loading_page, page));

            final String url = mSEConfig.getSiteUrl()
                               + String.format(URL_MY_BOOKS, mUserId, page, mFlags);
            try {
                document = mJsoupLoader.loadDocument(context, url, conCreator);

            } catch (@NonNull final FileNotFoundException e1) {
                document = null;
            }

            if (document != null) {
                final Element root = document.getElementById("collectionContent");
                if (root != null) {
                    if (page == 1) {
                        final Element last = root.select("div.pagination > a").last();
                        if (last != null) {
                            try {
                                maxPages = Integer.parseInt(last.text());
                                progressListener.setMaxPos(maxPages);
                                progressListener.setIndeterminate(false);
                                progressListener.publishProgress(0, null);

                            } catch (@NonNull final NumberFormatException e) {
                                // ignore; we'll just stay indeterminate
                            }
                        }
                    }

                    pageList = parsePage(root);
                    collection.addAll(pageList);
                }
            }

        } while (
            // any error ?
                document != null && pageList != null
                // If we have a page number (we should), is this the last page?
                && !(maxPages > 0 && maxPages == page)
                // fail safe: if we don't have a page number, but if the user
                // has an exact multitude of 25 books, we'll fetch one page to many.
                && pageList.size() == MAX_PAGE_SIZE);

        return collection;
    }

    /**
     * Parse a "collectionContent" section consisting of up to 25 book rows.
     *
     * @param root of the section to parse
     *
     * @return list with book data bundles for the page; can be empty
     */
    @NonNull
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

                    cData.putLong(DBKeys.KEY_ESID_STRIP_INFO_BE, externalId);

                    final CollectionParser form = new CollectionRowParser(externalId, collectionId);
                    form.setWishListBookshelf(mWishListBookshelf);
                    form.parse(row, cData);
                }
            } catch (@NonNull final NumberFormatException | IOException ignore) {
                // Make sure we don't return partial data
                cData.clear();
            }
        }
    }
}
