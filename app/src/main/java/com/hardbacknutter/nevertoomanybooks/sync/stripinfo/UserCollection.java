/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.JsoupLoader;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Fetches and parses the <strong>user collection list page</strong> from the site.
 * This list is available from the site top row menu bar.
 * <p>
 * <a href="https://www.stripinfo.be/userCollection">https://www.stripinfo.be/userCollection</a>
 * <p>
 * Each book is represented as a row containing a FORM.
 * The INPUT "name" attributes start with the name of the field, a "-", and the 'collection id'
 * of the book.
 * <p>
 * This class allows visiting each page, and parsing the specific collection row.
 * These are NOT full-book data sets.
 * <p>
 * Use this class with a simple loop/fetch:
 *
 * <pre>
 *     {@code
 *          while (uc.hasMore()) {
 *              List<Bundle> page = uc.fetchPage(...);
 *              ...
 *          }
 *     }
 * </pre>
 */
public class UserCollection {

    private static final String TAG = "UserCollection";

    /**
     * The collection page only provides a link to the front cover.
     * The back cover (identifier) can only be read from the main book page.
     * IMPORTANT: we *always* parse for the cover, but *ONLY* store the url
     * in a private key. The caller should determine if the cover is actually wanted
     * (and download it) when the collection data is being processed!
     */
    static final String BKEY_FRONT_COVER_URL = TAG + ":front_cover_url";

    /** Prefix used by the site attributes. */
    private static final String ROW_ID_ATTR = "showIfInCollection-";
    private static final int ROW_ID_ATTR_LEN = ROW_ID_ATTR.length();

    /**
     * param 1: userId
     * param 2: page number
     * param 3: binary flags; see {@link #FLAGS}.
     * <p>
     * suffix: "_search_%4s" : with a search-filter... not fully sure what fields are searched.
     * => not supported for now.
     * <p>
     * The suffix "_search-" is always added by the site form; so we add it as well.
     */
    private static final String URL_MY_BOOKS = "/userCollection/index/%1$s/0/%2$d/%3$s_search-";

    /**
     * Filters.
     * <p>
     * Each flag can be:
     * <ul>
     *      <li>0: don't care</li>
     *      <li>1: Yes</li>
     *      <li>2: No</li>
     * </ul>
     * <ul>
     *      <li>1000: books I own ("in bezit")</li>
     *      <li>0100: in wishlist ("verlanglijst")</li>
     *      <li>0010: read ("gelezen")</li>
     *      <li>0001: which I rated ("met score")</li>
     * </ul>
     * <p>
     * Examples: 1020: all books I own but have not yet read. Don't care about wishlist/rating.
     * <p>
     * For now hardcoded to getting all books in the collection.
     * So we get the "owned" books; but we also get the "wanted" books.
     */
    private static final String FLAGS = "0000";

    /** A "collectionContent" section consists of up to 25 book rows. */
    private static final int COLLECTION_CONTENT_ROWS = 25;

    /** Responsible for loading and parsing the web page. */
    @NonNull
    private final JsoupLoader jsoupLoader;

    /** Internal id from the website; used in the auth Cookie and links. */
    @NonNull
    private final String userId;
    @NonNull
    private final StripInfoSearchEngine searchEngine;
    @NonNull
    private final CollectionParser rowParser;
    private int maxPages = 1;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param searchEngine    to use
     * @param userId          as extracted from the auth Cookie.
     * @param bookshelfMapper mapper for the wishlist/owned flags
     */
    @AnyThread
    UserCollection(@NonNull final Context context,
                   @NonNull final StripInfoSearchEngine searchEngine,
                   @NonNull final String userId,
                   @NonNull final BookshelfMapper bookshelfMapper) {
        this.userId = userId;
        this.searchEngine = searchEngine;
        jsoupLoader = new JsoupLoader(this.searchEngine.createFutureGetRequest(context));
        rowParser = new CollectionParser(context, bookshelfMapper);
    }

    @IntRange(from = 1)
    public int getMaxPages() {
        return maxPages;
    }

    /**
     * Fetch a single page.
     *
     * @param context          Current context
     * @param pageNr           page number to fetch: 1..
     * @param progressListener Progress and cancellation interface
     *
     * @return list with book-data Bundles
     *
     * @throws IOException     on generic/other IO failures
     * @throws SearchException on generic exceptions (wrapped) during search
     */
    @SuppressLint("DefaultLocale")
    @WorkerThread
    @NonNull
    List<Book> fetchPage(@NonNull final Context context,
                         @IntRange(from = 1) final int pageNr,
                         @NonNull final ProgressListener progressListener)
            throws SearchException, IOException {

        if (pageNr > maxPages) {
            throw new SearchException(searchEngine.getEngineId(), "Can't fetch more pages", null);
        }

        progressListener.publishProgress(1, context.getString(
                R.string.progress_msg_loading_page, pageNr));

        final String url = searchEngine.getHostUrl(context)
                           + String.format(URL_MY_BOOKS, userId, pageNr, FLAGS);

        final Document document = jsoupLoader.loadDocument(context, url, null);
        return parseDocument(document, pageNr, progressListener);
    }

    private int parseMaxPages(@NonNull final Element root,
                              @NonNull final ProgressListener progressListener)
            throws SearchException {
        final Element last = root.select("div.pagination > a").last();
        if (last != null) {
            try {
                final int count = Integer.parseInt(last.text());
                // If the last page has less books, this is to high... oh well...
                progressListener.setMaxPos(count * COLLECTION_CONTENT_ROWS);
                progressListener.setIndeterminate(false);
                progressListener.publishProgress(0, null);
                return count;

            } catch (@NonNull final NumberFormatException e) {
                throw new SearchException(searchEngine.getEngineId(),
                                          "Unable to read page number", null);
            }
        }

        throw new SearchException(searchEngine.getEngineId(),
                                  "No 'div.pagination > a' found",
                                  null);
    }

    @VisibleForTesting
    @NonNull
    List<Book> parseDocument(@NonNull final Document document,
                             final int pageNr,
                             @NonNull final ProgressListener progressListener)
            throws SearchException {
        final Element root = document.getElementById("collectionContent");
        if (root != null) {
            if (pageNr == 1) {
                // From the first page we fetch, we can parse the actual amount of pages
                maxPages = parseMaxPages(root, progressListener);
            }
            return parsePage(root);
        }

        if (BuildConfig.DEBUG /* always */) {
            if (pageNr != maxPages) {
                LoggerFactory.getLogger().d(TAG, "parseDocument",
                                            "No 'collectionContent' found for url="
                                            + document.location());
            }
        }

        // The last page did not contain 'collectionContent'.
        // Assume we reached the end.
        return List.of();
    }

    /**
     * Parse a "collectionContent".
     *
     * @param root of the section to parse
     *
     * @return list with book data bundles for the page; can be empty
     */
    @AnyThread
    @NonNull
    private List<Book> parsePage(@NonNull final Element root) {
        final List<Book> collection = new ArrayList<>();

        // showing 'progress' here is pointless as even older devices will be fast.
        for (final Element row : root.select("div.collectionRow")) {
            final Book cData = new Book();

            parseRow(row, cData);
            if (!cData.isEmpty()) {
                collection.add(cData);
            }
        }
        return collection;
    }

    @AnyThread
    private void parseRow(@NonNull final Element row,
                          @NonNull final Book cData) {
        final String idAttr = row.id();
        // sanity check, each row is normally a book.
        if (idAttr.startsWith(ROW_ID_ATTR)) {
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

                    rowParser.parseAmount(row, "aantal-" + collectionId, cData);
                    rowParser.parseDateAcquired(row, "aankoopdatum-" + collectionId, cData);
                    rowParser.parseDigitalFlag(row, "digitaal-" + collectionId, cData);
                    rowParser.parseEdition(row, "druk-" + collectionId, cData);
                    rowParser.parseLocation(row, "locatie-" + collectionId, cData);
                    rowParser.parseNotes(row, "opmerking-" + collectionId, cData);
                    rowParser.parseOwnedFlag(row, "bezit-" + collectionId, cData);
                    rowParser.parsePricePaid(row, "prijs-" + collectionId, cData);
                    rowParser.parseRating(row, "score-" + collectionId, cData);
                    rowParser.parseReadFlag(row, "gelezen-" + collectionId, cData);
                    rowParser.parseWishListFlag(row, "wishlist-" + collectionId, cData);

                    // Add as last one in case of errors thrown.
                    // The presence of this data indicates a full record was parsed successfully
                    cData.putLong(DBKey.STRIP_INFO_COLL_ID, collectionId);
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // Make sure we don't return partial data
                cData.clearData();
            }
        }
    }
}
