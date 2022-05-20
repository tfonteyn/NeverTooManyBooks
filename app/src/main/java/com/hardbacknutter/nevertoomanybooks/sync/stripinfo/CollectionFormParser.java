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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpPost;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Handles the userdata FORM from the individual book side ajax panel.
 */
public class CollectionFormParser
        extends CollectionBaseParser {

    /** Local ref for convenience. */
    private static final String FORM_URL = StripInfoSearchEngine.COLLECTION_FORM_URL;

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

    /**
     * Constructor.
     *
     * @param context Current context
     */
    @AnyThread
    public CollectionFormParser(@NonNull final Context context,
                                @NonNull final BookshelfMapper bookshelfmapper) {
        super(context, bookshelfmapper);

        idLocation = FF_LOCATIE;
        idNotes = FF_OPMERKING;
        idDateAcquired = FF_AANKOOP_DATUM;
        idRating = FF_SCORE;
        idEdition = FF_DRUK;
        idPricePaid = FF_AANKOOP_PRIJS;
        idAmount = FF_AANTAL;

        final SearchEngineConfig config = SearchEngineRegistry
                .getInstance().getByEngineId(SearchSites.STRIP_INFO_BE);

        futureHttpPost = new FutureHttpPost<>(R.string.site_stripinfo_be);
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs())
                      .setReadTimeout(config.getReadTimeoutInMs())
                      .setThrottler(StripInfoSearchEngine.THROTTLER)
                      .setRequestProperty(HttpUtils.CONTENT_TYPE,
                                          HttpUtils.CONTENT_TYPE_FORM_URL_ENCODED);
    }

    /**
     * Parse the form ('root') and put the results into the 'destBundle'.
     *
     * @param root         Element to parse
     * @param externalId   website book id
     * @param collectionId website book collection-id
     * @param destBundle   to store the results in
     *
     * @throws IOException on any failure
     */
    @WorkerThread
    public void parse(@NonNull final Element root,
                      @IntRange(from = 1) final long externalId,
                      @IntRange(from = 1) final long collectionId,
                      @NonNull final Bundle destBundle)
            throws IOException, StorageException {

        idOwned = "stripCollectieInBezit-" + externalId;
        idRead = "stripCollectieGelezen-" + externalId;
        idWanted = "stripCollectieInWishlist-" + externalId;

        parseFlags(root, destBundle);

        final String postBody = new Uri.Builder()
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FORM_MODE, "detail")
                // no "frmName" used here
                .build()
                .getEncodedQuery();

        //noinspection ConstantConditions
        final Document response = Objects.requireNonNull(
                futureHttpPost.post(FORM_URL, postBody, (bis) -> {
                    try {
                        return Jsoup.parse(bis, null, FORM_URL);
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));

        parseDetails(response, destBundle);

        // Add as last one in case of errors thrown
        destBundle.putLong(DBKey.STRIP_INFO_COLL_ID, collectionId);
    }

    public void cancel() {
        futureHttpPost.cancel();
    }
}
