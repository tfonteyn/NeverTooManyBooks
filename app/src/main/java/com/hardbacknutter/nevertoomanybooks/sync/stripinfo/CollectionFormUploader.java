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
import android.net.Uri;
import android.os.LocaleList;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.math.MathUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;

import com.hardbacknutter.nevertoomanybooks.core.network.HttpCall;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.network.HttpCallFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupParserHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * <strong>Used by the synchronisation logic, i.e. the {@link StripInfoWriter}.</strong>
 * <p>
 * Handles the userdata FORM from the individual book side ajax panel.
 */
class CollectionFormUploader {

    /**
     * Send a FORM to the site. Used to either upload the full set of
     * collection details, or just upload the 'score' (rating).
     */
    private static final String MODE_SEND_FORM = "form";

    private static final String FORM_MODE = "mode";
    private static final String FORM_NAME = "frmName";

    private static final String FF_LOCATIE = "locatie";
    private static final String FF_OPMERKING = "opmerking";
    private static final String FF_AANKOOP_DATUM = "aankoopDatum";
    private static final String FF_SCORE = "score";
    private static final String FF_DRUK = "druk";
    private static final String FF_AANKOOP_PRIJS = "aankoopPrijs";
    private static final String FF_AANTAL = "aantal";

    private static final String FF_STRIP_ID = "stripId";
    private static final String FF_STRIP_COLLECTIE_ID = "stripCollectieId";
    private static final String ERROR_COLLECTION_ID_0 = "collectionId == 0";

    /** Delegate common Element handling. */
    private final JSoupParserHelper jSoupParserHelper = new JSoupParserHelper();

    @NonNull
    private final String postUrl;

    @NonNull
    private final RealNumberParser ratingNumberParser;
    @NonNull
    private final MoneyParser moneyParser;
    @NonNull
    private final OkHttpClient httpClient;
    @Nullable
    private HttpCall httpCall;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    @AnyThread
    CollectionFormUploader(@NonNull final Context context) {

        final StripInfoSearchEngine searchEngine =
                (StripInfoSearchEngine) EngineId.StripInfoBe.createSearchEngine(context);
        httpClient = searchEngine.createHttpClient();

        postUrl = StripInfoSearchEngine.COLLECTION_FORM_URL;

        final Locale siteLocale = EngineId.StripInfoBe.getDefaultLocale();
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(siteLocale, userLocales);
        ratingNumberParser = new RealNumberParser(allLocales);
        moneyParser = new MoneyParser(siteLocale, allLocales);
    }

    /**
     * IMPLEMENTED, BUT UNLIKELY TO BE EXPOSED TO THE USER UI.
     * Used internally.
     * <p>
     * Set or reset the flag/checkbox 'In Bezit'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book           to use
     * @param collectionData data
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void setOwned(@NonNull final Book book,
                         @NonNull final StripInfoCollectionData collectionData)
            throws IOException, IllegalArgumentException {

        setBooleanByMode(book, collectionData,
                         collectionData.isOwned() ? "inBezit" : "notInBezit");
    }


    /**
     * IMPLEMENTED, BUT UNLIKELY TO BE EXPOSED TO THE USER UI.
     * <p>
     * Set or reset the flag/checkbox 'Gelezen'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book           to use
     * @param collectionData data
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void setRead(@NonNull final Book book,
                        @NonNull final StripInfoCollectionData collectionData)
            throws IOException, IllegalArgumentException {

        setBooleanByMode(book, collectionData,
                         book.isRead() ? "gelezen" : "notGelezen");
    }

    /**
     * IMPLEMENTED, BUT UNLIKELY TO BE EXPOSED TO THE USER UI.
     * <p>
     * Set or reset the flag/checkbox 'In verlanglijst'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book           to use
     * @param collectionData data
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    public void setWanted(@NonNull final Book book,
                          @NonNull final StripInfoCollectionData collectionData)
            throws IOException, IllegalArgumentException {

        setBooleanByMode(book, collectionData,
                         collectionData.isWanted() ? "inWishlist" : "notInWishlist");
    }

    /**
     * IMPLEMENTED, BUT UNLIKELY TO BE EXPOSED TO THE USER UI.
     * <p>
     * Post a request to the site to set the rating of the given book.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book           to set
     * @param collectionData data
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    public void setRating(@NonNull final Book book,
                          @NonNull final StripInfoCollectionData collectionData)
            throws IOException, IllegalArgumentException, StorageException {

        final String externalId = book.requireIdentifierValue(Identifier.SID_STRIP_INFO);

        final long collectionId = collectionData.getCollectionId();
        if (collectionId == 0) {
            //TEST: can we send the rating form with FF_STRIP_COLLECTIE_ID="" ?
            setOwned(book, collectionData);
        }

        final RequestBody postBody = new FormBody.Builder()
                .add(FF_SCORE, ratingToSite(book))
                .add(FF_STRIP_ID, externalId)
                .add(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .add(FORM_MODE, MODE_SEND_FORM)
                .add(FORM_NAME, "collScore")
                .build();
        doPost(postBody);
    }

    @AnyThread
    @NonNull
    private String ratingToSite(@NonNull final Book book) {
        // The Book rating runs from 0.0 to 5.0; multiply by 2 for the site 1..10.
        // We clamp due to paranoia
        return String.valueOf(MathUtils.clamp(book.getRating(ratingNumberParser) * 2,
                                              0, 10));
    }


    /**
     * If not added to the collection (as defined by the site) before, the book
     * will be posted to the site as "Owned" and
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book to send
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    public void send(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final String externalId = book.requireIdentifierValue(Identifier.SID_STRIP_INFO);
        final StripInfoCollectionData collectionData =
                book.getStripInfoCollectionData().orElseGet(StripInfoCollectionData::new);

        long collectionId = collectionData.getCollectionId();
        if (collectionId == 0) {
            // Flag the book as 'owned' which will give it a collection-id.
            setOwned(book, collectionData);
            collectionId = collectionData.getCollectionId();
            // sanity check
            if (collectionId == 0) {
                throw new IllegalArgumentException(ERROR_COLLECTION_ID_0);
            }
        }

        final Uri.Builder builder = new Uri.Builder();

        builder.appendQueryParameter(FF_SCORE, ratingToSite(book));

        String dateAcquired = book.getString(DBKey.DATE_ACQUIRED);
        if (dateAcquired.length() == 10) {
            // convert from ISO {@code "YYYY-MM-DD"} to "DD/MM/YYYY"
            dateAcquired = dateAcquired.substring(8, 10)
                           + '/' + dateAcquired.substring(5, 7)
                           + '/' + dateAcquired.substring(0, 4);
        }
        builder.appendQueryParameter(FF_AANKOOP_DATUM, dateAcquired);


        if (book.contains(DBKey.PRICE_PAID)) {
            final Object v = book.get(DBKey.PRICE_PAID, moneyParser.getRealNumberParser());
            if (v != null) {
                if (v instanceof Money) {
                    // The site does not store a currency; it's hardcoded/supposed to be EURO.
                    // But the user could have entered historical data with pre-euro currencies,
                    // so we must convert to EURO before sending.
                    final Money value = ((Money) v).toEuro();
                    builder.appendQueryParameter(FF_AANKOOP_PRIJS, String.valueOf(value));
                } else {
                    // Money parsing failed. Send the value string as-is.
                    builder.appendQueryParameter(FF_AANKOOP_PRIJS, String.valueOf(v));
                }
            }
        }

        // The site only supports numbers 1..x (and changes an empty string into a "1")
        // so we either put "1" for first-edition, or "2" for anything else.
        final boolean isFirst = book.isEdition(Book.Edition.FIRST);
        builder.appendQueryParameter(FF_DRUK, isFirst ? "1" : "2");

        builder.appendQueryParameter(FF_AANTAL, String.valueOf(collectionData.getAmount()));

        builder.appendQueryParameter(FF_LOCATIE, book.getLocation());
        builder.appendQueryParameter(FF_OPMERKING, book.getNotes());

        final RequestBody postBody = new FormBody.Builder()
                .add(FF_STRIP_ID, externalId)
                .add(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .add(FORM_MODE, MODE_SEND_FORM)
                .add(FORM_NAME, "collDetail")
                .build();
        doPost(postBody);
    }

    /**
     * Post a request to the site to delete the given book from our collection.
     *
     * @param book to delete
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    public void delete(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final String externalId = book.requireIdentifierValue(Identifier.SID_STRIP_INFO);
        final StripInfoCollectionData collectionData =
                book.getStripInfoCollectionData().orElse(null);
        // Sanity check
        if (collectionData == null || collectionData.getCollectionId() == 0) {
            return;
        }

        final long collectionId = collectionData.getCollectionId();

        // We first get the delete-form to make sure the server still has our book
        // (and to mimic the browser work flow).
        RequestBody postBody;

        postBody = new FormBody.Builder()
                .add(FF_STRIP_ID, externalId)
                .add(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .add(FORM_MODE, "delete")
                // no "frmName" used here
                .build();

        final Document form = doPost(postBody);

        final OptionalLong siteExtId = jSoupParserHelper.getPositiveLong(form, FF_STRIP_ID);
        final OptionalLong siteCollId = jSoupParserHelper.getPositiveLong(form, FF_STRIP_COLLECTIE_ID);
        if (siteExtId.isPresent() && externalId.equals(String.valueOf(siteExtId.getAsLong()))
            && siteCollId.isPresent() && collectionId == siteCollId.getAsLong()) {
            postBody = new FormBody.Builder()
                    .add(FF_STRIP_ID, externalId)
                    .add(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                    .add(FORM_MODE, "deleteConfirmation")
                    .add(FORM_NAME, "collDelete")
                    .build();

            //TODO: should parse the response to check delete went ok.
            doPost(postBody);
        }
    }

    /**
     * Send a form with a single boolean flag.
     *
     * @param book           to use
     * @param collectionData to use
     * @param mode           one of the 3 flags, in either 'on'  or 'off' format.
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     */
    @WorkerThread
    private void setBooleanByMode(@NonNull final Book book,
                                  @NonNull final StripInfoCollectionData collectionData,
                                  @NonNull final String mode)
            throws IOException, IllegalArgumentException {

        final String externalId = book.requireIdentifierValue(Identifier.SID_STRIP_INFO);

        final long collectionId = collectionData.getCollectionId();
        if (collectionId == 0) {
            // Not in the collection yet, send a request to add it while setting the mode
            final RequestBody postBody = new FormBody.Builder()
                    .add(FF_STRIP_ID, externalId)
                    .add(FF_STRIP_COLLECTIE_ID, "")
                    .add(FORM_MODE, mode)
                    .build();
            final Document responseForm = doPost(postBody);

            jSoupParserHelper.getPositiveLong(responseForm, FF_STRIP_COLLECTIE_ID).ifPresent(id -> {
                collectionData.setCollectionId(id);
                book.setStage(EntityStage.Stage.Dirty);
            });
        } else {
            // Already in our collection, send a request to set the mode
            final RequestBody postBody = new FormBody.Builder()
                    .add(FF_STRIP_ID, externalId)
                    .add(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                    .add(FORM_MODE, mode)
                    .build();
            doPost(postBody);
        }
    }

    /**
     * Post the FORM to the remote server. Does initial parsing of the response HTML.
     *
     * @param postBody to send
     *
     * @return the JSoup parsed Document
     *
     * @throws IOException on generic/other IO failures
     */
    @WorkerThread
    @NonNull
    private Document doPost(@NonNull final RequestBody postBody)
            throws IOException {

        httpCall = HttpCallFactory.create(httpClient, EngineId.StripInfoBe);
        final Request request = new Request.Builder()
                .url(postUrl)
                .post(postBody)
                .header(HttpConstants.ACCEPT_ENCODING,
                        HttpConstants.ACCEPT_ENCODING_GZIP)
                .header(HttpConstants.CONNECTION,
                        HttpConstants.CONNECTION_KEEP_ALIVE)
                .build();

        return Objects.requireNonNull(httpCall.post(request, (response, bis) ->
                Jsoup.parse(bis, null, postUrl)));
    }

    /**
     * Request to cancel an ongoing post (to the site).
     */
    public void cancel() {
        synchronized (this) {
            if (httpCall != null) {
                httpCall.cancel();
            }
        }
    }
}
