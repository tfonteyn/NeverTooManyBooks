/*
 * @Copyright 2018-2024 HardBackNutter
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
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
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorTypeMapper;
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
 * Other Amazon sites should work for basic info (e.g. title) only.
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
                   SearchEngine.CoverByEdition {

    /** Preferences - Type: {@code String}. */
    public static final String PK_HOST_URL = EngineId.Amazon.getPreferenceKey()
                                             + '.' + Prefs.PK_HOST_URL;
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
    private static final String LABEL_FORMAT =
            // English, Dutch
            "hardcover,paperback"
            // French
            + ",relié,broché"
            // German
            + ",taschenbuch,gebundene ausgabe"
            // Spanish
            + ",tapa dura,tapa blanda"
            // Portuguese
            + ",capa dura,capa blanda";

    private static final String LABEL_LANGUAGE =
            // English
            "language"
            // French
            + ",langue"
            // German
            + ",sprache"
            // Dutch
            + ",taal"
            // Spanish/Portuguese
            + ",idioma";

    private static final String LABEL_PUBLISHER =
            // English
            "publisher"
            // French
            + ",editeur,éditeur"
            // German
            + ",verlag,herausgeber"
            // Dutch
            + ",uitgever"
            // Spanish/Portuguese
            + ",editor,editorial";

    private static final String LABEL_SERIES =
            "series,collection";

    // These labels are ignored, but listed as an indication we know them.
    private static final String LABEL_IGNORED =
            "asin"
            // English
            + ",product dimensions"
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
            + ",vom hersteller empfohlenes alter"
            + ",originaltitel"
            // Dutch
            + ",productafmetingen"
            + ",brutogewicht (incl. verpakking)"
            + ",klantenrecensies"
            + ",plaats op amazon-bestsellerlijst"
            // Spanish/Portuguese
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
        final Document document = loadDocument(context, url, null);

        checkCaptcha(context, url, document);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    private void checkCaptcha(@NonNull final Context context,
                              @NonNull final String url,
                              @NonNull final Document document)
            throws SearchException {
        // FIXME: Amazon is blocking more and more... we'll have to stop supporting it soon.
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

        // Convert an ISBN13 to ISBN10 (i.e. the ASIN)
        final ISBN tmp = new ISBN(validIsbn, true);
        final String asin = tmp.isIsbn10Compat() ? tmp.asText(ISBN.Type.Isbn10) : validIsbn;
        final String url = getHostUrl(context) + String.format(BY_EXTERNAL_ID, asin);

        return genericSearch(context, url, fetchCovers);
    }

    @NonNull
    @Override
    public Book searchByBarcode(@NonNull final Context context,
                                @NonNull final String barcode,
                                @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        if (ASIN.isValidAsin(barcode)) {
            final String url = getHostUrl(context) + String.format(BY_EXTERNAL_ID, barcode);
            return genericSearch(context, url, fetchCovers);
        } else {
            // not supported
            return new Book();
        }
    }

    @NonNull
    @Override
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 1) final int cIdx,
                                                 @Nullable final Size size)
            throws StorageException, SearchException, CredentialsException {
        if (altEdition instanceof AltEditionIsbn) {
            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;
            final String isbn = edition.getIsbn();

            final String url = getHostUrl(context) + String.format(BY_EXTERNAL_ID, isbn);
            final Document document = loadDocument(context, url, null);

            checkCaptcha(context, url, document);

            if (isCancelled()) {
                return Optional.empty();
            }

            return parseCover(context, document, isbn, 0)
                    // let the system resolve any path variations
                    .map(fileSpec -> new File(fileSpec).getAbsolutePath());
        }
        return Optional.empty();
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
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

        // Fr the some books the title will be "just" the title,
        // for other books they will add the author and more info all in the same string.
        // It's too difficult to cover all possibilities, we're leaving that to the user.
        final Element titleElement = document.selectFirst("h1#title > span#productTitle");
        if (titleElement == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no title?");
            return;
        }

        final String title = titleElement.text().strip();
        book.putString(DBKey.TITLE, title);

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
        parseASIN(document, book);

        Series.checkForSeriesNameInTitle(book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            parseCover(context, document, isbn, 0).ifPresent(
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
            LoggerFactory.getLogger().w(TAG, "parsePrice", "no tmmSwatches?");
            return;
        }

        final Element swatchElement = tmmSwatches.selectFirst("div.swatchElement.selected");
        if (swatchElement == null) {
            LoggerFactory.getLogger().w(TAG, "parsePrice", "no swatchElement.selected?");
            return;
        }

        final Element slotPrice = swatchElement.selectFirst("span.slot-price");
        if (slotPrice == null) {
            LoggerFactory.getLogger().w(TAG, "parsePrice", "no span.slot-price?");
            return;
        }


        // 2023-10-28: verified to work on amazon.com, amazon.co.uk, amazon.com.be
        // but some books (.com?) have a "from $xx"
        final Element price = slotPrice.selectFirst("span");
        if (price == null) {
            LoggerFactory.getLogger().w(TAG, "parsePrice", "no span below span.slot-price?");
            return;
        }

        String priceText = price.text().strip();
        for (final String prefix : PRICE_PREFIXES) {
            if (priceText.startsWith(prefix)) {
                priceText = priceText.substring(prefix.length());
                break;
            }
        }

        addPriceListed(context, siteLocale, priceText, null, book);

        // The format can/should also be here
        final Element formatElement = swatchElement.selectFirst("a.a-button-text > span");
        if (formatElement != null) {
            final String format = formatElement.text().strip();
            if (!format.isEmpty()) {
                book.putString(DBKey.FORMAT, format);
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

                    final String label = SearchEngineUtils.cleanText(text[0]);
                    final String data = SearchEngineUtils.cleanName(text[1]);

                    final String lcLabel = label.toLowerCase(siteLocale);

                    if (LABEL_ISBN_13.equals(lcLabel)) {
                        book.putString(DBKey.BOOK_ISBN, data);

                    } else if (LABEL_ISBN_10.equals(lcLabel) && !book.contains(DBKey.BOOK_ISBN)) {
                        book.putString(DBKey.BOOK_ISBN, data);

                    } else if (LABEL_FORMAT.contains(lcLabel)) {
                        // we might already have the format, but we'll overwrite it - that's ok.
                        book.putString(DBKey.FORMAT, label);
                        book.putString(DBKey.PAGE_COUNT, extractPages(context, data));

                    } else if (LABEL_LANGUAGE.contains(lcLabel)) {
                        book.putString(DBKey.LANGUAGE, data);

                    } else if (LABEL_PUBLISHER.contains(lcLabel)) {
                        boolean publisherWasAdded = false;
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

                    } else if (LABEL_SERIES.contains(lcLabel)) {
                        book.add(Series.from(data));

                    } else {
                        if (BuildConfig.DEBUG /* always */) {
                            if (!LABEL_IGNORED.contains(lcLabel)) {
                                LoggerFactory.getLogger().d(TAG, "parse", "label=" + label);
                            }
                        }
                    }
                });
    }

    @NonNull
    protected DateParser getDateParser(@NonNull final Context context,
                                       @NonNull final Locale locale) {
        final List<Locale> locales;

        // Hack to support the Portuguese site which does a redirect to the Spanish one
        if ("es".equals(locale.getLanguage())) {
            locales = new ArrayList<>(LocaleListUtils.asList(context, locale));
            // Not verified but let's hope "pt_BR" uses the same spelling for month names
            locales.add(1, new Locale("pt"));
        } else {
            locales = LocaleListUtils.asList(context, locale);
        }
        final Locale systemLocale = ServiceLocator
                .getInstance().getSystemLocaleList().get(0);
        return new FullDateParser(systemLocale, locales);
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
                    final Author author = Author.from(a.text().strip());
                    @Author.Type
                    int type = Author.TYPE_UNKNOWN;

                    final Element typeElement = span.selectFirst("span.contribution");
                    if (typeElement != null) {
                        String data = typeElement.text().strip();
                        final Matcher matcher = AUTHOR_TYPE_PATTERN.matcher(data);
                        if (matcher.find()) {
                            data = matcher.group(1);
                        }

                        if (data != null) {
                            type = authorTypeMapper.map(siteLocale, data);
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
                                        @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        final Element img = document.selectFirst("img#imgBlkFront");
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
            // the src attribute contains a low quality picture in base64 format.
            String srcUrl = img.attr("src");
            // annoying... the url seems to start with a \n. Cut it off.
            if (srcUrl.startsWith("\n")) {
                srcUrl = srcUrl.substring(1);
            }
            url = srcUrl;
        }

        return saveImage(context, url, bookId, cIdx, null);
    }

    @NonNull
    private String extractPages(@NonNull final Context context,
                                @NonNull final CharSequence data) {
        if (pagesPattern == null) {
            final String baseUrl = getHostUrl(context);
            // check the domain name to determine the language of the site
            final String root = baseUrl.substring(baseUrl.lastIndexOf('.') + 1);
            final String pagesStr;
            // These are string from the actual website; hence not from resources
            switch (root) {
                case "de":
                    pagesStr = "Seiten";
                    break;

                case "nl":
                    pagesStr = "pagina's";
                    break;

                case "es":
                    pagesStr = "páginas";
                    break;

                default:
                    // English, French
                    pagesStr = "pages";
                    break;
            }
            pagesPattern = Pattern.compile(pagesStr, Pattern.LITERAL);
        }

        return pagesPattern.matcher(data).replaceAll("").strip();
    }
}
