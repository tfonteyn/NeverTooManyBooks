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
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

class AmazonHtmlHandler
        extends JsoupBase {

    private static final String PRODUCT_SUFFIX_URL = "/gp/product/%1$s";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_AMZ";
    private static final String TAG = "AmazonHtmlHandler";
    /**
     * Parse "some text (some more text)" into "some text" and "some more text".
     * <p>
     * We want a "some text" that does not START with a bracket!
     * <p>
     * Gollancz (18 Mar. 2010)
     * Gollancz; First Thus edition (18 Mar. 2010)
     */
    private static final Pattern PUBLISHER_PATTERN =
            Pattern.compile("([^(]+.*)"
                            + "\\s*"
                            + "\\("
                            + /* */ "(.*)"
                            + "\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AUTHOR_TYPE_PATTERN =
            Pattern.compile("\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PAGES_PATTERN = Pattern.compile("pages", Pattern.LITERAL);

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Series for this book. */
    @NonNull
    private final ArrayList<Series> mSeries = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();

    @NonNull
    private final Context mLocalizedAppContext;
    @NonNull
    private final SearchEngine mSearchEngine;

    /**
     * Constructor.
     * @param localizedAppContext Localised application context
     * @param searchEngine        the SearchEngine
     */
    AmazonHtmlHandler(@NonNull final Context localizedAppContext,
                      @NonNull final SearchEngine searchEngine) {
        super();
        mLocalizedAppContext = localizedAppContext;
        mSearchEngine = searchEngine;
    }

    /**
     * Constructor used for testing.
     *
     * @param localizedAppContext Localised application context
     * @param searchEngine        the SearchEngine
     * @param doc the JSoup Document.
     */
    @VisibleForTesting
    AmazonHtmlHandler(@NonNull final Context localizedAppContext,
                      @NonNull final SearchEngine searchEngine,
                      @NonNull final Document doc) {
        super(doc);
        mLocalizedAppContext = localizedAppContext;
        mSearchEngine = searchEngine;
    }

    @NonNull
    @WorkerThread
    Bundle fetchByNativeId(@NonNull final String nativeId,
                           final boolean[] fetchThumbnail,
                           @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        return fetch(AmazonSearchEngine.getBaseURL(mLocalizedAppContext)
                     + String.format(PRODUCT_SUFFIX_URL, nativeId),
                     fetchThumbnail, bookData);
    }

    @NonNull
    @WorkerThread
    private Bundle fetch(@NonNull final String path,
                         @NonNull final boolean[] fetchThumbnail,
                         @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        if (loadPage(mLocalizedAppContext, path) == null) {
            return bookData;
        }

        return parseDoc(fetchThumbnail, bookData);
    }

    @NonNull
    @VisibleForTesting
    Bundle parseDoc(@NonNull final boolean[] fetchThumbnail,
                    @NonNull final Bundle bookData)
            throws SocketTimeoutException {

        // This is WEIRD...
        // Unless we do this seemingly needless select, the next select (for the title)
        // will return null.
        // When run in JUnit, this call is NOT needed.
        // Whats different? -> the Java JDK!
        Element dummy = mDoc.selectFirst("div#booksTitle");

        Element titleElement = mDoc.selectFirst("span#productTitle");
        if (titleElement == null) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.d(TAG, "no title?");
            }
            return bookData;
        }

        String title = titleElement.text().trim();
        bookData.putString(DBDefinitions.KEY_TITLE, title);

        Element price = mDoc.selectFirst("span.offer-price");
        if (price != null) {
            Money money = new Money(mSearchEngine.getLocale(mLocalizedAppContext), price.text());
            if (money.getCurrency() != null) {
                bookData.putDouble(DBDefinitions.KEY_PRICE_LISTED, money.doubleValue());
                bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY, money.getCurrency());
            } else {
                bookData.putString(DBDefinitions.KEY_PRICE_LISTED, price.text());
            }
        }

        Elements authorSpans = mDoc.select("div#bylineInfo > span.author");
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
                    Author author = Author.fromString(a.text());

                    Element typeElement = span.selectFirst("span.contribution");
                    if (typeElement != null) {
                        String data = typeElement.text();
                        Matcher matcher = AUTHOR_TYPE_PATTERN.matcher(data);
                        if (matcher.find()) {
                            data = matcher.group(1);
                        }
                        if (data != null) {
                            switch (data) {
                                case "Author":
                                    author.addType(Author.TYPE_WRITER);
                                    break;

                                case "Illustrator":
                                    author.addType(Author.TYPE_ARTIST);
                                    break;

                                case "Introduction":
                                    author.addType(Author.TYPE_INTRODUCTION);
                                    break;

                                default:
                                    if (BuildConfig.DEBUG /* always */) {
                                        Logger.d(TAG, "type=" + data);
                                    }
                                    break;
                            }
                        }
                    }
                    mAuthors.add(author);
                }
            }
        }

        Elements lis = mDoc
                .select("div#detail_bullets_id > table > tbody > tr > td > div > ul > li");
        for (Element li : lis) {
            String label = li.child(0).text().trim();
            label = label.substring(0, label.length() - 1).trim();

            String data = li.childNode(1).toString().trim();
            switch (label) {
                case "ISBN-13":
                    bookData.putString(DBDefinitions.KEY_ISBN, data);
                    break;

                case "ISBN-10":
                    if (!bookData.containsKey(DBDefinitions.KEY_ISBN)) {
                        bookData.putString(DBDefinitions.KEY_ISBN, data);
                    }
                    break;

                case "Hardcover":
                case "Paperback":
                    // french
                case "Broché":
                    bookData.putString(DBDefinitions.KEY_FORMAT, label);
                    bookData.putString(DBDefinitions.KEY_PAGES,
                                       PAGES_PATTERN.matcher(data)
                                                    .replaceAll(Matcher.quoteReplacement(""))
                                                    .trim());
                    break;

                case "Language":
                    // french
                case "Langue":
                    // the language is a 'DisplayName' so convert to iso first.
                    data = LanguageUtils.getISO3FromDisplayName(mLocalizedAppContext, data);
                    bookData.putString(DBDefinitions.KEY_LANGUAGE, data);
                    break;

                case "Publisher":
                    // french
                case "Editeur": {
                    boolean addedPublisher = false;
                    Matcher matcher = PUBLISHER_PATTERN.matcher(data);
                    if (matcher.find()) {
                        String pubName = matcher.group(1);
                        String pubDate = matcher.group(2);
                        if (pubName != null) {
                            if (pubName.contains(";")) {
                                pubName = pubName.split(";")[0];
                            }
                            Publisher publisher = Publisher.fromString(pubName.trim());
                            mPublishers.add(publisher);
                            addedPublisher = true;
                        }
                        if (pubDate != null) {
                            bookData.putString(DBDefinitions.KEY_DATE_PUBLISHED, pubDate.trim());
                        }
                    }

                    if (!addedPublisher) {
                        Publisher publisher = Publisher.fromString(data);
                        mPublishers.add(publisher);
                    }
                    break;
                }

                case "Product Dimensions":
                case "Average Customer Review":
                case "Amazon Bestsellers Rank":
                    // french
                case "Dimensions du produit":
                case "Moyenne des commentaires client":
                case "Classement des meilleures ventes d'Amazon":
                    break;

                default:
                    if (BuildConfig.DEBUG /* always */) {
                        Logger.d(TAG, "label=" + label);
                    }
                    break;
            }
        }

        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY, mPublishers);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, mSeries);
        }

        // optional fetch of the cover.
        if (fetchThumbnail[0]) {
            Element coverElement = mDoc.selectFirst("img#imgBlkFront");

            String imageUrl;
            try {
                // data-a-dynamic-image = {"https://...":[327,499]	,"https://...":[227,346]}
                String tmp = coverElement.attr("data-a-dynamic-image");
                JSONObject json = new JSONObject(tmp);
                // just grab the first key
                imageUrl = json.keys().next();

            } catch (@NonNull final JSONException e) {
                // the src attribute contains a low quality picture in base64 format.
                String srcUrl = coverElement.attr("src");
                // annoying... the url seems to start with a \n. Cut it off.
                if (srcUrl.startsWith("\n")) {
                    srcUrl = srcUrl.substring(1);
                }
                imageUrl = srcUrl;
            }

            String name = bookData.getString(DBDefinitions.KEY_ISBN, "");
            if (name.isEmpty()) {
                // just use something...
                name = String.valueOf(System.currentTimeMillis());
            }
            name += FILENAME_SUFFIX;
            // Fetch the actual image
            String fileSpec = ImageUtils.saveImage(mLocalizedAppContext, imageUrl, name);
            if (fileSpec != null) {
                ArrayList<String> imageList =
                        bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                if (imageList == null) {
                    imageList = new ArrayList<>();
                }
                imageList.add(fileSpec);
                bookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
            }
        }
        return bookData;
    }
}
