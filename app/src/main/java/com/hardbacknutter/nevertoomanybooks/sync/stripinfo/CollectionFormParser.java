/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.Context;
import android.net.Uri;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpPost;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Handles the userdata FORM from the individual <strong>book side ajax panel</strong>.
 * <p>
 * Wraps the {@link CollectionParser} handling all ajax calls before delegating the actual parsing.
 */
public class CollectionFormParser {

    /** Main page form checkbox name-attr. Suffixed with the collection-id. */
    private static final String ROW_FF_OWNED = "stripCollectieInBezit-";
    /** Main page form checkbox name-attr. Suffixed with the collection-id. */
    private static final String ROW_FF_READ = "stripCollectieGelezen-";
    /** Main page form checkbox name-attr. Suffixed with the collection-id. */
    private static final String ROW_FF_WISHLIST = "stripCollectieInWishlist-";

    /** Side panel form name-attr. */
    private static final String SIDE_FF_AMOUNT = "aantal";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_DATE_ACQUIRED = "aankoopDatum";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_DIGITAL = "digitaal";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_EDITION = "druk";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_LOCATION = "locatie";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_PERSONAL_NOTES = "opmerking";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_PRICE_PAID = "aankoopPrijs";
    /** Side panel form name-attr. */
    private static final String SIDE_FF_RATING = "score";

    /** Hidden field on the side panel form name-attr. */
    private static final String SIDE_FF_FORM_MODE = "mode";
    /** Hidden field with the book id (the site/external id). */
    private static final String SIDE_FF_STRIP_ID = "stripId";
    /** Hidden field with the id linking your collection data with the actual book. */
    private static final String SIDE_FF_STRIP_COLLECTIE_ID = "stripCollectieId";

    @NonNull
    private final FutureHttpPost<Document> futureHttpPost;
    @NonNull
    private final String postUrl;

    @NonNull
    private final CollectionParser formParser;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param bookshelfMapper mapper for the wishlist/owned flags
     */
    @AnyThread
    public CollectionFormParser(@NonNull final Context context,
                                @NonNull final BookshelfMapper bookshelfMapper) {

        final SearchEngineConfig config = EngineId.StripInfoBe.requireConfig();

        postUrl = config.getHostUrl(context) + StripInfoSearchEngine.COLLECTION_FORM_URL;

        futureHttpPost = new FutureHttpPost<>(R.string.site_stripinfo_be);
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs(context))
                      .setReadTimeout(config.getReadTimeoutInMs(context))
                      .setThrottler(config.getThrottler())
                      .setRequestProperty(HttpConstants.CONTENT_TYPE,
                                          HttpConstants.CONTENT_TYPE_FORM_URL_ENCODED);

        formParser = new CollectionParser(context, bookshelfMapper);
    }

    /**
     * Parse the form ('root') and put the results into the Book.
     *
     * @param root         Element to parse
     * @param externalId   website book id
     * @param collectionId website book collection-id
     * @param book         to store the results in
     *
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    public void parse(@NonNull final Element root,
                      @IntRange(from = 1) final long externalId,
                      @IntRange(from = 1) final long collectionId,
                      @NonNull final Book book)
            throws IOException,
                   StorageException {

        // These come from the main page
        formParser.parseOwnedFlag(root, ROW_FF_OWNED + externalId, book);
        formParser.parseReadFlag(root, ROW_FF_READ + externalId, book);
        formParser.parseWishListFlag(root, ROW_FF_WISHLIST + externalId, book);

        // The other fields come from an ajax fetched side-panel
        final String postBody = new Uri.Builder()
                .appendQueryParameter(SIDE_FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(SIDE_FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(SIDE_FF_FORM_MODE, "detail")
                // no "frmName" used here
                .build()
                .getEncodedQuery();

        //noinspection DataFlowIssue
        final Document response = Objects.requireNonNull(
                futureHttpPost.post(postUrl, postBody, bis -> {
                    try {
                        return Jsoup.parse(bis, null, postUrl);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));

        formParser.parseAmount(response, SIDE_FF_AMOUNT, book);
        formParser.parseDateAcquired(response, SIDE_FF_DATE_ACQUIRED, book);
        formParser.parseDigitalFlag(response, SIDE_FF_DIGITAL, book);
        formParser.parseEdition(response, SIDE_FF_EDITION, book);
        formParser.parseLocation(response, SIDE_FF_LOCATION, book);
        formParser.parseNotes(response, SIDE_FF_PERSONAL_NOTES, book);
        formParser.parsePricePaid(response, SIDE_FF_PRICE_PAID, book);
        formParser.parseRating(response, SIDE_FF_RATING, book);

        // Add as last one in case of errors thrown
        book.putLong(DBKey.STRIP_INFO_COLL_ID, collectionId);
    }

    public void cancel() {
        futureHttpPost.cancel();
    }
}
