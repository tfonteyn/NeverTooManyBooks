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

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;

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

    /**
     * Constructor.
     *
     * @param context Current context
     */
    @AnyThread
    public CollectionFormParser(@NonNull final Context context,
                                @NonNull final Bookshelfmapper bookshelfmapper) {
        super(context, bookshelfmapper);

        mIdLocation = FF_LOCATIE;
        mIdNotes = FF_OPMERKING;
        mIdDateAcquired = FF_AANKOOP_DATUM;
        mIdRating = FF_SCORE;
        mIdEdition = FF_DRUK;
        mIdPricePaid = FF_AANKOOP_PRIJS;
        mIdAmount = FF_AANTAL;
    }

    /**
     * Parse the form ('root') and put the results into the 'destBundle'.
     *
     * @param root         Element to parse
     * @param destBundle   to store the results in
     * @param externalId   website book id
     * @param collectionId website book collection-id
     *
     * @throws IOException on any failure
     */
    @WorkerThread
    public void parse(@NonNull final Element root,
                      @NonNull final Bundle destBundle,
                      @IntRange(from = 1) final long externalId,
                      @IntRange(from = 1) final long collectionId)
            throws IOException {

        mIdOwned = "stripCollectieInBezit-" + externalId;
        mIdRead = "stripCollectieGelezen-" + externalId;
        mIdWanted = "stripCollectieInWishlist-" + externalId;

        parseFlags(root, destBundle);

        Document details;

        final String postBody = new Uri.Builder()
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FORM_MODE, "detail")
                // no "frmName" used here
                .build()
                .getEncodedQuery();

        StripInfoSearchEngine.THROTTLER.waitUntilRequestAllowed();

        HttpURLConnection request = null;
        try {
            request = (HttpURLConnection) new URL(FORM_URL).openConnection();
            request.setRequestMethod(HttpUtils.POST);
            request.setRequestProperty(HttpUtils.CONTENT_TYPE,
                                       HttpUtils.CONTENT_TYPE_FORM_URL_ENCODED);
            request.setDoOutput(true);

            // explicit connect for clarity
            request.connect();

            try (OutputStream os = request.getOutputStream();
                 Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 Writer writer = new BufferedWriter(osw)) {
                writer.write(postBody);
                writer.flush();
            }

            HttpUtils.checkResponseCode(request, R.string.site_stripinfo_be);

            try (InputStream is = request.getInputStream();
                 BufferedInputStream bis = new BufferedInputStream(is)) {
                details = Jsoup.parse(bis, null, FORM_URL);
            }
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }

        parseDetails(details, destBundle);

        // Add as last one in case of errors thrown
        destBundle.putLong(DBKey.KEY_STRIP_INFO_COLL_ID, collectionId);
    }
}
