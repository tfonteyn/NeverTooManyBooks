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
import java.util.Currency;
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
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
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

    // Local mapping for the types in the json data
    private static final Map<String, Integer> FORMAT_MAPPING = Map.of(
            "https://schema.org/Paperback", R.string.book_format_paperback,
            "https://schema.org/EBook", R.string.book_format_ebook,
            "https://schema.org/AudiobookFormat", R.string.book_format_audiobook
    );

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
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());

        final String hostUrl = getHostUrl();
        final String country = getCountry();
        final String url = hostUrl + String.format(BY_ISBN, country, codeStr);
        final Document document = loadDocument(context, url, Map.of(
                HttpConstants.REFERER, hostUrl + String.format(ROOT_REFERER, country)));

        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            multiResult(context, productCode, document, criteria.getFetchCovers(), book);
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
                       @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            final String codeStr = productCode.getFormatted(getEngineId());
            if (!codeStr.isBlank()) {
                words.add(codeStr);
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
            multiResult(context, productCode, document, criteria.getFetchCovers(), book);
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context      Current context
     * @param searchedCode which the user searched for;
     *                     can be {@code null} if the search used different criteria
     * @param document     to parse
     * @param fetchCovers  Set array indexes to {@code true} to fetch a cover for that index.
     *                     Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book         to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @WorkerThread
    private void multiResult(@NonNull final Context context,
                             @Nullable final ProductCode searchedCode,
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final String url = parseMultiResult(document);
        if (url == null) {
            return;
        }
        final Document redirected = loadDocument(context, url, Map.of(
                HttpConstants.REFERER, document.location()));
        if (!isCancelled()) {
            parse(context, searchedCode, redirected, fetchCovers, book);
        }
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param document to parse
     *
     * @return the url to redirect to, or {@code null} if parsing failed.
     *
     * @throws SearchException oif we hit the bot-block page
     */
    @VisibleForTesting
    @Nullable
    String parseMultiResult(@NonNull final Document document)
            throws SearchException {
        final Element urlElement = document.selectFirst(
                String.format("a[href^=/%1$s/nl/p/]", getCountry()));
        if (urlElement == null) {
            // check for the bot-block page, so we can inform the user if this is the problem
            final Element element = document.selectFirst("div.unicorn");
            if (element != null) {
                throw new SearchException(getEngineId(),
                                          new HttpForbiddenException(
                                                  getEngineId().getLabelResId(),
                                                  "unicorn", null, document.location()));
            }
            return null;
        }

        String url = urlElement.attr("href");
        if (url.isBlank()) {
            return null;
        }
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        return url;
    }

    /**
     * Parse the downloaded {@link org.jsoup.nodes.Document} for a single Book.
     * <p>
     * We're ignoring the label "Co Auteur" and "Hoofdredacteur" on purpose.
     *
     * @param context      Current context
     * @param searchedCode which the user searched for;
     *                     can be {@code null} if the search used different criteria
     * @param document     to parse
     * @param fetchCovers  Set array indexes to {@code true} to fetch a cover for that index.
     *                     Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book         to update
     *
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @WorkerThread
    void parse(@NonNull final Context context,
               @Nullable final ProductCode searchedCode,
               @NonNull final Document document,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws StorageException, SearchException {

        final Element jsonElement = document.selectFirst("script[type=\"application/ld+json\"]");
        if (jsonElement != null) {
            try {
                parseJson(context, searchedCode, document, new JSONObject(jsonElement.data()),
                          fetchCovers, book);
            } catch (@NonNull final JSONException e) {
                throw new SearchException(getEngineId(), e);
            }
        } else {
            // If for whatever reason the json blob is missing, fall back to html.
            // 2026-08-06: this code path may not be up to date!
            parseHtml(context, searchedCode, document, fetchCovers, book);
        }
    }

    @VisibleForTesting
    @WorkerThread
    void parseJson(@NonNull final Context context,
                   @Nullable final ProductCode searchedCode,
                   @NonNull final Document document,
                   @NonNull final JSONObject root,
                   @NonNull final boolean[] fetchCovers,
                   @NonNull final Book book)
            throws StorageException {

        if (BuildConfig.DEBUG /* always */) {
            // can be used in tests
            book.putString("PARSER", "JSON");
        }

        book.setTitle(root.optString("name"));
        book.setDescription(root.optString("description"));
        book.setLanguage(root.optString("inLanguage"));

        final JSONObject jsonAuthor = root.optJSONObject("author");
        if (jsonAuthor != null) {
            // The author in json is the primary author only.
            // We would need to parse the html for others and roles.
            // Add directly, don't use the parserHelper addAuthor method
            final String name = jsonAuthor.optString("name");
            if (!name.isBlank()) {
                book.add(Author.from(name));
            }
        }


        final JSONObject jsonPublisher = root.optJSONObject("publisher");
        if (jsonPublisher != null) {
            // same as authors,
            // We would need to parse the html for others and roles.
            final String name = jsonPublisher.optString("name");
            if (!name.isBlank()) {
                book.add(Publisher.from(name));
            }
        }
        final JSONObject jsonRating = root.optJSONObject("aggregateRating");
        if (jsonRating != null) {
            final float ratingValue = jsonRating.optFloat("ratingValue");
            if (!Float.isNaN(ratingValue)) {
                book.setRating(ratingValue);
            }
        }
        final JSONArray works = root.optJSONArray("workExample");
        if (works != null) {
            parseJsonWorks(context, searchedCode, works, book);
        }

        final JSONArray genre = root.optJSONArray("genre");
        if (genre != null) {
            parseJsonTags(genre, book);
        }

        if (fetchCovers[0]) {
            parseCoversFromJson(context, root, document, fetchCovers, book);
        }
    }

    private void parseJsonWorks(@NonNull final Context context,
                                @Nullable final ProductCode searchedCode,
                                @NonNull final JSONArray works,
                                @NonNull final Book book)
            throws JSONException {
        // we're using "get" and throw when there is an issue
        int index = 0;
        if (searchedCode != null) {
            for (int i = 0; i < works.length(); i++) {
                final String text = works.getJSONObject(i).optString("isbn");
                if (!text.isBlank()) {
                    final ProductCode code = ISBN.parse(text);
                    if (code.equals(searchedCode)) {
                        // found it
                        index = i;
                        break;
                    }
                }
            }
        }
        // use the first one if we did not find a match
        final JSONObject work = works.getJSONObject(index);

        // There is a "name" which is a repeat of the title. Ignoring this one.

        // back to using "opt"!
        book.setRawProductCode(work.optString("isbn"));
        book.setPages(work.optString("numberOfPages"));
        // the format is ISO
        book.setPublicationDate(work.optString("datePublished"));

        final String description = work.optString("@description");
        if (!description.isBlank()) {
            // overwrite generic description with edition specific description
            book.setDescription(description);
        }

        // do NOT use "@type", as the value "Book" is used for both book and ebook :/
        final String bookFormat = work.optString("bookFormat");
        final Integer formatStrId = FORMAT_MAPPING.get(bookFormat);
        if (formatStrId != null) {
            book.setFormat(context.getString(formatStrId));
        } else if (bookFormat.startsWith("https://schema.org/")) {
            book.setFormat(bookFormat.substring(19));
        }

        final JSONObject offers = work.optJSONObject("offers");
        if (offers != null) {
            parseJsonPrice(offers, book);
        } else {
            final JSONObject pa = work.optJSONObject("potentialAction");
            if (pa != null) {
                final JSONObject eao = pa.optJSONObject("expectsAcceptanceOf");
                if (eao != null) {
                    parseJsonPrice(eao, book);
                }
            }
        }
    }

    private void parseJsonPrice(@NonNull final JSONObject o,
                                @NonNull final Book book) {
        // its a String!
        final String rawPrice = o.optString("price");
        final BigDecimal price = new BigDecimal(rawPrice);
        final String priceCurrency = o.optString("priceCurrency", "EUR");
        book.setPriceListed(new Money(price, Currency.getInstance(priceCurrency)));
    }

    private void parseJsonTags(@NonNull final JSONArray genre,
                               @NonNull final Book book) {
        final List<String> tagNames = genre.toList()
                                           .stream()
                                           // they are simple Strings
                                           .map(String::valueOf)
                                           .collect(Collectors.toList());
        parserHelper.setTags(tagNames, book);
    }

    @VisibleForTesting
    @WorkerThread
    void parseHtml(@NonNull final Context context,
                   @Nullable final ProductCode searchedCode,
                   @NonNull final Document document,
                   @NonNull final boolean[] fetchCovers,
                   @NonNull final Book book)
            throws StorageException {

        if (BuildConfig.DEBUG /* always */) {
            // can be used in tests
            book.putString("PARSER", "HTML");
        }

        final Element titleElement = document
                .selectFirst("div.md\\:col-span-2 > div > div.mb-3 > h1.mb-3 > span");
        if (titleElement == null || titleElement.text().isEmpty()) {
            return;
        }
        book.setTitle(SearchEngineUtils.cleanText(titleElement));

        final Element heading = document.selectFirst(
                "div[data-testid=collapsible-content] > h2:containsOwn(Productspecificaties), "
                + "div[data-testid=collapsible-content] > h2:containsOwn(Spécifications produit)");
        if (heading == null) {
            return;
        }

        final Element parentDiv = heading.parent();
        if (parentDiv == null) {
            return;
        }
        final Elements flexRows = parentDiv.select("div.flex.py-2");
        if (flexRows.isEmpty()) {
            return;
        }

        final Locale siteLocale = getLocale(context);
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(siteLocale, userLocales);
        final RealNumberParser ratingNumberParser = new RealNumberParser(allLocales);

        for (final Element row : flexRows) {


            final Element label = row.selectFirst("div.w-1\\/2.pl-4");
            final Element value = row.selectFirst("div.w-1\\/2.px-4");
            if (label != null && value != null) {
                final String labelText = label.text();
                switch (labelText) {
                    case "Taal":
                    case "Langue": {
                        // the first occurrence uses the iso abbreviation
                        if (!book.contains(DBKey.LANGUAGE)) {
                            book.setLanguage(SearchEngineUtils.cleanText(value));
                        }
                        break;
                    }
                    case "Uitvoering":
                    case "Version": {
                        if (!book.contains(DBKey.FORMAT)) {
                            final String format = SearchEngineUtils.cleanText(value);
                            // economy/cheaper edition -> it's a paperback!
                            if ("Voordeeleditie".equals(format)
                                || "Édition spéciale".equals(format)) {
                                book.setFormat(context.getString(R.string.book_format_paperback));
                            }
                            book.setFormat(format);
                        }
                        break;
                    }
                    case "Oorspronkelijke releasedatum":
                    case "Date de sortie initiale": {
                        final String text = SearchEngineUtils.cleanText(value);
                        // can be empty!
                        if (!text.isBlank()) {
                            parserHelper.addPublicationDate(context, siteLocale, text, book);
                        }
                        break;
                    }
                    case "Aantal pagina's":
                    case "Nombre de pages": {
                        if (!book.contains(DBKey.PAGES)) {
                            book.setPages(SearchEngineUtils.cleanText(value));
                        }
                        break;
                    }
                    case "Originele titel":
                    case "Titre original": {
                        book.setTranslatedFromTitle(SearchEngineUtils.cleanText(value));
                        break;
                    }
                    case "Hoofdauteur":
                    case "Auteur principal": {
                        // The translator is parsed before the primary author;
                        // Force the primary one to be added at the top of the list.
                        parseAuthor(value, AuthorRole.WRITER, book, true);
                        break;
                    }
                    case "Tweede Auteur":
                    case "Deuxième auteur": {
                        parseAuthor(value, AuthorRole.WRITER, book, false);
                        break;
                    }
                    case "Hoofdillustrator":
                    case "Illustrateur en chef":
                    case "Tweede Illustrator":
                    case "Deuxième illustrateur": {
                        parseAuthor(value, AuthorRole.ARTIST, book, false);
                        break;
                    }
                    case "Hoofdredacteur":
                    case "Rédacteur en chef":
                    case "Tweede Redacteur":
                    case "Deuxième rédacteur": {
                        parseAuthor(value, AuthorRole.EDITOR, book, false);
                        break;
                    }
                    case "Eerste Vertaler":
                    case "Tweede Vertaler":
                    case "Premier traducteur":
                    case "Deuxième traducteur": {
                        parseAuthor(value, AuthorRole.TRANSLATOR, book, false);
                        break;
                    }
                    case "Verteller":
                    case "Narrateur": {
                        if ("E-book".equals(book.getFormat())) {
                            parseAuthor(value, AuthorRole.NARRATOR, book, false);
                        }
                        break;
                    }
                    case "Serie": {
                        // The series number is only available embedded in the title
                        // but without any specific structure to it.
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final String text = SearchEngineUtils.cleanName(a);
                            if (!text.isBlank()) {
                                book.add(Series.from(text));
                            }
                        }
                        break;
                    }

                    case "Hoofduitgeverij":
                    case "Editeur principal":
                    case "Tweede Uitgeverij":
                    case "Deuxième édition":
                    case "Co Uitgever(s)":
                    case "Coéditeur(s)": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final String s = SearchEngineUtils.cleanName(a);
                            if (!s.isBlank()) {
                                book.add(Publisher.from(s));
                            }
                        }
                        break;
                    }
                    case "EAN": {
                        // useful for audiobooks
                        if (!book.contains(DBKey.ISBN)) {
                            book.setRawProductCode(SearchEngineUtils.cleanText(value));
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
        parsePrice(document, book);

        if (fetchCovers[0]) {
            parseCoversFromHtml(context, document, fetchCovers, book);
        }
    }

    private void parseAuthor(@NonNull final Element value,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book,
                             final boolean addAsFirst) {
        final Element a = value.selectFirst("a");
        if (a != null) {
            final String s = SearchEngineUtils.cleanName(a);
            if (!s.isBlank()) {
                parserHelper.addAuthor(Author.from(s), type, book, addAsFirst);
            }
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {

        final Element descrElement = document
                .selectFirst("div[data-testid=collapsible-content] div.pr-10.prose");
        if (descrElement != null) {
            final String s = SearchEngineUtils.cleanText(descrElement);
            if (!s.isBlank()) {
                book.setDescription(s);
            }
        }
    }

    private void parseRating(@NonNull final Document document,
                             @NonNull final Book book,
                             @NonNull final RealNumberParser realNumberParser) {
        // Rating uses a ',' as decimal separator.
        final RatingParser ratingParser = new RatingParser(realNumberParser, 5);

        final Element ratingElement = document.selectFirst("span[data-testid=average-rating]");
        if (ratingElement != null) {
            ratingParser.parse(ratingElement.text()).ifPresent(book::setRating);
        }
    }

    private void parsePrice(@NonNull final Document document,
                            @NonNull final Book book) {
        //TODO: if they are out of stock, this element will NOT contain a price.
        // We should get the price from the buttons on the page just above this field
        // but those button elements are not easy to parse for.
        final Element priceContainer = document.selectFirst("div.group\\/productoffer");
        if (priceContainer != null) {
            try {
                final Element euroElement = priceContainer
                        .selectFirst("span.row-span-2[aria-hidden=true]");
                if (euroElement == null) {
                    return;
                }
                final Element centsElement = priceContainer
                        .selectFirst("span.translate-x-\\[20\\%\\][aria-hidden=true]");

                // Because they are in separate elements, we do a quick BigDecimal calculation
                // instead of using the full MoneyParser.
                BigDecimal price = new BigDecimal(euroElement.text());
                if (centsElement != null) {
                    final BigDecimal cents = new BigDecimal(centsElement.text())
                            .movePointLeft(2);
                    price = price.add(cents);
                }

                book.setPriceListed(new Money(price, Money.EURO));

            } catch (@NonNull final NumberFormatException ignore) {
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
        parserHelper.setTags(tagNames, book);
    }

    /**
     * Parse the document for cover images.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws StorageException on storage related failures
     */
    private void parseCoversFromHtml(@NonNull final Context context,
                                     @NonNull final Document document,
                                     @NonNull final boolean[] fetchCovers,
                                     @NonNull final Book book)
            throws StorageException {

        // Target an img where src starts with media.s-bol.com
        // AND has loading=eager AND has fetchPriority=high
        final Element frontCoverImg = document.selectFirst(
                "img[src^=https://media.s-bol.com/][loading=eager][fetchPriority=high]");
        if (frontCoverImg == null) {
            return;
        }
        final String url = frontCoverImg.attr("src");
        commonParseCovers(context, document, fetchCovers, url, book);
    }

    private void parseCoversFromJson(@NonNull final Context context,
                                     @NonNull final JSONObject root,
                                     @NonNull final Document document,
                                     @NonNull final boolean[] fetchCovers,
                                     @NonNull final Book book)
            throws StorageException {

        // The front cover is available in the json object,
        // but the back cover is NOT and we need to parse the html for it.
        final JSONObject imageJson = root.optJSONObject("image");
        if (imageJson != null) {
            final String url = imageJson.optString("url");
            commonParseCovers(context, document, fetchCovers, url, book);
        }
    }

    /**
     * Given the front cover url, fetch it and optionally parse the html document
     * for the back cover and get that as well.
     * <p>
     * The front cover url is passed in as that can come either from the json parser
     * or from the html parser.
     *
     * @param context       Current context
     * @param document      to parse
     * @param fetchCovers   Set array indexes to {@code true} to fetch a cover for that index.
     *                      Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param frontCoverUrl to fetch
     * @param book          to update
     *
     * @throws StorageException on storage related failures
     */
    private void commonParseCovers(@NonNull final Context context,
                                   @NonNull final Document document,
                                   @NonNull final boolean[] fetchCovers,
                                   @NonNull final String frontCoverUrl,
                                   @NonNull final Book book)
            throws StorageException {

        if (frontCoverUrl.isEmpty()) {
            return;
        }

        final String codeStr = book.getRawProductCode();
        final Optional<String> oFileSpec = saveImage(context, frontCoverUrl, null,
                                                     codeStr, 0, null);
        if (oFileSpec.isEmpty()) {
            return;
        }
        CoverFileSpecArray.setFileSpec(book, 0, oFileSpec.get());
        // only attempt to get the back-cover if we got a front-cover
        // and if we want one.
        if (!fetchCovers[1]) {
            return;
        }
        // Target the img element whose 'alt' attribute ends with "back cover"
        final Element backCoverImg = document.selectFirst("img[alt$=- back cover]");
        if (backCoverImg == null) {
            return;
        }
        final String url = backCoverImg.attr("src");
        if (url.isEmpty()) {
            return;
        }
        saveImage(context, url, null, codeStr, 1, null).ifPresent(
                fs -> CoverFileSpecArray.setFileSpec(book, 1, fs));
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
