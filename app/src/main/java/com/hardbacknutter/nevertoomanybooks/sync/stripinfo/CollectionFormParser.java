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
 * Handles the userdata FORM from the individual book side ajax panel.
 */
public class CollectionFormParser
        extends CollectionBaseParser {

    private static final String FORM_MODE = "mode";

    private static final String FF_LOCATIE = "locatie";
    private static final String FF_OPMERKING = "opmerking";
    private static final String FF_AANKOOP_DATUM = "aankoopDatum";
    private static final String FF_SCORE = "score";
    private static final String FF_DRUK = "druk";
    private static final String FF_AANKOOP_PRIJS = "aankoopPrijs";
    private static final String FF_AANTAL = "aantal";

    private static final String FF_STRIP_ID = "stripId";
    private static final String FF_STRIP_COLLECTIE_ID = "stripCollectieId";

    @NonNull
    private final FutureHttpPost<Document> futureHttpPost;
    @NonNull
    private final String postUrl;

    /**
     * Constructor.
     *
     * @param context         Current context
     * @param bookshelfMapper mapper for the wishlist/owned flags
     */
    @AnyThread
    public CollectionFormParser(@NonNull final Context context,
                                @NonNull final BookshelfMapper bookshelfMapper) {
        super(context, bookshelfMapper);

        idLocation = FF_LOCATIE;
        idNotes = FF_OPMERKING;
        idDateAcquired = FF_AANKOOP_DATUM;
        idRating = FF_SCORE;
        idEdition = FF_DRUK;
        idPricePaid = FF_AANKOOP_PRIJS;
        idAmount = FF_AANTAL;

        final SearchEngineConfig config = EngineId.StripInfoBe.requireConfig();

        postUrl = config.getHostUrl(context) + StripInfoSearchEngine.COLLECTION_FORM_URL;

        futureHttpPost = new FutureHttpPost<>(R.string.site_stripinfo_be);
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs(context))
                      .setReadTimeout(config.getReadTimeoutInMs(context))
                      .setThrottler(config.getThrottler())
                      .setRequestProperty(HttpConstants.CONTENT_TYPE,
                                          HttpConstants.CONTENT_TYPE_FORM_URL_ENCODED);
    }

    /**
     * Parse the form ('root') and put the results into the 'destBundle'.
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

        idOwned = "stripCollectieInBezit-" + externalId;
        idRead = "stripCollectieGelezen-" + externalId;
        idWanted = "stripCollectieInWishlist-" + externalId;

        parseFlags(root, book);

        final String postBody = new Uri.Builder()
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FORM_MODE, "detail")
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

        parseDetails(response, book);

        // Add as last one in case of errors thrown
        book.putLong(DBKey.STRIP_INFO_COLL_ID, collectionId);
    }

    public void cancel() {
        futureHttpPost.cancel();
    }
}
