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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorRoleMapper;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * This class supports parsing these Amazon websites:
 * www.amazon.com
 * www.amazon.co.uk
 * www.amazon.fr
 * www.amazon.de
 * www.amazon.nl
 * www.amazon.es
 * <p>
 * Anything failing there is a bug.
 * Other Amazon sites should work for basic info (e.g. title) only;
 * no guarantee other info will be parsed correctly.
 * <p>
 * TODO: We're ignoring the rating(stars) for now.
 * Note we don't support Kindle or Audiobook entries very well for now
 * due to them not having ISBN's.
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
                   SearchEngine.ByExternalId,
                   SearchEngine.CoverByEdition,
                   SearchEngine.SearchOnSite {

    private static final String PREFERENCE_KEY = "amazon";

    /** Website character encoding. */
    private static final String CHARSET = "UTF-8";
    /** Log tag. */
    private static final String TAG = "AmazonSearchEngine";

    /**
     * Search by product id.
     * <p>
     * Param 1: ISBN13 or ASIN/ISBN10.
     */
    private static final String BY_PRODUCT_ID = "/gp/product/%1$s";

    /**
     * The search url for books when opening a browser activity.
     * <p>
     * Fields that can be added to the /gp URL:
     * <ul>
     *      <li>&field-isbn</li>
     *      <li>&field-author</li>
     *      <li>&field-title</li>
     *      <li>&field-publisher</li>
     *      <li>&field-keywords</li>
     * </ul>
     * <p>
     * ENHANCE: add "Find by ISBN" menu item;
     * ENHANCE: add "Find by Title+author" menu item
     *
     * @see <a href="https://www.amazon.co.uk/advanced-search/books/">
     *         www.amazon.co.uk/advanced-search/books</a>
     */
    private static final String ADV_SEARCH_BOOKS = "/gp/search?index=books";

    /**
     * 2025-06-01: this may be irrelevant now as the date seems to have its own label now.
     * <p>
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

    /** Depending on the specific site, the labels translations we check for. */
    private static final List<String> LABEL_FORMAT = List.of(
            // English
            "kindle edition", "audiobook",
            // English AND Dutch
            "hardcover", "paperback", "paperback (mass market)",
            // Dutch
            "kindle-editie", "cd",
            // French
            "format kindle", "livre audio",
            "relié", "broché",
            // German
            "kindle", "hörbuch",
            "taschenbuch", "gebundene ausgabe", "gebundenes buch",
            // Spanish; bolsillo==paperback... but more correctly a "boxset"
            // leave the latter to the user, don't add it to the format mapper
            "versión kindle", "audiolibro",
            "tapa dura", "tapa blanda", "libro de bolsillo",
            // Portuguese
            "audiolivro",
            "capa dura", "capa blanda"
    );

    private static final List<String> LABEL_PAGES = List.of(
            // English
            "print length",
            // French
            "nombre de pages de l'édition imprimée",
            // German
            "seitenzahl der print-ausgabe",
            // Dutch
            "printlengte",
            // Spanish/Portuguese
            "longitud de impresión"
    );

    private static final List<String> LABEL_LANGUAGE = List.of(
            // English
            "language",
            // French
            "langue",
            // German
            "sprache",
            // Dutch
            "taal",
            // Spanish/Portuguese
            "idioma"
    );

    private static final List<String> LABEL_PUBLISHER = List.of(
            // English
            "publisher",
            // French
            "editeur", "éditeur",
            // German: note that "Herausgeber" (==editor) is an Amazon translation error.
            // They use it as a synonym for "Verlag".
            "verlag", "herausgeber",
            // Dutch
            "uitgever",
            // Spanish/Portuguese
            "editor", "editorial"
    );

    private static final List<String> LABEL_PUBLICATION_DATE = List.of(
            // English
            "publication date",
            // French
            "date de publication",
            // German
            "erscheinungstermin",
            // Dutch
            "publicatiedatum",
            // Spanish
            "fecha de publicación"
    );

    private static final List<String> LABEL_SERIES = List.of(
            "series",
            "collection"
    );

    private static final List<String> LABEL_ASIN = List.of(
            "asin"
    );

    // These labels are ignored, but listed as an indication we know them.
    private static final String LABEL_IGNORED =
            // English
            "product dimensions"
            + ",shipping weight"
            + ",customer reviews"
            + ",average customer review"
            + ",amazon bestsellers rank"
            // French
            + ",dimensions du produit"
            + ",commentaires client"
            + ",moyenne des commentaires client"
            + ",classement des meilleures ventes d'amazon"
            // German
            + ",größe und/oder gewicht"
            + ",abmessungen"
            + ",kundenrezensionen"
            + ",amazon bestseller-rang"
            + ",lesealter"
            + ",vom hersteller empfohlenes alter"
            + ",originaltitel"
            // Dutch
            + ",productafmetingen"
            + ",brutogewicht (incl. verpakking)"
            + ",klantenrecensies"
            + ",plaats op amazon-bestsellerlijst"
            // Spanish
            + ",peso del producto"
            + ",clasificación en los más vendidos de amazon"
            + ",dimensiones"
            // Portuguese
            + ",peso do produto"
            + ",classificação dos produtos mais vendidos"
            + ",dimensões";

    private static final String LABEL_ISBN_13 = "isbn-13";
    private static final String LABEL_ISBN_10 = "isbn-10";

    private static final String[] PRICE_PREFIXES = {
            // English
            "from ",
            // French
            "à partir de ",
            // German
            "ab ",
            // Dutch
            "vanaf ",
            // Spanish/Portuguese
            "desde "};

    private static final String SPANISH = "es";

    /**
     * Parse the "x pages" string.
     * English/French,German,Dutch,Spanish/Portuguese
     */
    private static final Pattern PAGES_PATTERN =
            Pattern.compile("(\\d+) (?:pages|Seiten|pagina's|páginas)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final AuthorRoleMapper authorRoleMapper = new AuthorRoleMapper();

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
    public AmazonSearchEngine(@NonNull final Context appContext,
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
                                    R.string.site_amazon,
                                    List.of(R.string.site_description_various_languages,
                                            R.string.site_description_shop),
                // amazon.com, amazon.ca : blocked by captcha
                                    "https://www.amazon.co.uk",
                // The Locale will be dynamically set depending on the country
                                    Locale.US)
                .setIdentifierKey(Identifier.SID_ASIN)
                .setPreferenceFragmentClazz(AmazonPreferencesFragment.class);
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.
        return getLocale(context, getHostUrl());
    }

    /**
     * Check if we have been blocked by the captcha.
     * If we are, this engine will be disabled.
     * The user can re-enable it in the site settings.
     *
     * @param context  Current context
     * @param url      the search url last used
     * @param document to parse
     *
     * @throws SearchException if we were blocked
     */
    private void checkCaptcha(@NonNull final Context context,
                              @NonNull final String url,
                              @NonNull final Document document)
            throws SearchException {
        final Element block = document.selectFirst("form[action='/errors/validateCaptcha']");
        if (block != null) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, "checkCaptcha", "Mr. B...");
            }

            Site.Type.Data.getSite(getEngineId()).setActive(false);
            throw new SearchException(getEngineId(), "Amazon blocked url=" + url,
                                      context.getString(R.string.error_site_access_blocked));
        }
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        // Try to convert an ISBN13 to ISBN10 (i.e. the ASIN)
        final ISBN tmp = new ISBN(validIsbn, true);
        // If conversion is not possible, use the ISBN13 anyhow
        final String asin = tmp.isIsbn10Compat() ? tmp.asText(ISBN.Type.Isbn10) : validIsbn;

        final String url = getHostUrl() + String.format(BY_PRODUCT_ID, asin);
        return genericSearch(context, url, fetchCovers);
    }

    /**
     * Search by ASIN.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final ASIN asin = new ASIN(externalId);
        if (asin.isValid()) {
            final String url = getHostUrl() + String.format(BY_PRODUCT_ID, asin.asText());
            return genericSearch(context, url, fetchCovers);
        } else {
            return new Book();
        }
    }

    @NonNull
    @Override
    public Book searchByBarcode(@NonNull final Context context,
                                @NonNull final String barcode,
                                @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        return searchByExternalId(context, barcode, fetchCovers);
    }

    @NonNull
    private Book genericSearch(@NonNull final Context context,
                               @NonNull final String url,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final Document document = loadDocument(context, url, null);

        checkCaptcha(context, url, document);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 0) final int cIdx,
                                                 @Nullable final ImageWebSize size)
            throws StorageException, SearchException, CredentialsException {
        if (altEdition instanceof AltEditionIsbn) {
            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

            final String url = getHostUrl() + String.format(BY_PRODUCT_ID, isbn);
            final Document document = loadDocument(context, url, null);

            checkCaptcha(context, url, document);

            if (isCancelled()) {
                return Optional.empty();
            }

            return parseCover(context, document, isbn, cIdx)
                    // let the system resolve any path variations
                    .map(fileSpec -> new File(fileSpec).getAbsolutePath());
        }
        return Optional.empty();
    }

    /**
     * Parse the downloaded {@link org.jsoup.nodes.Document} for a single Book.
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

        // For some books the title will be "just" the title,
        // for other books they will add the author and more info all in the same string.
        // It's too difficult to cover all possibilities, we're leaving that to the user.
        final Element titleElement = document.selectFirst("h1#title > span#productTitle");
        if (titleElement == null) {
            LoggerFactory.getLogger().w(TAG, getHostUrl(),
                                        "parse", "no title?");
            return;
        }

        book.setTitle(cleanText(titleElement));

        // Use the site locale for all parsing!
        // Derive it from the actual url, as this might have been a redirect
        // e.g. amazon.pt redirects to amazon.es
        final Locale siteLocale = getLocale(context, document.location().split("/")[2]);

        parsePrice(context, siteLocale, document, book);
        parseAuthors(siteLocale, document, book);

        if (isCancelled()) {
            return;
        }

        parseDetails(context, siteLocale, document, book);
        // Normally we should have found the ASIN, but if not, try parsing the add-to-cart code
        if (book.getIdentifierValue(Identifier.SID_ASIN).isEmpty()) {
            parseASIN(document, book);
        }

        Series.checkForSeriesNameInTitle(book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCover(context, document, book.getIsbn(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));

        }
    }

    /**
     * Parse the document for a price field.
     * <p>
     * We try a couple of but there is no guarantee.
     *
     * @param context    Current context
     * @param siteLocale to use
     * @param document   to parse
     * @param book       to update
     */
    private void parsePrice(@NonNull final Context context,
                            @NonNull final Locale siteLocale,
                            @NonNull final Document document,
                            @NonNull final Book book) {
        final Element tmmSwatches = document.selectFirst("div#tmmSwatches");
        if (tmmSwatches == null) {
            // This happens when the book was on the site, but is actually not for sale.
            // i.e. sold-out, even on marketplace.
            LoggerFactory.getLogger().w(TAG, getHostUrl(),
                                        "parsePrice", "no tmmSwatches?");
            return;
        }

        final Element swatchElement = tmmSwatches.selectFirst("div.swatchElement.selected");
        if (swatchElement == null) {
            // 2025-11-16: seen this happen while the page structure was not changed.
            // This would only be the case when the page has NO selected format.
            // Not sure how this can happen; multiple checks showed the structured
            // to be correct and no way was found to have NO format selected.
            LoggerFactory.getLogger().w(TAG, getHostUrl(),
                                        "parsePrice", "no swatchElement.selected?");
            return;
        }

        final Element slotPrice = swatchElement.selectFirst("span.slot-price");
        if (slotPrice == null) {
            LoggerFactory.getLogger().w(TAG, getHostUrl(),
                                        "parsePrice", "no span.slot-price?");
            return;
        }


        // 2023-10-28: verified to work on amazon.com, amazon.co.uk, amazon.com.be
        // but some books (.com?) have a "from $xx"
        final Element price = slotPrice.selectFirst("span");
        if (price == null) {
            LoggerFactory.getLogger().w(TAG, getHostUrl(),
                                        "parsePrice", "no span below span.slot-price?");
            return;
        }

        String priceText = cleanText(price);
        for (final String prefix : PRICE_PREFIXES) {
            if (priceText.startsWith(prefix)) {
                priceText = priceText.substring(prefix.length());
                break;
            }
        }

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> tmpAllLocales = LocaleListUtils.asList(siteLocale, userLocales);
        // Amazon does not give a hoot about other countries outside of the US.
        // So, for example, on the german site we find prices like "€12.34", i.e. using a dot
        // as the decimal separator instead of the proper comma as used in Germany.
        // We're keeping the site-locale first to guard against Amazon doing the right thing...
        // but add the US Locale in the meantime.
        final List<Locale> allLocales = new ArrayList<>(tmpAllLocales);
        allLocales.add(Locale.US);

        final MoneyParser parser = new MoneyParser(siteLocale, allLocales);
        addPriceListed(context, parser, priceText, null, book);

        // The format can/should also be here
        final Element formatElement = swatchElement.selectFirst("a.a-button-text > span");
        if (formatElement != null) {
            book.setFormat(cleanText(formatElement));
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
                final String asin = cleanText(asinElement.attr("value"));
                book.setIdentifierValue(Identifier.SID_ASIN, asin);
            }
        }
    }

    /**
     * Parse fields.
     * <p>
     * Parse format last checked/updated: 2023-06-25
     *
     * @param context    Current context
     * @param siteLocale to use for case manipulation
     * @param document   to parse
     * @param book       to update
     */
    private void parseDetails(@NonNull final Context context,
                              @NonNull final Locale siteLocale,
                              @NonNull final Document document,
                              @NonNull final Book book) {

        document.select("div#detailBulletsWrapper_feature_div > div > ul > li")
                .stream()
                .map(li -> li.text().strip().split(":", 2))
                .filter(text -> text.length == 2)
                .forEach(text -> {

                    final String label = cleanText(text[0]);
                    final String lcLabel = label.toLowerCase(siteLocale);

                    if (LABEL_ASIN.contains(lcLabel)) {
                        // Not checking validity, this is straight from Amazon after all.
                        final ASIN asin = new ASIN(text[1]);
                        book.setIdentifierValue(Identifier.SID_ASIN, asin.asText());

                        if (!book.hasIsbn()) {
                            // Set as ISBN if we don't have on yet.
                            // If the book has a real ISBN-13 it will overwrite this.
                            book.setIsbn(asin.asText());
                        }
                    } else if (LABEL_ISBN_13.equals(lcLabel)) {
                        book.setIsbn(ISBN.cleanText(text[1]));

                    } else if (LABEL_ISBN_10.equals(lcLabel) && !book.hasIsbn()) {
                        book.setIsbn(ISBN.cleanText(text[1]));

                    } else if (LABEL_FORMAT.contains(lcLabel)) {
                        // we might already have the format, but we'll overwrite it - that's OK.
                        book.setFormat(label);
                        // 2025-06-01: we can likely remove this, as there is now LABEL_PAGES
                        final String data = cleanText(text[1]);
                        parsePages(data, book);

                    } else if (LABEL_PAGES.contains(lcLabel)) {
                        final String data = cleanText(text[1]);
                        parsePages(data, book);

                    } else if (LABEL_LANGUAGE.contains(lcLabel)) {
                        final String data = cleanText(text[1]);
                        book.setLanguage(data);

                    } else if (LABEL_PUBLISHER.contains(lcLabel)) {
                        boolean publisherWasAdded = false;
                        final String data = cleanName(text[1]);
                        final Matcher matcher = PUBLISHER_PATTERN.matcher(data);
                        if (matcher.find()) {
                            final String pubName = matcher.group(1);
                            if (pubName != null) {
                                final Publisher publisher = Publisher.from(pubName.strip());
                                book.add(publisher);
                                publisherWasAdded = true;
                            }

                            final String pubDate = matcher.group(2);
                            if (pubDate != null) {
                                addPublicationDate(context, siteLocale, pubDate.strip(), book);
                            }
                        }

                        if (!publisherWasAdded) {
                            final Publisher publisher = Publisher.from(data);
                            book.add(publisher);
                        }
                    } else if (LABEL_PUBLICATION_DATE.contains(lcLabel)) {
                        final String data = cleanText(text[1]);
                        addPublicationDate(context, siteLocale, data, book);

                    } else if (LABEL_SERIES.contains(lcLabel)) {
                        final String data = cleanText(text[1]);
                        book.add(Series.from(data));

                    } else {
                        if (BuildConfig.DEBUG /* always */) {
                            if (!LABEL_IGNORED.contains(lcLabel)) {
                                LoggerFactory.getLogger().d(TAG, getHostUrl(),
                                                            "parse", "label=" + label);
                            }
                        }
                    }
                });
    }

    private void parsePages(@NonNull final CharSequence data,
                            @NonNull final Book book) {
        final Matcher matcher = PAGES_PATTERN.matcher(data);
        if (matcher.find()) {
            final String pages = matcher.group(1);
            if (pages != null && !pages.isEmpty()) {
                book.setPages(pages);
            }
        }
    }

    @NonNull
    protected DateParser<LocalDateTime> getFullDateParser(@NonNull final Context context,
                                                          @NonNull final Locale locale) {

        // Hack to support the Portuguese site which does a redirect to the Spanish one
        if (SPANISH.equals(locale.getLanguage())) {
            final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
            final List<Locale> allLocales =
                    new ArrayList<>(LocaleListUtils.asList(locale, userLocales));
            // "pt" and "pt_BR" use the same spelling for month names
            allLocales.add(1, new Locale("pt"));
            return new FullDateParser(isoDateParser, allLocales);

        } else {
            return super.getFullDateParser(context, locale);
        }
    }

    /**
     * Parse the Author list.
     *
     * @param siteLocale to use for case manipulation
     * @param document   to parse
     * @param book       to update
     */
    private void parseAuthors(@NonNull final Locale siteLocale,
                              @NonNull final Document document,
                              @NonNull final Book book) {
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
                    final String s = cleanName(a);
                    final Author author = Author.from(s);
                    @AuthorRole.Role
                    int type = AuthorRole.UNKNOWN;

                    final Element typeElement = span.selectFirst("span.contribution");
                    if (typeElement != null) {
                        String data = cleanText(typeElement);
                        final Matcher matcher = AUTHOR_TYPE_PATTERN.matcher(data);
                        if (matcher.find()) {
                            data = matcher.group(1);
                        }

                        if (data != null) {
                            type = authorRoleMapper.map(siteLocale, data);
                        }
                    }

                    addAuthor(author, type, book);
                }
            }
        }
    }

    /**
     * Parses the given {@link Document} for the cover and fetches it when present.
     *
     * @param context  Current context
     * @param document to parse
     * @param bookId   (optional) isbn or native id of the book,
     *                 will only be used for the temporary cover filename
     * @param cIdx     0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                            @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        final Element img = document.selectFirst("img#landingImage");
        if (img == null) {
            return Optional.empty();
        }

        String url;
        try {
            // data-a-dynamic-image = {"https://...":[327,499],"https://...":[227,346]}
            final String tmp = img.attr("data-a-dynamic-image");
            final JSONObject json = new JSONObject(tmp);
            // just grab the first key
            url = json.keys().next();

        } catch (@NonNull final JSONException e) {
            // fallback to the src attribute
            String srcUrl = img.attr("src");
            // annoying... the url may start with a \n. Cut it off.
            if (srcUrl.startsWith("\n")) {
                srcUrl = srcUrl.substring(1);
            }
            url = srcUrl;
        }

        return saveImage(context, url, null, bookId, cIdx, null);
    }

    @Override
    public boolean isShowSearchOnSiteMenu(@NonNull final Context context) {
        final String key = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU;
        return ServiceLocator.getInstance().getSharedPreferences().getBoolean(key, true);
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

        final StringJoiner fields = new StringJoiner("&");
        fields.add(ADV_SEARCH_BOOKS);

        if (author != null) {
            final String cAuthor = SearchEngineUtils
                    .encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    fields.add("field-author=" + URLEncoder.encode(cAuthor, CHARSET));
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
                    fields.add("field-keywords=" + URLEncoder.encode(cSeries, CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        return getHostUrl() + fields;
    }
}
