/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.amazon;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBookHandlerBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

/**
 * This class supports parsing these Amazon websites:
 * www.amazon.com
 * www.amazon.co.uk
 * www.amazon.fr
 * www.amazon.de
 * www.amazon.nl
 * <p>
 * Anything failing there is a bug.
 * <p>
 * Other Amazon sites should work for basic info (e.g. title) only.
 *
 * <strong>Warning:</strong> the french site lists author names in BOTH "given family"
 * and "family given" formats (the latter without a comma). There does not seem to be a preference.
 * So... we will incorrectly interpret the format "family given".
 * FIXME: when trying to find an author.. search our database twice with f/g and g/f
 */
class AmazonHtmlHandler
        extends JsoupBookHandlerBase {

    /** Log tag. */
    private static final String TAG = "AmazonHtmlHandler";

    /** Param 1: native book ID; the ASIN/ISBN. */
    private static final String BY_NATIVE_ID = "/gp/product/%1$s";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_AMZ";

    /**
     * Parse "some text; more text (some more text)" into "some text" and "some more text".
     * <p>
     * Also: we want a "some text" that does not START with a '('.
     * <p>
     * Gollancz (18 Mar. 2010)
     * ==> "Gollancz" and "18 Mar. 2010"
     * Gollancz; First Thus edition (18 Mar. 2010)
     * ==> "Gollancz" and "18 Mar. 2010"
     * Dargaud; <b>Édition&#160;:</b> Nouvelle (21 janvier 2005)
     * ==> "Dargaud" and "21 janvier 2005"
     */
    private static final Pattern PUBLISHER_PATTERN =
            Pattern.compile("([^(;]*).*\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern AUTHOR_TYPE_PATTERN =
            Pattern.compile("\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Parse the "x pages" string. */
    private final Pattern mPagesPattern;

    /**
     * Constructor.
     *
     * @param context      Current context
     * @param searchEngine to use
     */
    AmazonHtmlHandler(@NonNull final Context context,
                      @NonNull final SearchEngine searchEngine) {
        super(context, searchEngine);

        final String baseUrl = mSearchEngine.getUrl(mContext);
        // check the domain name to determine the language of the site
        final String root = baseUrl.substring(baseUrl.lastIndexOf('.') + 1);
        final String pagesStr;
        switch (root) {
            case "de":
                pagesStr = "Seiten";
                break;

            case "nl":
                pagesStr = "pagina's";
                break;

            default:
                // English, French
                pagesStr = "pages";
                break;
        }
        mPagesPattern = Pattern.compile(pagesStr, Pattern.LITERAL);
    }

    /**
     * Constructor for mocking.
     *
     * @param context      Current context
     * @param searchEngine to use
     * @param doc          the pre-loaded Jsoup document.
     */
    @VisibleForTesting
    AmazonHtmlHandler(@NonNull final Context context,
                      @NonNull final SearchEngine searchEngine,
                      @NonNull final Document doc) {
        this(context, searchEngine);
        mDoc = doc;
    }

    @NonNull
    @WorkerThread
    public Bundle fetchByNativeId(@NonNull final String nativeId,
                                  @NonNull final boolean[] fetchThumbnail,
                                  @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        final String url = mSearchEngine.getUrl(mContext) + String.format(BY_NATIVE_ID, nativeId);

        return fetchUrl(url, fetchThumbnail, bookData);
    }

    @NonNull
    @Override
    @VisibleForTesting
    public Bundle parseDoc(@NonNull final boolean[] fetchThumbnail,
                           @NonNull final Bundle bookData) {

        final Locale siteLocale = mSearchEngine.getLocale(mContext);

        // This is WEIRD...
        // Unless we do this seemingly needless select, the next select (for the title)
        // will return null.
        // When run in JUnit, this call is NOT needed.
        // Whats different? -> the Java JDK!
        //noinspection unused,ConstantConditions
        final Element dummy = mDoc.selectFirst("div#booksTitle");

        final Element titleElement = mDoc.selectFirst("span#productTitle");
        if (titleElement == null) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.d(TAG, "no title?");
            }
            return bookData;
        }

        final String title = titleElement.text().trim();
        bookData.putString(DBDefinitions.KEY_TITLE, title);

        final Element price = mDoc.selectFirst("span.offer-price");
        if (price != null) {
            Money money = new Money(siteLocale, price.text());
            if (money.getCurrency() != null) {
                bookData.putDouble(DBDefinitions.KEY_PRICE_LISTED, money.doubleValue());
                bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY, money.getCurrency());
            } else {
                bookData.putString(DBDefinitions.KEY_PRICE_LISTED, price.text());
            }
        }

        final Elements authorSpans = mDoc.select("div#bylineInfo > span.author");
        for (Element span : authorSpans) {
            // If an author has a popup dialog linked, then it has an id with contributorNameID
            Element a = span.selectFirst("a.contributorNameID");
            if (a == null) {
                // If there is no popup, then it's a simple link
                a = span.selectFirst("a.a-link-normal");
            }
            if (a != null) {
                String href = a.attr("href");
                if (href != null && href.contains("byline")) {
                    final Author author = Author.from(a.text());

                    final Element typeElement = span.selectFirst("span.contribution");
                    if (typeElement != null) {
                        String data = typeElement.text();
                        final Matcher matcher = AUTHOR_TYPE_PATTERN.matcher(data);
                        if (matcher.find()) {
                            data = matcher.group(1);
                        }

                        if (data != null) {
                            author.addType(AuthorTypeMapper.map(siteLocale, data));
                        }
                    }
                    mAuthors.add(author);
                }
            }
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        final Elements lis = mDoc
                .select("div#detail_bullets_id > table > tbody > tr > td > div > ul > li");
        for (Element li : lis) {
            String label = li.child(0).text().trim();
            if (label.endsWith(":")) {
                label = label.substring(0, label.length() - 1).trim();
            }

            // we used to do: String data = li.childNode(1).toString().trim();
            // but that fails when the data is spread over multiple child nodes.
            // so we now just cut out the label, and use the text itself.
            li.child(0).remove();
            String data = li.text().trim();
            switch (label.toLowerCase(siteLocale)) {
                case "isbn-13":
                    bookData.putString(DBDefinitions.KEY_ISBN, data);
                    break;

                case "isbn-10":
                    if (!bookData.containsKey(DBDefinitions.KEY_ISBN)) {
                        bookData.putString(DBDefinitions.KEY_ISBN, data);
                    }
                    break;

                case "hardcover":
                case "paperback":
                case "relié":
                case "broché":
                case "taschenbuch":
                case "gebundene ausgabe":
                    bookData.putString(DBDefinitions.KEY_FORMAT, label);
                    bookData.putString(DBDefinitions.KEY_PAGES,
                                       mPagesPattern.matcher(data).replaceAll("").trim());
                    break;

                case "language":
                case "langue":
                case "sprache":
                case "taal":
                    // the language is a 'DisplayName' so convert to iso first.
                    data = LanguageUtils.getISO3FromDisplayName(mContext,
                                                                siteLocale, data);
                    bookData.putString(DBDefinitions.KEY_LANGUAGE, data);
                    break;

                case "publisher":
                case "editeur":
                case "verlag":
                case "uitgever": {
                    boolean publisherWasAdded = false;
                    final Matcher matcher = PUBLISHER_PATTERN.matcher(data);
                    if (matcher.find()) {
                        final String pubName = matcher.group(1);
                        if (pubName != null) {
                            final Publisher publisher = Publisher.from(pubName.trim());
                            mPublishers.add(publisher);
                            publisherWasAdded = true;
                        }

                        final String pubDate = matcher.group(2);
                        if (pubDate != null) {
                            bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED, pubDate.trim());
                        }
                    }

                    if (!publisherWasAdded) {
                        final Publisher publisher = Publisher.from(data);
                        mPublishers.add(publisher);
                    }
                    break;
                }

                case "series":
                case "collection":
                    mSeries.add(Series.from(data));
                    break;

                case "product dimensions":
                case "shipping weight":
                case "customer reviews":
                case "average customer review":
                case "amazon bestsellers rank":
                    // french
                case "dimensions du produit":
                case "commentaires client":
                case "moyenne des commentaires client":
                case "classement des meilleures ventes d'amazon":
                    // german
                case "größe und/oder gewicht":
                case "kundenrezensionen":
                case "amazon bestseller-rang":
                case "vom hersteller empfohlenes alter":
                case "originaltitel":
                    // dutch
                case "productafmetingen":
                case "brutogewicht (incl. verpakking)":
                case "klantenrecensies":
                case "plaats op amazon-bestsellerlijst":

                    // These labels are ignored, but listed as an indication we know them.
                    break;

                default:
                    if (BuildConfig.DEBUG /* always */) {
                        Logger.d(TAG, "label=" + label);
                    }
                    break;
            }
        }

        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
        }

        if (mSearchEngine.isCancelled()) {
            return bookData;
        }

        if (fetchThumbnail[0]) {
            parseDocForCover(bookData);
        }
        return bookData;
    }

    /**
     * Parses the downloaded {@link #mDoc} for the cover and fetches it when present.
     *
     * @param bookData Bundle to update
     */
    public void parseDocForCover(@NonNull final Bundle bookData) {
        //noinspection ConstantConditions
        final Element coverElement = mDoc.selectFirst("img#imgBlkFront");

        String url;
        try {
            // data-a-dynamic-image = {"https://...":[327,499],"https://...":[227,346]}
            final String tmp = coverElement.attr("data-a-dynamic-image");
            final JSONObject json = new JSONObject(tmp);
            // just grab the first key
            url = json.keys().next();

        } catch (@NonNull final JSONException e) {
            // the src attribute contains a low quality picture in base64 format.
            String srcUrl = coverElement.attr("src");
            // annoying... the url seems to start with a \n. Cut it off.
            if (srcUrl.startsWith("\n")) {
                srcUrl = srcUrl.substring(1);
            }
            url = srcUrl;
        }

        fetchCover(url, bookData, FILENAME_SUFFIX, 0);
    }
}
