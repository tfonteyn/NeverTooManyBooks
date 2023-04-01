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
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.math.MathUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpPost;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.JSoupHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Handles the userdata FORM from the individual book side ajax panel.
 * <p>
 * URGENT: hook up the delete / setRead ... with BoB/ShowBook etc...
 */
public class CollectionFormUploader {

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

    /** Delegate common Element handling. */
    private final JSoupHelper jSoupHelper = new JSoupHelper();

    @NonNull
    private final FutureHttpPost<Document> futureHttpPost;
    @NonNull
    private final String postUrl;

    @NonNull
    private final RealNumberParser realNumberParser;
    @NonNull
    private final MoneyParser moneyParser;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    @AnyThread
    CollectionFormUploader(@NonNull final Context context) {
        final SearchEngineConfig config = EngineId.StripInfoBe.requireConfig();

        postUrl = config.getHostUrl(context) + StripInfoSearchEngine.COLLECTION_FORM_URL;

        futureHttpPost = new FutureHttpPost<>(R.string.site_stripinfo_be);
        futureHttpPost.setConnectTimeout(config.getConnectTimeoutInMs(context))
                      .setReadTimeout(config.getReadTimeoutInMs(context))
                      .setThrottler(config.getThrottler())
                      .setRequestProperty(HttpConstants.CONTENT_TYPE,
                                          HttpConstants.CONTENT_TYPE_FORM_URL_ENCODED);

        final Locale siteLocale = config.getEngineId().getDefaultLocale();
        final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
        realNumberParser = new RealNumberParser(locales);
        moneyParser = new MoneyParser(siteLocale, realNumberParser);
    }

    /**
     * Set or reset the flag/checkbox 'In Bezit'.
     * If not added to the collection (as defined by the site) before, the book
     * will be updated with the new collection-id, and set to {@link EntityStage.Stage#Dirty}.
     * It's up to the caller to update the book in the local database.
     *
     * @param book to use
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    public void setOwned(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final boolean owned = book.getBoolean(DBKey.STRIP_INFO_OWNED);

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
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    public void setRead(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final boolean read = book.getBoolean(DBKey.READ__BOOL);

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
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    public void setWanted(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final boolean wanted = book.getBoolean(DBKey.STRIP_INFO_WANTED);

        setBooleanByMode(book, wanted ? "inWishlist" : "notInWishlist");
    }

    @WorkerThread
    public void setRating(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final long externalId = book.getLong(DBKey.SID_STRIP_INFO);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId == 0");
        }

        final long collectionId = book.getLong(DBKey.STRIP_INFO_COLL_ID);
        if (collectionId == 0) {
            throw new IllegalArgumentException("collectionId == 0");
        }

        final String postBody = new Uri.Builder()
                .appendQueryParameter(FF_SCORE, ratingToSite(book))
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FORM_MODE, MODE_SEND_FORM)
                .appendQueryParameter(FORM_NAME, "collScore")
                .build()
                .getEncodedQuery();
        //noinspection ConstantConditions
        doPost(postBody);
    }

    @AnyThread
    @NonNull
    private String ratingToSite(@NonNull final Book book) {
        return String.valueOf(MathUtils.clamp(book.getFloat(DBKey.RATING, realNumberParser) * 2,
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

        final long externalId = book.getLong(DBKey.SID_STRIP_INFO);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId == 0");
        }

        long collectionId = book.getLong(DBKey.STRIP_INFO_COLL_ID);
        if (collectionId == 0) {
            // Flag the book as 'owned' which will give it a collection-id.
            setOwned(book);
            collectionId = book.getLong(DBKey.STRIP_INFO_COLL_ID);
            // sanity check
            if (collectionId == 0) {
                throw new IllegalArgumentException("collectionId == 0");
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
            final Object v = book.get(DBKey.PRICE_PAID, realNumberParser);
            if (v != null) {
                if (book.contains(DBKey.PRICE_PAID_CURRENCY)) {
                    final Money money = moneyParser
                            .parse(String.valueOf(v), book.getString(DBKey.PRICE_PAID_CURRENCY));
                    if (money != null) {
                        // The site does not store a currency; it's hardcoded/supposed to be EURO.
                        // So always convert it to EURO and than send it.
                        builder.appendQueryParameter(FF_AANKOOP_PRIJS,
                                                     String.valueOf(money.toEuro()));
                    } else {
                        // just send the value string as-is
                        builder.appendQueryParameter(FF_AANKOOP_PRIJS, String.valueOf(v));
                    }
                } else {
                    // just send the value string as-is
                    builder.appendQueryParameter(FF_AANKOOP_PRIJS, String.valueOf(v));
                }
            }
        }

        // The site only supports numbers 1..x (and changes an empty string into a "1")
        // so we either put "1" for first-edition, or "2" for a reprint.
        final boolean isFirst = (book.getLong(
                DBKey.EDITION__BITMASK) & Book.Edition.FIRST) != 0;
        builder.appendQueryParameter(FF_DRUK, isFirst ? "1" : "2");

        // we're only supporting 1 copy and the site does not allow 0 or an empty string.
        builder.appendQueryParameter(FF_AANTAL, "1");

        builder.appendQueryParameter(FF_LOCATIE, book.getString(DBKey.LOCATION));
        builder.appendQueryParameter(FF_OPMERKING, book.getString(DBKey.PERSONAL_NOTES));

        final String postBody =
                builder.appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                       .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                       .appendQueryParameter(FORM_MODE, MODE_SEND_FORM)
                       .appendQueryParameter(FORM_NAME, "collDetail")
                       .build()
                       .getEncodedQuery();
        //noinspection ConstantConditions
        doPost(postBody);
    }

    @WorkerThread
    public void delete(@NonNull final Book book)
            throws IOException, IllegalArgumentException, StorageException {

        final long externalId = book.getLong(DBKey.SID_STRIP_INFO);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId=0");
        }

        final long collectionId = book.getLong(DBKey.STRIP_INFO_COLL_ID);
        if (collectionId == 0) {
            throw new IllegalArgumentException("collectionId=0");
        }

        // We first get the delete-form to make sure the server still has our book
        // (and to mimic the browser work flow).
        String postBody = new Uri.Builder()
                .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                .appendQueryParameter(FORM_MODE, "delete")
                // no "frmName" used here
                .build()
                .getEncodedQuery();

        //noinspection ConstantConditions
        final Document form = doPost(postBody);

        if (externalId == jSoupHelper.getInt(form, FF_STRIP_ID)
            && collectionId == jSoupHelper.getInt(form, FF_STRIP_COLLECTIE_ID)) {

            postBody = new Uri.Builder()
                    .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                    .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                    .appendQueryParameter(FORM_MODE, "deleteConfirmation")
                    .appendQueryParameter(FORM_NAME, "collDelete")
                    .build()
                    .getEncodedQuery();

            //TODO: should parse the response to check delete went ok.
            //noinspection ConstantConditions
            doPost(postBody);
        }
    }

    /**
     * Helper to remove all StripInfo related fields from the Book.
     *
     * @param book to remove from
     */
    void removeFields(@NonNull final Book book) {
        book.remove(DBKey.SID_STRIP_INFO);
        book.remove(DBKey.STRIP_INFO_AMOUNT);
        book.remove(DBKey.STRIP_INFO_COLL_ID);
        book.remove(DBKey.STRIP_INFO_OWNED);
        book.remove(DBKey.STRIP_INFO_WANTED);
        book.remove(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC);
    }

    /**
     * Send a form with a single boolean flag.
     *
     * @param book to use
     * @param mode one of the 3 flags, in either 'on'  or 'off' format.
     *
     * @throws IOException              on generic/other IO failures
     * @throws IllegalArgumentException if the external id was not present
     * @throws StorageException         on storage related failures
     */
    @WorkerThread
    private void setBooleanByMode(@NonNull final Book book,
                                  @NonNull final String mode)
            throws IOException, IllegalArgumentException, StorageException {

        final long externalId = book.getLong(DBKey.SID_STRIP_INFO);
        if (externalId == 0) {
            throw new IllegalArgumentException("externalId=0");
        }

        long collectionId = book.getLong(DBKey.STRIP_INFO_COLL_ID);
        if (collectionId == 0) {
            final String postBody = new Uri.Builder()
                    .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                    .appendQueryParameter(FF_STRIP_COLLECTIE_ID, "")
                    .appendQueryParameter(FORM_MODE, mode)
                    .build()
                    .getEncodedQuery();
            //noinspection ConstantConditions
            final Document responseForm = doPost(postBody);

            collectionId = jSoupHelper.getInt(responseForm, FF_STRIP_COLLECTIE_ID);
            book.putLong(DBKey.STRIP_INFO_COLL_ID, collectionId);

            book.setStage(EntityStage.Stage.Dirty);

        } else {
            final String postBody = new Uri.Builder()
                    .appendQueryParameter(FF_STRIP_ID, String.valueOf(externalId))
                    .appendQueryParameter(FF_STRIP_COLLECTIE_ID, String.valueOf(collectionId))
                    .appendQueryParameter(FORM_MODE, mode)
                    .build()
                    .getEncodedQuery();
            //noinspection ConstantConditions
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
     * @throws IOException      on generic/other IO failures
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Document doPost(@NonNull final String postBody)
            throws IOException, StorageException {

        return Objects.requireNonNull(futureHttpPost.post(postUrl, postBody, bis -> {
            try {
                return Jsoup.parse(bis, null, postUrl);
            } catch (@NonNull final IOException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    public void cancel() {
        futureHttpPost.cancel();
    }
}
