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
import android.os.Bundle;

import androidx.annotation.NonNull;

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
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;

/**
 * Parse the userdata from the individual book side ajax panel.
 */
public class CollectionFormParser
        extends CollectionBaseParser {

    private static final String FORM_URL = "/ajax_collectie.php";
    private static final String MODE_DETAIL = "detail";
    private static final String MODE_DELETE = "delete";
    @NonNull
    private final SearchEngineConfig mSEConfig;

    /**
     * Constructor.
     *
     * @param externalId   the book id from the web site
     * @param collectionId the user specific book id from the web site
     */
    public CollectionFormParser(final long externalId,
                                final long collectionId) {
        super(externalId, collectionId);

        mSEConfig = SearchEngineRegistry.getInstance().getByEngineId(SearchSites.STRIP_INFO_BE);

        mIdOwned = "stripCollectieInBezit-" + mExternalId;
        mIdRead = "stripCollectieGelezen-" + mExternalId;
        mIdWanted = "stripCollectieInWishlist-" + mExternalId;

        mIdLocation = "locatie";
        mIdNotes = "opmerking";
        mIdDateAcquired = "aankoopDatum";
        mIdRating = "score";
        mIdEdition = "druk";
        mIdPricePaid = "aankoopPrijs";
        mIdAmount = "aantal";
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    private String createRequestBody(@NonNull final String mode) {
        return String.format("stripId=%1$d"
                             + "&stripCollectieId=%2$d"
                             + "&mode=%3$s",
                             mExternalId, mCollectionId, mode);
    }

    public void parse(@NonNull final Element root,
                      @NonNull final Bundle destBundle)
            throws IOException {

        parseFlags(root, destBundle);
        final Document form = doPost(createRequestBody(MODE_DETAIL));

        // <form method="POST" action="ajax_collectie.php">
        // <input type="text" name="score" id="score" placeholder="Score" value="3.00" />
        // <input type="text" name="aankoopDatum" id="aankoopDatum" value="01/05/1992" />
        // <input type="text" name="aankoopPrijs" id="aankoopPrijs" value="30.00" />
        // <input type="text" name="druk" id="druk" value="20" />
        // <input type="text" name="aantal" id="aantal" value="1" />
        // <input type="text" name="locatie" id="locatie" value="home" />
        // <textarea name="opmerking" id="opmerking">testing</textarea>
        // <input type="hidden" name="stripId" id="stripId" value="153809" />
        // <input type="hidden" name="stripCollectieId" id="stripCollectieId" value="2544201" />
        // <input type="hidden" name="mode" id="mode" value="form" />
        // <input type="submit" name="save" id="save" value="Aanpassingen opslaan" />
        // <input type="hidden" name="frmName" id="frmName" value="collDetail" />
        // </form>

        parseDetails(form, destBundle);
    }

    public boolean delete()
            throws IOException {
        // We do this to make sure the server still has our book.
        final Document form = doPost(createRequestBody(MODE_DELETE));

        // <form method="POST" action="ajax_collectie.php">
        // <input type="hidden" name="stripId" id="stripId" value="21919" />
        // <input type="hidden" name="stripCollectieId" id="stripCollectieId" value="2545502" />
        // <input type="hidden" name="mode" id="mode" value="deleteConfirmation" />
        // <input type="submit" name="save" id="save" value="Ja, ik ben zeker" />
        // <input type="hidden" name="frmName" id="frmName" value="collDelete" />
        // </form>

        final int stripId = mJSoupHelper.getInt(form, "stripId");
        final int stripCollectieId = mJSoupHelper.getInt(form, "stripCollectieId");
        if (mExternalId == stripId && mCollectionId == stripCollectieId) {
            @SuppressLint("DefaultLocale")
            final String postBody =
                    String.format("stripId=%1$d"
                                  + "&stripCollectieId=%2$d"
                                  + "&mode=deleteConfirmation"
                                  + "&frmName=collDelete",
                                  mExternalId, mCollectionId);
            doPost(postBody);
            return true;

        } else {
            return false;
        }
    }

    /**
     * Post the FORM from the remote server. Does initial parsing of the response HTML.
     *
     * @param postBody to send
     *
     * @throws IOException on any failure
     */
    @NonNull
    private Document doPost(@NonNull final String postBody)
            throws IOException {

        final String url = mSEConfig.getSiteUrl() + FORM_URL;

        StripInfoSearchEngine.THROTTLER.waitUntilRequestAllowed();

        HttpURLConnection request = null;
        try {
            request = (HttpURLConnection) new URL(url).openConnection();
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
                return Jsoup.parse(bis, null, url);
            }
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }
    }
}
