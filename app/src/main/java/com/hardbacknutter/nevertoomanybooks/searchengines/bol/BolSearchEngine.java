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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpForbiddenException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.bol.com">www.bol.com</a>
 * <p>
 * All genres; Dutch and many other languages.
 * <p>
 * This site is a company based in The Netherlands and is <strong>NOT</strong>> related to
 * the german website <a href="https://www.bol.de">www.bol.de</a>.
 * The latter is a brand name of <a href="https://www.thalia.de">www.thalia.de</a>.
 * <p>
 * Accessing bol.com can be done using 4 different suffix combinations
 * <pre>
 *      https://www.bol.com/be/nl/
 *      https://www.bol.com/be/fr/
 *      https://www.bol.com/be/nl/
 *      https://www.bol.com/nl/fr/
 * </pre>
 * The first suffix is the country: either Belgium (be) or The Netherlands (nl).
 * The second is the language: either Dutch (nl) or French (fr).
 * <p>
 * We support selecting the Belgian or the Netherlands site via a user setting
 * to accommodate price differences between the two countries.
 * We <strong>only</strong> access the site via the Dutch language suffix as it makes
 * no difference at all in getting results.
 * <p>
 * 2025-02-15: bol.com blocks all requests coming from outside the country (EU?)
 * and from vpn's.
 * https://www.mobileread.com/forums/showthread.php?t=139472&page=35
 * https://airvpn.org/routes/?q=https%3A%2F%2Fwww.bol.com%2F
 */
public class BolSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText,
                   SearchEngine.SearchOnSite {

    private static final String PREFERENCE_KEY = "bol";

    /** one of {"","be","nl"}. */
    static final String PK_BOL_COUNTRY = PREFERENCE_KEY + ".country";

    /** Website character encoding. */
    private static final String CHARSET = "UTF-8";

    private static final String TAG = "BolSearchEngine";

    /**
     * Search using a text-string.
     * <p>
     * param 1: the country "be" or "nl"
     * param 2: words, separated by spaces
     */
    private static final String BY_TEXT = "/%1$s/nl/s/?searchtext=%2$s";
    /**
     * Search using the ISBN.
     * <p>
     * param 1: the country "be" or "nl"
     * param 2: the isbn
     */
    private static final String BY_ISBN = "/%1$s/nl/s/?searchtext=+%2$s+";

    /**
     * The referer for an initial connect.
     * <p>
     * Param 1: the country "be" or "nl"
     */
    private static final String ROOT_REFERER = "/%s/nl/";

    /** Front-covers can be given using either of these keys. We must try both. */
    private static final List<String> FRONT_COVER_KEYS = List.of("coverImageUrl", "imageUrl");

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BolSearchEngine(@NonNull final Context appContext,
                           @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    /**
     * Called during startup to initialise the immutable/default engine configuration.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @return {@link EngineId.Builder}
     */
    @Keep
    @NonNull
    public static EngineId.Builder init() {
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_bol_com,
                                    List.of(R.string.site_description_dutch_and_more,
                                            R.string.site_description_shop),
                                    "https://www.bol.com",
                                    new Locale("nl", "NL"))
                .setPreferenceFragmentClazz(BolPreferencesFragment.class)
                .setConfig(cb -> cb
                        .setTagsToIgnore(Set.of("Boeken", "Livres"))
                        .build(SearchEngineConfig::new));
    }

    /**
     * Get the country for the website: nl or be.
     * By default, we use the country the user is in, defined as either Belgium or
     * The Netherlands+rest-of-the-world.
     * The user can set their personal preference to BE or NL in the settings.
     *
     * @return "be" or "nl"
     */
    @NonNull
    private String getCountry() {
        String country = ServiceLocator.getInstance().getSharedPreferences()
                                       .getString(PK_BOL_COUNTRY, null);
        if (country != null && !country.isEmpty()) {
            return country;
        } else {
            // Never configured, use the users actual country
            country = ServiceLocator.getInstance().getSystemLocaleList().get(0)
                                    .getCountry();
            if ("BE".equals(country)) {
                // Belgium
                return "be";
            } else {
                // The Netherlands + rest of the world.
                return "nl";
            }
        }
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // The site can display in French, but we always access the site in Dutch for now.
        // This should not cause any issue as searches show books in all languages.
        return new Locale("nl", getCountry().toUpperCase(Locale.ROOT));
    }

    /**
     * Send a HEAD request to prepare a cookie for further calls.
     *
     * @throws SearchException on any error
     */
    private void ensureCookie()
            throws SearchException {
        final FutureHttp<Boolean> httpHead = createHeadRequest();
        try {
            httpHead.head(getHostUrl(), con -> true);
        } catch (@NonNull final StorageException | IOException e) {
            throw new SearchException(getEngineId(), e);
        }
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final ISBN isbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String validIsbn = SearchEngineUtils.formatIsbn(getEngineId(), isbn);

        final String hostUrl = getHostUrl();
        final String country = getCountry();
        final String url = hostUrl + String.format(BY_ISBN, country, validIsbn);
        final Document document = loadDocument(context, url, Map.of(
                HttpConstants.REFERER, hostUrl + String.format(ROOT_REFERER, country)));

        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    /**
     * Criteria supported: ALL.
     * Code: supported.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");

        final ISBN isbn = criteria.getIsbn();
        if (isbn != null) {
            final String code = SearchEngineUtils.formatIsbn(getEngineId(), isbn);
            if (!code.isEmpty()) {
                words.add(code);
            }
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        final String hostUrl = getHostUrl();
        final String country = getCountry();
        final String url = hostUrl + String.format(BY_TEXT, country, words);
        final Document document = loadDocument(context, url, Map.of(
                HttpConstants.REFERER, hostUrl + String.format(ROOT_REFERER, country)));
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @VisibleForTesting
    @WorkerThread
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Document document,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final String aHref = String.format("a[href^=/%1$s/nl/p/]", getCountry());
        final Element urlElement = document.selectFirst(aHref);
        // urlElement will be null if there were no results.
        if (urlElement != null) {
            String url = urlElement.attr("href");
            // sanity check - it normally does NOT have the protocol/site part
            if (url.startsWith("/")) {
                url = getHostUrl() + url;
            }
            final Document redirected = loadDocument(context, url, Map.of(
                    HttpConstants.REFERER, document.location()));
            if (!isCancelled()) {
                parse(context, redirected, fetchCovers, book);
            }
        } else {
            final Element element = document.selectFirst("div.unicorn");
            if (element != null) {
                throw new SearchException(getEngineId(),
                                          new HttpForbiddenException(
                                                  getEngineId().getLabelResId(),
                                                  "unicorn", null, document.location()));
            }
        }
    }

    /**
     * Parse the downloaded {@link org.jsoup.nodes.Document} for a single Book.
     * <p>
     * We're ignoring the label "Co Auteur" and "Hoofdredacteur" on purpose.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
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

        final Element titleElement = document.selectFirst("span[data-test='title']");
        if (titleElement == null || titleElement.text().isEmpty()) {
            // well, this is unexpected...
            // This is seen when accessing the site in French and looking for
            // a Dutch (or german...) book....
            // The site simply does not list the title... anywhere! ... ouch...
            return;
        }
        book.setTitle(cleanText(titleElement));

        final Elements specs = document.select("div.specs > dl.specs__list");
        if (specs.isEmpty()) {
            return;
        }

        final Locale siteLocale = getLocale(context);
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(siteLocale, userLocales);
        final RealNumberParser ratingNumberParser = new RealNumberParser(allLocales);
        final MoneyParser moneyParser = new MoneyParser(siteLocale, allLocales);

        for (final Element specRow : specs.select("div.specs__row")) {
            final Element label = specRow.selectFirst("dt.specs__title");
            final Element value = specRow.selectFirst("dd.specs__value");
            if (label != null && value != null) {
                final String labelText = label.text();
                switch (labelText) {
                    case "Taal":
                    case "Langue": {
                        // the first occurrence uses the iso abbreviation
                        if (!book.contains(DBKey.LANGUAGE)) {
                            book.setLanguage(cleanText(value));
                        }
                        break;
                    }
                    case "Bindwijze":
                    case "Binding": {
                        if (!book.contains(DBKey.FORMAT)) {
                            book.setFormat(cleanText(value));
                        }
                        break;
                    }
                    case "Oorspronkelijke releasedatum":
                    case "Date de sortie initiale": {
                        final String text = cleanText(value);
                        // can be empty!
                        if (!text.isBlank()) {
                            addPublicationDate(context, siteLocale, text, book);
                        }
                        break;
                    }
                    case "Aantal pagina's":
                    case "Nombre de pages": {
                        if (!book.contains(DBKey.PAGES)) {
                            book.setPages(cleanText(value));
                        }
                        break;
                    }
                    case "Hoofdauteur":
                    case "Auteur principal":
                    case "Tweede Auteur":
                    case "Deuxième auteur": {
                        parseAuthor(value, AuthorRole.WRITER, book);
                        break;
                    }
                    case "Hoofdillustrator":
                    case "Illustrateur en chef":
                    case "Tweede Illustrator":
                    case "Deuxième illustrateur": {
                        parseAuthor(value, AuthorRole.ARTIST, book);
                        break;
                    }
                    case "Hoofdredacteur":
                    case "Rédacteur en chef":
                    case "Tweede Redacteur":
                    case "Deuxième rédacteur": {
                        parseAuthor(value, AuthorRole.EDITOR, book);
                        break;
                    }
                    case "Eerste Vertaler":
                    case "Tweede Vertaler":
                    case "Premier traducteur":
                    case "Deuxième traducteur": {
                        parseAuthor(value, AuthorRole.TRANSLATOR, book);
                        break;
                    }
                    case "Verteller":
                    case "Narrateur": {
                        parseAuthor(value, AuthorRole.NARRATOR, book);
                        break;
                    }
                    case "Originele titel":
                    case "Titre original": {
                        book.setTranslatedFromTitle(cleanText(value));
                        break;
                    }
                    case "Serie": {
                        // The series number is only available embedded in the title
                        // but without any specific structure to it.
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final String text = cleanName(a);
                            if (!text.isBlank()) {
                                book.add(Series.from(text));
                            }
                        }
                        break;
                    }

                    case "Hoofduitgeverij":
                    case "Editeur principal": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final String s = cleanName(a);
                            if (!s.isBlank()) {
                                book.add(Publisher.from(s));
                            }
                        }
                        break;
                    }
                    case "EAN": {
                        // useful for audiobooks
                        if (!book.contains(DBKey.ISBN)) {
                            book.setIsbn(cleanText(value));
                        }
                        break;
                    }
                    case "Categorieën":
                    case "Catégories": {
                        processTags(value, book);
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        parseDescription(document, book);
        parseRating(document, book, ratingNumberParser);
        parsePrice(document, book, moneyParser);

        if (fetchCovers[0]) {
            parseCovers(context, document, fetchCovers, book);
        }
    }

    private void parseAuthor(@NonNull final Element value,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {
        final Element a = value.selectFirst("a");
        if (a != null) {
            final String s = cleanName(a);
            if (!s.isBlank()) {
                addAuthor(Author.from(s), type, book);
            }
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {
        final Element descrElement = document.selectFirst("div.product-description");
        if (descrElement != null) {
            final String s = cleanText(descrElement);
            if (!s.isBlank()) {
                book.setDescription(s);
            }
        }
    }

    private void parseRating(@NonNull final Document document,
                             @NonNull final Book book,
                             @NonNull final RealNumberParser realNumberParser) {
        final RatingParser ratingParser = new RatingParser(realNumberParser, 5);

        final Element ratingElement = document.selectFirst("div.reviews-summary__avg-score");
        if (ratingElement != null) {
            ratingParser.parse(ratingElement.text()).ifPresent(book::setRating);
        }
    }

    private void parsePrice(@NonNull final Document document,
                            @NonNull final Book book,
                            @NonNull final MoneyParser moneyParser) {
        //TODO: if they are out of stock, this element will NOT contain a price.
        // We should get the price from the buttons on the page just above this field
        // but those button elements are not easy to parse for.
        final Element priceElement = document.selectFirst("span.promo-price");
        if (priceElement != null) {
            //noinspection OverlyBroadCatchBlock
            try {
                // <span class="promo-price" data-test="price">22
                //    <sup class="promo-price__fraction" data-test="price-fraction">99</sup>
                // </span>
                // text() will get "22 99", so add a "," as decimal separator and parse as normal
                final String priceStr = priceElement.text().replace(" ", ",");
                // The currency is not part of the string,
                // so we just parse it as a simple double and then add the EURO.
                final double price = moneyParser.getRealNumberParser().parseDouble(priceStr);
                book.putMoney(DBKey.PRICE_LISTED,
                              new Money(BigDecimal.valueOf(price), Money.EURO));

            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }

    private void processTags(@NonNull final Element value,
                             @NonNull final Book book) {
        // it's an 'ul' with 'li' each containing an 'a'
        final List<String> tagNames = value.select("a")
                                           .stream()
                                           .map(Element::text)
                                           .collect(Collectors.toList());
        setTags(tagNames, book);
    }

    /**
     * Parse the document for cover images.
     * <p>
     * Will NOT throw if the JSON objects are messed up; we just won't have an image.
     * <p>
     * Example of the text inside an "imageSlotConfig" if it's a JSONArray:
     * <pre>
     *     {@code
     * [
     *   {
     *     "type": "book-flipper",
     *     "coverImageUrl": "https://media.s-bol.com/gKE8jpMpWokY/VR6Nlz/550x766.jpg",
     *     "backImageUrl": "https://media.s-bol.com/y802R6lV9MnV/550x556.jpg",
     *     "hardcover": true,
     *     "flipBookText": "Boek omdraaien",
     *     "thickness": "medium",
     *     "m2": {
     *       "bltgiselecteditembookflippertemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.10"
     *       },
     *       "bltghselecteditembookflippertemplate0FlipBookByBook": {
     *         "bltgh": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.10.FlipBookByBook"
     *       },
     *       "bltghselecteditembookflippertemplate1FlipBookByLink": {
     *         "bltgh": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.10.FlipBookByLink"
     *       }
     *     }
     *   },
     *   {
     *     "type": "image",
     *     "imageUrl": "https://media.s-bol.com/gKE8jpMpWokY/VR6Nlz/550x766.jpg",
     *     "productTitle": "nijntjes voorleesfeest",
     *     "zoomImageUrl": "https://media.s-bol.com/gKE8jpMpWokY/VR6Nlz/861x1200.jpg",
     *     "isHighPriorityEnabled": true,
     *     "m2": {
     *       "bltgiselecteditemimagetemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.11"
     *       }
     *     }
     *   },
     *   {
     *     "type": "image",
     *     "imageUrl": "https://media.s-bol.com/y802R6lV9MnV/550x556.jpg",
     *     "productTitle": "nijntjes voorleesfeest",
     *     "zoomImageUrl": "https://media.s-bol.com/y802R6lV9MnV/1186x1200.jpg",
     *     "isHighPriorityEnabled": true,
     *     "m2": {
     *       "bltgiselecteditemimagetemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.12"
     *       }
     *     }
     *   },
     *   {
     *     "type": "video",
     *     "imageUrl": "https://media.s-bol.com/mEW048AnXmQG/550x309.jpg",
     *     "videoUrl": "/nl/rnwy/ajax/video/product?productId=9200000122271922",
     *     "srtText": "Video afspelen",
     *     "m2": {
     *       "bltghselecteditemvideotemplate0StartVideo": {
     *         "bltgh": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.13.StartVideo"
     *       },
     *       "bltgiselecteditemvideotemplate0": {
     *         "bltgi": "hhyqGFF0fyCZv8ZiGQdMGg.2_9.13"
     *       }
     *     }
     *   }
     * ]
     *     }
     * </pre>
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws StorageException The covers directory is not available
     */
    private void parseCovers(@NonNull final Context context,
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        final String isbn = book.getIsbn();

        final Element imageSlotConfig = document.selectFirst(
                "section[data-group-name='product-images'] script");
        if (imageSlotConfig != null
            && imageSlotConfig.hasAttr("data-image-slot-config")) {

            // The data of this element can contain a JSONArray or a JSONObject
            final String text = imageSlotConfig.data().strip();
            try {
                if (text.startsWith("[") && text.endsWith("]")) {
                    // If it's a JSONArray, simply grab the first element.
                    // This will either be a "book-flipper" with both front- and back-cover
                    // in the keys "coverImageUrl" and backImageUrl";
                    // or an "image" (or "video") with the front-cover in the key "imageUrl"
                    final JSONArray objects = new JSONArray(text);
                    final JSONObject currentItem = objects.optJSONObject(0);
                    if (currentItem != null) {
                        parseCovers(context, currentItem, isbn, fetchCovers, book);
                    }
                } else {
                    // TEST: This 'else' branch can likely be removed.
                    final JSONObject imageSlotSlider = new JSONObject(text)
                            .optJSONObject("imageSlotSlider");
                    if (imageSlotSlider != null) {
                        final JSONObject currentItem = imageSlotSlider.optJSONObject("currentItem");
                        if (currentItem != null) {
                            parseCovers(context, currentItem, isbn, fetchCovers, book);
                        }
                    }
                }
            } catch (@NonNull final JSONException e) {
                // Log it so we can extend the above check if needed.
                // There is more than one way of listing images...
                LoggerFactory.getLogger().w(TAG, e, "text=`" + text + "`");
            }
        }
    }

    private void parseCovers(@NonNull final Context context,
                             @NonNull final JSONObject currentItem,
                             @NonNull final String bookId,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        // The site uses several possible keys, loop until found or exhausted
        for (final String key : FRONT_COVER_KEYS) {
            final String coverUrl = currentItem.optString(key);
            if (!coverUrl.isEmpty()) {
                final Optional<String> oFileSpec = saveImage(context, coverUrl, null, bookId, 0,
                                                             null);
                if (oFileSpec.isPresent()) {
                    CoverFileSpecArray.setFileSpec(book, 0, oFileSpec.get());
                    // only attempt to get the back-cover if we got a front-cover
                    // and (obv.) if we want one.
                    if (fetchCovers[1]) {
                        final String url = currentItem.optString("backImageUrl");
                        if (!url.isEmpty()) {
                            saveImage(context, url, null, bookId, 1, null).ifPresent(
                                    fs -> CoverFileSpecArray.setFileSpec(book, 1, fs));
                        }
                    }
                    // All done. We have a front-cover and maybe a back-cover.
                    return;
                }
            }
        }
    }

    @Override
    public boolean isShowSearchOnSiteMenu(@NonNull final Context context) {
        final String key = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU;

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false);
        } else {
            final Languages languages = ServiceLocator.getInstance().getLanguages();
            return languages.isUserLanguage(context, "nld")
                   || languages.isUserLanguage(context, "fra");
        }
    }

    @NonNull
    @Override
    public String createSearchOnSiteUrl(@NonNull final Context context,
                                        @Nullable final Author author,
                                        @Nullable final Series series) {
        if (BuildConfig.DEBUG /* always */) {
            if (author == null && series == null) {
                throw new IllegalArgumentException("both author and series are null");
            }
        }

        final StringJoiner fields = new StringJoiner(" ");

        if (author != null) {
            final String cAuthor = SearchEngineUtils
                    .encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    fields.add(URLEncoder.encode(cAuthor, CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        if (series != null) {
            final String cSeries = SearchEngineUtils
                    .encodeSearchString(series.getTitle());
            if (!cSeries.isEmpty()) {
                try {
                    fields.add(URLEncoder.encode(cSeries, CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        return getHostUrl() + String.format(BY_TEXT, getCountry(), fields);
    }
}
