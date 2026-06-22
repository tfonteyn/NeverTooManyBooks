/*
 * @Copyright 2018-2026 HardBackNutter
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

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * <strong>Used by the {@link StripInfoSearchEngine} </strong>
 * <p>
 * Handles the userdata FORM from the individual <strong>book side ajax panel</strong>.
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
    private final OkHttpClient httpClient;
    @NonNull
    private final String postUrl;

    @NonNull
    private final CollectionParser formParser;
    @Nullable
    private HttpCall httpCall;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param httpClient      to use
     * @param bookshelfMapper mapper for the wishlist/owned flags
     */
    @AnyThread
    public CollectionFormParser(@NonNull final Context context,
                                @NonNull final OkHttpClient httpClient,
                                @NonNull final BookshelfMapper bookshelfMapper) {

        this.httpClient = httpClient;

        //noinspection DataFlowIssue
        postUrl = EngineId.StripInfoBe.getConfig().getHostUrl()
                  + StripInfoSearchEngine.COLLECTION_FORM_URL;

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

        final RequestBody postBody = new FormBody.Builder()
                .add(SIDE_FF_STRIP_ID, String.valueOf(externalId))
                .add(SIDE_FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .add(SIDE_FF_FORM_MODE, "detail")
                // no "frmName" used here
                .build();

        httpCall = HttpCallFactory.create(httpClient, R.string.site_stripinfo_be, false);
        final Request request = new Request.Builder()
                .url(postUrl)
                .post(postBody)
                .header(HttpConstants.ACCEPT_ENCODING,
                        HttpConstants.ACCEPT_ENCODING_GZIP)
                .header(HttpConstants.CONNECTION,
                        HttpConstants.CONNECTION_KEEP_ALIVE)
                .build();

        final Document document = Objects.requireNonNull(
                httpCall.post(request, (response, bis) -> Jsoup.parse(bis, null, postUrl)));
        httpCall = null;

        final StripInfoCollectionData collectionData =
                book.getStripInfoCollectionData().orElseGet(StripInfoCollectionData::new);
        collectionData.setSid(externalId);
        collectionData.setCollectionId(collectionId);

        // These come from the main page
        formParser.parseOwnedFlag(root, ROW_FF_OWNED + externalId, book, collectionData);
        formParser.parseWishListFlag(root, ROW_FF_WISHLIST + externalId, book, collectionData);
        formParser.parseReadFlag(root, ROW_FF_READ + externalId, book);

        // The other fields come from an ajax fetched side-panel
        formParser.parseDigitalFlag(document, SIDE_FF_DIGITAL, book, collectionData);
        formParser.parseAmount(document, SIDE_FF_AMOUNT, collectionData);

        formParser.parseDateAcquired(document, SIDE_FF_DATE_ACQUIRED, book);
        formParser.parseEdition(document, SIDE_FF_EDITION, book);
        formParser.parseLocation(document, SIDE_FF_LOCATION, book);
        formParser.parseNotes(document, SIDE_FF_PERSONAL_NOTES, book);
        formParser.parsePricePaid(document, SIDE_FF_PRICE_PAID, book);
        formParser.parseRating(document, SIDE_FF_RATING, book);

        book.setStripInfoCollectionData(collectionData);
    }

    public void cancel() {
        synchronized (this) {
            if (httpCall != null) {
                httpCall.cancel();
            }
        }
    }
}
