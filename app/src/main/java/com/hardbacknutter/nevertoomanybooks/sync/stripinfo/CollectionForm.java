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
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.math.MathUtils;

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
import com.hardbacknutter.nevertoomanybooks.database.DBKeys;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.network.HttpUtils;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * Handles the userdata FORM from the individual book side ajax panel.
 */
public class CollectionForm
        extends CollectionBaseParser {

    private static final String FF_LOCATIE = "locatie";
    private static final String FF_OPMERKING = "opmerking";
    private static final String FF_AANKOOP_DATUM = "aankoopDatum";
    private static final String FF_SCORE = "score";
    private static final String FF_DRUK = "druk";
    private static final String FF_AANKOOP_PRIJS = "aankoopPrijs";
    private static final String FF_AANTAL = "aantal";
    private static final String FF_STRIP_ID = "stripId";
    private static final String FF_STRIP_COLLECTIE_ID = "stripCollectieId";
    private static final String FF_MODE = "mode";
    private static final String FF_FRM_NAME = "frmName";

    private static final String FORM_URL = "/ajax_collectie.php";

    @NonNull
    private final String mPostUrl;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    @AnyThread
    public CollectionForm(@NonNull final Context context,
                          @NonNull final SyncConfig config) {
        super(context, config);

        mIdLocation = FF_LOCATIE;
        mIdNotes = FF_OPMERKING;
        mIdDateAcquired = FF_AANKOOP_DATUM;
        mIdRating = FF_SCORE;
        mIdEdition = FF_DRUK;
        mIdPricePaid = FF_AANKOOP_PRIJS;
        mIdAmount = FF_AANTAL;

        mPostUrl = SearchEngineRegistry.getInstance()
                                       .getByEngineId(SearchSites.STRIP_INFO_BE)
                                       .getSiteUrl() + FORM_URL;
    }

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

        final Document form = getForm("detail", externalId, collectionId);
        parseDetails(form, destBundle);

        // Add as last one in case of errors thrown
        destBundle.putLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID, collectionId);
    }


    /**
     * Set or reset the flag/checkbox 'In Bezit'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book to use
     *
     * @throws IOException              on any failure
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void setOwned(@NonNull final Book book)
            throws IOException, IllegalArgumentException {

        final boolean owned = book.getBoolean(DBKeys.KEY_STRIP_INFO_BE_OWNED);

        setBooleanByMode(book, owned ? "inBezit" : "notInBezit");
    }

    /**
     * Set or reset the flag/checkbox 'Gelezen'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book to use
     *
     * @throws IOException              on any failure
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void setRead(@NonNull final Book book)
            throws IOException, IllegalArgumentException {

        final boolean read = book.getBoolean(DBKeys.KEY_READ);

        setBooleanByMode(book, read ? "gelezen" : "notGelezen");
    }

    /**
     * Set or reset the flag/checkbox 'In verlanglijst'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book to use
     *
     * @throws IOException              on any failure
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void setWanted(@NonNull final Book book)
            throws IOException, IllegalArgumentException {

        final boolean wanted = book.getBoolean(DBKeys.KEY_STRIP_INFO_BE_WANTED);

        setBooleanByMode(book, wanted ? "inWishlist" : "notInWishlist");
    }

    @WorkerThread
    private void setBooleanByMode(@NonNull final Book book,
                                  @NonNull final String mode)
            throws IOException, IllegalArgumentException {

        final long externalId = book.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId == 0");
        }

        long collectionId = book.getLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID);
        if (collectionId == 0) {
            final String postBody = new Uri.Builder()
                    .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                    .appendQueryParameter(FF_STRIP_COLLECTIE_ID, "")
                    .appendQueryParameter(FF_MODE, mode)
                    .build()
                    .getEncodedQuery();
            //noinspection ConstantConditions
            final Document responseForm = doPost(postBody);
            collectionId = mJSoupHelper.getInt(responseForm, FF_STRIP_COLLECTIE_ID);
            book.putLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID, collectionId);

            book.setStage(EntityStage.Stage.Dirty);

        } else {
            final String postBody = new Uri.Builder()
                    .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                    .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                    .appendQueryParameter(FF_MODE, mode)
                    .build()
                    .getEncodedQuery();
            //noinspection ConstantConditions
            doPost(postBody);
        }
    }


    @WorkerThread
    public void setRating(@NonNull final Book book)
            throws IOException, IllegalArgumentException {

        final long externalId = book.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId == 0");
        }

        final long collectionId = book.getLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID);
        if (collectionId == 0) {
            throw new IllegalArgumentException("collectionId == 0");
        }

        final String postBody = new Uri.Builder()
                .appendQueryParameter(FF_SCORE, ratingToSite(book))

                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FF_MODE, "form")
                .appendQueryParameter(FF_FRM_NAME, "collScore")
                .build()
                .getEncodedQuery();
        //noinspection ConstantConditions
        doPost(postBody);
    }

    /**
     * If not added to the collection (as defined by the site) before, the book
     * will be posted to the site as "Owned" and
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book to send
     *
     * @throws IOException              on any failure
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void send(@NonNull final Book book)
            throws IOException, IllegalArgumentException {

        final long externalId = book.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId == 0");
        }

        long collectionId = book.getLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID);
        if (collectionId == 0) {
            // Flag the book as 'owned' which will give it a collection-id.
            setOwned(book);
            collectionId = book.getLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID);
            // sanity check
            if (collectionId == 0) {
                throw new IllegalArgumentException("collectionId == 0");
            }
        }

        final Uri.Builder builder = new Uri.Builder();

        builder.appendQueryParameter(FF_SCORE, ratingToSite(book));

        String dateAcquired = book.getString(DBKeys.KEY_DATE_ACQUIRED);
        if (dateAcquired.length() == 10) {
            // convert from ISO {@code "YYYY-MM-DD"} to "DD/MM/YYYY"
            dateAcquired = dateAcquired.substring(8, 10)
                           + '/' + dateAcquired.substring(5, 7)
                           + '/' + dateAcquired.substring(0, 4);
        }
        builder.appendQueryParameter(FF_AANKOOP_DATUM, dateAcquired);

        // The site does not store a currency; it's hardcoded/supposed to be EURO.
        final Money paid = book.getMoney(DBKeys.KEY_PRICE_PAID);
        builder.appendQueryParameter(FF_AANKOOP_PRIJS,
                                     paid != null ? String.valueOf(paid.toEuro()) : "");

        // The site only supports numbers 1..x (and changes an empty string into a "1")
        // so we either put "1" for first-edition, or "2" for a reprint.
        builder.appendQueryParameter(FF_DRUK, book.isBitSet(DBKeys.KEY_EDITION_BITMASK,
                                                            Book.Edition.FIRST) ? "1" : "2");

        // we're only supporting 1 copy and the site does not allow 0 or an empty string.
        builder.appendQueryParameter(FF_AANTAL, "1");

        builder.appendQueryParameter(FF_LOCATIE, book.getString(DBKeys.KEY_LOCATION));
        builder.appendQueryParameter(FF_OPMERKING, book.getString(DBKeys.KEY_PRIVATE_NOTES));

        final String postBody = builder
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FF_MODE, "form")
                .appendQueryParameter(FF_FRM_NAME, "collDetail")
                .build()
                .getEncodedQuery();
        //noinspection ConstantConditions
        doPost(postBody);
    }

    @AnyThread
    private String ratingToSite(@NonNull final Book book) {
        return String.valueOf(MathUtils.clamp(book.getFloat(DBKeys.KEY_RATING) * 2, 0, 10));
    }

    @WorkerThread
    public void delete(@NonNull final Book book)
            throws IOException, IllegalArgumentException {

        final long externalId = book.getLong(DBKeys.KEY_ESID_STRIP_INFO_BE);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId == 0");
        }

        final long collectionId = book.getLong(DBKeys.KEY_STRIP_INFO_BE_COLL_ID);
        if (collectionId == 0) {
            throw new IllegalArgumentException("collectionId == 0");
        }

        // We first get the delete-form to make sure the server still has our book
        // (and to mimic the browser work flow).
        final Document form = getForm("delete", externalId, collectionId);
        if (externalId == mJSoupHelper.getInt(form, FF_STRIP_ID)
            && collectionId == mJSoupHelper.getInt(form, FF_STRIP_COLLECTIE_ID)) {

            final String postBody = new Uri.Builder()
                    .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                    .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                    .appendQueryParameter(FF_MODE, "deleteConfirmation")
                    .appendQueryParameter(FF_FRM_NAME, "collDelete")
                    .build()
                    .getEncodedQuery();

            //TODO: should parse the response to check delete went ok.
            //noinspection ConstantConditions
            doPost(postBody);
        }
    }

    @SuppressLint("DefaultLocale")
    @NonNull
    @WorkerThread
    private Document getForm(@NonNull final String mode,
                             @IntRange(from = 1) final long externalId,
                             @IntRange(from = 1) final long collectionId)
            throws IOException {

        final String postBody = new Uri.Builder()
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FF_MODE, mode)
                // no "frmName" used here
                .build()
                .getEncodedQuery();

        //noinspection ConstantConditions
        return doPost(postBody);
    }

    /**
     * Post the FORM to the remote server. Does initial parsing of the response HTML.
     *
     * @param postBody to send
     *
     * @return the JSoup parsed Document
     *
     * @throws IOException on any failure
     */
    @NonNull
    @WorkerThread
    private Document doPost(@NonNull final String postBody)
            throws IOException {

        StripInfoSearchEngine.THROTTLER.waitUntilRequestAllowed();

        HttpURLConnection request = null;
        try {
            request = (HttpURLConnection) new URL(mPostUrl).openConnection();
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
                return Jsoup.parse(bis, null, mPostUrl);
            }
        } finally {
            if (request != null) {
                request.disconnect();
            }
        }
    }
}
