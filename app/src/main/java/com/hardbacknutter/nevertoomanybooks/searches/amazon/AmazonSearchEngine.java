/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searches.amazon;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.covers.ImageFileInfo;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
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
 * <p>
 * Should really implement the Amazon API.
 * https://docs.aws.amazon.com/en_pv/AWSECommerceService/latest/DG/becomingAssociate.html
 */
@SearchEngine.Configuration(
        id = SearchSites.AMAZON,
        nameResId = R.string.site_amazon,
        url = "https://www.amazon.com",
        prefKey = AmazonSearchEngine.PREF_KEY,
//        // ENHANCE: support ASIN
//        domainKey = DBDefinitions.KEY_EID_ASIN,
//        domainViewId = R.id.site_amazon,
//        domainMenuId = R.id.MENU_VIEW_BOOK_AT_AMAZON,
        filenameSuffix = "AMZ"
)
public class AmazonSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.CoverByIsbn {

    /** Preferences prefix. */
    static final String PREF_KEY = "amazon";
    /** Type: {@code String}. */
    @VisibleForTesting
    public static final String PREFS_HOST_URL = PREF_KEY + ".host.url";

    /** Log tag. */
    private static final String TAG = "AmazonSearchEngine";
    /** Website character encoding. */
    private static final String UTF_8 = "UTF-8";
    /**
     * The search url.
     *
     * <ul>Fields that can be added to the /gp URL
     *      <li>&field-isbn</li>
     *      <li>&field-author</li>
     *      <li>&field-title</li>
     *      <li>&field-publisher</li>
     *      <li>&field-keywords</li>
     * </ul>
     */
    private static final String SEARCH_SUFFIX = "/gp/search?index=books";
    /** Param 1: external book ID; the ASIN/ISBN. */
    private static final String BY_EXTERNAL_ID = "/gp/product/%1$s";
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
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext Application context
     */
    public AmazonSearchEngine(@NonNull final Context appContext) {
        super(appContext);

        final String baseUrl = getSiteUrl();
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

    @NonNull
    public static String getSiteUrl(@NonNull final Context context) {
        //noinspection ConstantConditions
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(PREFS_HOST_URL,
                           SearchEngineRegistry.getByEngineId(SearchSites.AMAZON).getSiteUrl());
    }

    /**
     * Start an intent to search for an author and/or series on the Amazon website.
     *
     * @param context Application context
     * @param author  to search for
     * @param series  to search for
     *
     * @return url
     */
    public static String createUrl(@NonNull final Context context,
                                   @Nullable final String author,
                                   @Nullable final String series) {

        final String cAuthor = cleanupOpenWebsiteString(author);
        final String cSeries = cleanupOpenWebsiteString(series);

        String fields = "";
        if (!cAuthor.isEmpty()) {
            try {
                fields += "&field-author=" + URLEncoder.encode(cAuthor, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(context, TAG, e, "Unable to add author to URL");
            }
        }

        if (!cSeries.isEmpty()) {
            try {
                fields += "&field-keywords=" + URLEncoder.encode(cSeries, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(context, TAG, e, "Unable to add series to URL");
            }
        }

        return getSiteUrl(context) + SEARCH_SUFFIX + fields.trim();
    }

    @NonNull
    private static String cleanupOpenWebsiteString(@Nullable final String search) {
        if (search == null || search.isEmpty()) {
            return "";
        }

        final StringBuilder out = new StringBuilder(search.length());
        char prev = ' ';
        for (char curr : search.toCharArray()) {
            if (Character.isLetterOrDigit(curr)) {
                out.append(curr);
                prev = curr;
            } else {
                if (!Character.isWhitespace(prev)) {
                    out.append(' ');
                }
                prev = ' ';
            }
        }
        return out.toString().trim();
    }

    /**
     * The external id for Amazon is the isbn.
     *
     * @param isbn to search for
     *
     * @return url
     */
    @NonNull
//    @Override
    public String createUrl(@NonNull final String isbn) {
        String fields = "";
        if (!isbn.isEmpty()) {
            try {
                fields += "&field-isbn=" + URLEncoder.encode(isbn, UTF_8);
            } catch (@NonNull final UnsupportedEncodingException e) {
                Logger.error(mAppContext, TAG, e, "Unable to add isbn to URL");
            }
        }

        return getSiteUrl() + SEARCH_SUFFIX + fields.trim();
    }

    @NonNull
    @Override
    public String getSiteUrl() {
        return getSiteUrl(mAppContext);
    }

    @NonNull
    @Override
    public Locale getLocale() {
        // Derive the Locale from the user configured url.
        return getLocale(getSiteUrl());
    }

    /**
     * Derive the Locale from the actual url.
     *
     * @param baseUrl to digest
     *
     * @return Locale matching the url root domain
     */
    @NonNull
    private Locale getLocale(@NonNull final String baseUrl) {

        final String root = baseUrl.substring(baseUrl.lastIndexOf('.') + 1);
        switch (root) {
            case "com":
                return Locale.US;

            case "uk":
                // country code is GB (july 2020: for now...)
                return Locale.UK;

            default:
                // other amazon sites are (should be ?) just the country code.
                final Locale locale = AppLocale.getInstance().getLocale(mAppContext, root);
                if (BuildConfig.DEBUG /* always */) {
                    Logger.d(TAG, "getLocale=" + locale);
                }
                return locale != null ? locale : Locale.US;
        }
    }

    /**
     * The external ID is the ASIN.
     * The ASIN for books is identical to the ISBN10 code.
     */
    @NonNull
//    @Override
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(url);
        if (document != null && !isCancelled()) {
            parse(document, fetchThumbnail, bookData);
        }

        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchThumbnail)
            throws IOException {

        final ISBN tmp = new ISBN(validIsbn);
        if (tmp.isIsbn10Compat()) {
            return searchByExternalId(tmp.asText(ISBN.TYPE_ISBN10), fetchThumbnail);
        } else {
            return searchByExternalId(validIsbn, fetchThumbnail);
        }
    }

    @Nullable
    @Override
    public String searchCoverImageByIsbn(@NonNull final String validIsbn,
                                         @IntRange(from = 0, to = 1) final int cIdx,
                                         @Nullable final ImageFileInfo.Size size) {
        try {
            final String url = getSiteUrl() + String.format(BY_EXTERNAL_ID, validIsbn);
            final Document document = loadDocument(url);
            if (document != null && !isCancelled()) {
                final ArrayList<String> imageList = parseCovers(document, validIsbn, 0);
                if (!imageList.isEmpty()) {
                    final File downloadedFile = new File(imageList.get(0));
                    // let the system resolve any path variations
                    final File destination = new File(downloadedFile.getAbsolutePath());
                    FileUtils.renameOrThrow(downloadedFile, destination);
                    return destination.getAbsolutePath();
                }
            }
        } catch (@NonNull final IOException ignore) {
            // ignore
        }
        return null;
    }

    @Override
    @VisibleForTesting
    public void parse(@NonNull final Document document,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData)
            throws IOException {
        super.parse(document, fetchThumbnail, bookData);

        final Locale siteLocale = getLocale(document.location().split("/")[2]);

        // This is WEIRD...
        // Unless we do this seemingly needless select, the next select (for the title)
        // will return null.
        // When run in JUnit, this call is NOT needed.
        // Whats different? -> the Java JDK!
        //noinspection unused
        final Element unused = document.selectFirst("div#booksTitle");

        final Element titleElement = document.selectFirst("span#productTitle");
        if (titleElement == null) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.d(TAG, "no title?");
            }
            return;
        }

        final String title = titleElement.text().trim();
        bookData.putString(DBDefinitions.KEY_TITLE, title);

        final Element price = document.selectFirst("span.offer-price");
        if (price != null) {
            Money money = new Money(siteLocale, price.text());
            if (money.getCurrency() != null) {
                bookData.putDouble(DBDefinitions.KEY_PRICE_LISTED, money.doubleValue());
                bookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY, money.getCurrency());
            } else {
                bookData.putString(DBDefinitions.KEY_PRICE_LISTED, price.text());
            }
        }

        final Elements authorSpans = document.select("div#bylineInfo > span.author");
        for (Element span : authorSpans) {
            // If an author has a popup dialog linked, then it has an id with contributorNameID
            Element a = span.selectFirst("a.contributorNameID");
            if (a == null) {
                // If there is no popup, it's a simple link
                a = span.selectFirst("a.a-link-normal");
            }
            if (a != null) {
                final String href = a.attr("href");
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

        if (isCancelled()) {
            return;
        }

        final Elements lis = document
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
                    data = Languages
                            .getInstance().getISO3FromDisplayName(mAppContext, siteLocale, data);
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


        // <form method="post" id="addToCart"
        //<input type="hidden" id="ASIN" name="ASIN" value="0752853694">
        final Element addToCart = document.getElementById("addToCart");
        if (addToCart != null) {
            final Element asinElement = addToCart.selectFirst("input#ASIN");
            if (asinElement != null) {
                final String asin = asinElement.attr("value");
                if (asin != null) {
                    bookData.putString(DBDefinitions.KEY_EID_ASIN, asin);
                }
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

        checkForSeriesNameInTitle(bookData);

        if (isCancelled()) {
            return;
        }

        if (fetchThumbnail[0]) {
            final String isbn = bookData.getString(DBDefinitions.KEY_ISBN);
            final ArrayList<String> imageList = parseCovers(document, isbn, 0);
            if (!imageList.isEmpty()) {
                bookData.putStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[0], imageList);
            }
        }
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
     *
     * @param document to parse
     * @param isbn     (optional) ISBN of the book, will be used for the cover filename
     * @param cIdx     0..n image index
     */
    @WorkerThread
    @VisibleForTesting
    @NonNull
    private ArrayList<String> parseCovers(@NonNull final Document document,
                                          @Nullable final String isbn,
                                          @SuppressWarnings("SameParameterValue")
                                          @IntRange(from = 0, to = 1) final int cIdx) {

        final Element coverElement = document.selectFirst("img#imgBlkFront");
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

        final ArrayList<String> imageList = new ArrayList<>();

        final String tmpName = createFilename(isbn, cIdx, null);
        final String fileSpec = saveImage(url, tmpName);
        if (fileSpec != null) {
            imageList.add(fileSpec);
        }
        return imageList;
    }
}
