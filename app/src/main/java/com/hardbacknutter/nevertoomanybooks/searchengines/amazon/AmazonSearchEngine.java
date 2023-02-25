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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.sync.AuthorTypeMapper;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Money;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
 * <p>
 * Should really implement the Amazon API.
 * <a href="https://docs.aws.amazon.com/en_pv/AWSECommerceService/latest/DG/becomingAssociate.html">
 * becomingAssociate</a>
 * <p>
 * Implementing SearchEngine.ByText using
 * <pre>
 * "https://www.amazon.co.uk/s/ref=sr_adv_b&search-alias=stripbooks"
 *      + "&unfiltered=1"
 *      + "&__mk_en_GB=ÅMÅZÕÑ"
 * </pre>
 * FAILED due to amazon blocking these kind of request with captcha's.
 * They seem to increasingly block any type of robot access.
 */
public class AmazonSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByBarcode,
                   SearchEngine.CoverByIsbn {

    /** Preferences - Type: {@code String}. */
    public static final String PK_HOST_URL = EngineId.Amazon.getPreferenceKey()
                                             + Prefs.pk_suffix_host_url;
    /** Website character encoding. */
    static final String CHARSET = "UTF-8";
    /** Log tag. */
    private static final String TAG = "AmazonSearchEngine";

    /**
     * Search by ASIN. This is an absolute uri.
     * Param 1: external book ID; the ASIN/ISBN10.
     */
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
    private final AuthorTypeMapper authorTypeMapper = new AuthorTypeMapper();
    /** Parse the "x pages" string. */
    @Nullable
    private Pattern pagesPattern;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public AmazonSearchEngine(@NonNull final Context appContext,
                              @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.
        return getLocale(context, getHostUrl(context));
    }

    @NonNull
    private Book genericSearch(@NonNull final Context context,
                               @NonNull final String url,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        // FIXME: for some weird reason Amazon will now throw a 503 / SSLHandshake exception
        //  if the book is not found.
        final Document document = loadDocument(context, url, null);
        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        // Convert an ISBN13 to ISBN10 (i.e. the ASIN)
        final ISBN tmp = new ISBN(validIsbn, true);
        final String asin = tmp.isIsbn10Compat() ? tmp.asText(ISBN.Type.Isbn10) : validIsbn;

        return genericSearch(context,
                             getHostUrl(context) + String.format(BY_EXTERNAL_ID, asin),
                             fetchCovers);
    }

    @NonNull
    @Override
    public Book searchByBarcode(@NonNull final Context context,
                                @NonNull final String barcode,
                                @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        if (ASIN.isValidAsin(barcode)) {
            return genericSearch(context,
                                 getHostUrl(context) + String.format(BY_EXTERNAL_ID, barcode),
                                 fetchCovers);
        } else {
            // not supported
            return new Book();
        }
    }

    @Nullable
    @Override
    public String searchCoverByIsbn(@NonNull final Context context,
                                    @NonNull final String validIsbn,
                                    @IntRange(from = 0, to = 1) final int cIdx,
                                    @Nullable final Size size)
            throws StorageException, SearchException, CredentialsException {

        final String url = getHostUrl(context) + String.format(BY_EXTERNAL_ID, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            final ArrayList<String> imageList = parseCovers(document, validIsbn, 0);
            if (!imageList.isEmpty()) {
                // let the system resolve any path variations
                return new File(imageList.get(0)).getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     *
     * @throws StorageException     on storage related failures
     * @throws CredentialsException on authentication/login failures
     *                              This should only occur if the engine calls/relies on
     *                              secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // This is WEIRD...
        // Unless we do this seemingly needless select, the next select (for the title)
        // will return null.
        // When run in JUnit, this call is NOT needed.
        // Whats different? -> the Java JDK!
        final Element unused = document.selectFirst("div#booksTitle");

        final Element titleElement = document.selectFirst("span#productTitle");
        if (titleElement == null) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, "parse", "no title?");
            }
            return;
        }

        final String title = titleElement.text().trim();
        book.putString(DBKey.TITLE, title);

        final Locale siteLocale = getLocale(context, document.location().split("/")[2]);

        final List<Locale> localeList = LocaleListUtils.asList(context, siteLocale);

        parsePrice(document, book, localeList);
        parseAuthors(document, book, siteLocale);

        if (isCancelled()) {
            return;
        }

        parseDetails(context, document, book, siteLocale);
        parseASIN(document, book);

        checkForSeriesNameInTitle(book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            final ArrayList<String> list = parseCovers(document, isbn, 0);
            if (!list.isEmpty()) {
                book.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
    }

    private void parsePrice(@NonNull final Document document,
                            @NonNull final Book book,
                            @NonNull final List<Locale> localeList) {
        final Element price = document.selectFirst("span.offer-price");
        if (price != null) {
            final Money money = Money.parse(localeList, price.text());
            if (money != null) {
                book.putMoney(DBKey.PRICE_LISTED, money);
            } else {
                // parsing failed, store the string as-is;
                // no separate currency!
                book.putString(DBKey.PRICE_LISTED, price.text());
            }
        }
    }

    private void parseASIN(@NonNull final Document document,
                           @NonNull final Book book) {
        // <form method="post" id="addToCart"
        //<input type="hidden" id="ASIN" name="ASIN" value="0752853694">
        final Element addToCart = document.getElementById("addToCart");
        if (addToCart != null) {
            final Element asinElement = addToCart.selectFirst("input#ASIN");
            if (asinElement != null) {
                final String asin = asinElement.attr("value");
                if (!asin.isEmpty()) {
                    book.putString(DBKey.SID_ASIN, asin);
                }
            }
        }
    }

    /**
     * @param context    Current context
     * @param document   to parse
     * @param book       to update
     * @param siteLocale to use for case manipulation
     */
    private void parseDetails(@NonNull final Context context,
                              @NonNull final Document document,
                              @NonNull final Book book,
                              @NonNull final Locale siteLocale) {
        final Elements lis = document
                .select("div#detail_bullets_id > table > tbody > tr > td > div > ul > li");
        for (final Element li : lis) {
            String label = li.child(0).text().trim();
            if (label.endsWith(":")) {
                label = label.substring(0, label.length() - 1).trim();
            }

            // we used to do: String data = li.childNode(1).toString().trim();
            // but that fails when the data is spread over multiple child nodes.
            // so we now just cut out the label, and use the text itself.
            li.child(0).remove();
            final String data = li.text().trim();
            switch (label.toLowerCase(siteLocale)) {
                case "isbn-13":
                    book.putString(DBKey.BOOK_ISBN, data);
                    break;

                case "isbn-10":
                    if (!book.contains(DBKey.BOOK_ISBN)) {
                        book.putString(DBKey.BOOK_ISBN, data);
                    }
                    break;

                case "hardcover":
                case "paperback":
                case "relié":
                case "broché":
                case "taschenbuch":
                case "gebundene ausgabe":
                    book.putString(DBKey.FORMAT, label);
                    book.putString(DBKey.PAGE_COUNT, extractPages(context, data));
                    break;

                case "language":
                case "langue":
                case "sprache":
                case "taal":
                    book.putString(DBKey.LANGUAGE, data);
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
                            book.add(publisher);
                            publisherWasAdded = true;
                        }

                        final String pubDate = matcher.group(2);
                        if (pubDate != null) {
                            book.putString(DBKey.BOOK_PUBLICATION__DATE, pubDate.trim());
                        }
                    }

                    if (!publisherWasAdded) {
                        final Publisher publisher = Publisher.from(data);
                        book.add(publisher);
                    }
                    break;
                }

                case "series":
                case "collection":
                    book.add(Series.from(data));
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
                        LoggerFactory.getLogger().d(TAG, "parse", "label=" + label);
                    }
                    break;
            }
        }
    }

    /**
     * @param document   to parse
     * @param book       to update
     * @param siteLocale to use for case manipulation
     */
    private void parseAuthors(@NonNull final Document document,
                              @NonNull final Book book,
                              @NonNull final Locale siteLocale) {
        for (final Element span : document.select("div#bylineInfo > span.author")) {
            // If an author has a popup dialog linked, then it has an id with contributorNameID
            Element a = span.selectFirst("a.contributorNameID");
            if (a == null) {
                // If there is no popup, it's a simple link
                a = span.selectFirst("a.a-link-normal");
            }
            if (a != null) {
                final String href = a.attr("href");
                if (href.contains("byline")) {
                    // Warning: the french site lists author names in BOTH "given family"
                    // and "family given" formats (the latter without a comma).
                    // There does not seem to be a preference.
                    // So... we will incorrectly interpret the format "family given".
                    //FIXME: search our database twice with f/g and g/f
                    // this means parsing the 'a.text()' twice.. and french names... COMPLICATED
                    final Author author = Author.from(a.text());

                    final Element typeElement = span.selectFirst("span.contribution");
                    if (typeElement != null) {
                        String data = typeElement.text();
                        final Matcher matcher = AUTHOR_TYPE_PATTERN.matcher(data);
                        if (matcher.find()) {
                            data = matcher.group(1);
                        }

                        if (data != null) {
                            author.addType(authorTypeMapper.map(siteLocale, data));
                        }
                    }
                    book.add(author);
                }
            }
        }
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
     *
     * @param document to parse
     * @param isbn     (optional) ISBN of the book, will be used for the cover filename
     * @param cIdx     0..n image index
     *
     * @return a list with fileSpecs; can be empty
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @VisibleForTesting
    @NonNull
    private ArrayList<String> parseCovers(@NonNull final Document document,
                                          @Nullable final String isbn,
                                          @SuppressWarnings("SameParameterValue")
                                          @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        final ArrayList<String> imageList = new ArrayList<>();

        final Element coverElement = document.selectFirst("img#imgBlkFront");
        if (coverElement == null) {
            return imageList;
        }

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

        final String fileSpec = saveImage(url, isbn, cIdx, null);
        if (fileSpec != null) {
            imageList.add(fileSpec);
        }
        return imageList;
    }

    @NonNull
    private String extractPages(@NonNull final Context context,
                                @NonNull final CharSequence data) {
        if (pagesPattern == null) {
            final String baseUrl = getHostUrl(context);
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
            pagesPattern = Pattern.compile(pagesStr, Pattern.LITERAL);
        }

        return pagesPattern.matcher(data).replaceAll("").trim();
    }
}
