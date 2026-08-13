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

package com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * Catalogue site serving Czech and Slovak mainly.
 * <p>
 * The site uses different id's for different types of authors.
 * Example:
 * https://www.databazeknih.cz/autori/albert-uderzo-76934
 * https://www.databazeknih.cz/ilustratori/albert-uderzo-91908
 * (there are more types!)
 * <p>
 * We only use the "autori" links for now as we have no mapping to the others
 */
public class DatabazeKnihSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByText,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByBarcode {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    private static final String HOST_URL = "https://www.databazeknih.cz";
    /** {@link EngineId#getDefaultLocale()}. */
    private static final Locale HOST_LOCALE = new Locale("cs", "CZ");
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "databazeknih";

    // see class docs!
    static final String AUTHOR_URL = "https://www.databazeknih.cz/autori/x-%s";

    private static final String TAG = "DatabazeKnihSearchEngin";

    /**
     * Search by isbn, or any other set of keywords.
     * Param 1: url encoded isbn/keywords
     */
    private static final String SEARCH_URL = HOST_URL + "/search?in=books&q=%1$s";
    /**
     * Search by sid.
     * Param 1: sid
     */
    private static final String BY_SID = HOST_URL + "/prehled-knihy/x-%1$s";
    /**
     * Fetch a lazy-loading subsection of the book page.
     * Param 1: sid
     */
    private static final String MORE_DETAILS_URL = HOST_URL + "/book-detail-more-info/%1$s";

    /** The HTML title attribute starts with this if we get a list back. */
    private static final String MULTI_RESULT_PAGE_TITLE = "Vyhledávání";
    /** a link txt we need to remove from the description field. */
    private static final String CELY_TEXT = "... celý text";
    /** website shows this if the book has no description. */
    private static final String NO_DESCRIPTION_TEXT = "Popis knihy zde zatím bohužel není...";
    /** website indication it's an eBook. */
    private static final String EBOOK = "ekniha";
    /** website indication it's an eBook. */
    private static final String AUDIOBOOK = "audiokniha";
    /** website indication it's a normal/paper book. */
    private static final String CLASSIC_BOOK = "klasická kniha";

    /**
     * The site uses non-standard language names.
     *
     * @see #mapLanguage(String)
     */
    private static final Map<String, String> LANG_MAPPING = Map.of(
            "český", "ces",
            "slovenský", "slo",
            "německý", "deu",
            "polský", "pol",
            "anglický", "eng",
            "francouzský", "fre",
            "španělský", "spa",
            "italský", "ita",
            // literally "other", we remove it
            "jiný", ""
    );
    private static final Pattern TITLE_YEAR_PATTERN = Pattern.compile("^(.*?)(?:,\\s*(\\d{4}))?$");

    @NonNull
    private final RatingParser ratingParser;
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();
    private final AuthorResolverHelper authorResolverHelper;

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
    public DatabazeKnihSearchEngine(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
        ratingParser = new RatingParser(5);
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
        return new EngineId.Builder(HOST_PREF_KEY,
                                    R.string.site_databazeknih_cz,
                                    List.of(R.string.site_description_czech,
                                            R.string.site_description_catalog),
                                    HOST_URL,
                                    HOST_LOCALE)
                .setPreferenceFragmentClazz(DatabazeKnihPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_DATABAZE_KNIH)
                .setIdentifierKey(Identifier.EntityType.Author, Identifier.SID_DATABAZE_KNIH)
                .setAuthorResolverSupplier(DatabazeKnihAuthorResolver::create);
    }

    /**
     * Called at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context
     *
     * @return list
     */
    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_databaze_knih);
        final String site = "https://www.databazeknih.cz";
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_DATABAZE_KNIH,
                               name, site,
                               "https://www.databazeknih.cz/prehled-knihy/x-%s",
                               "P10386"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_DATABAZE_KNIH,
                               name, site,
                               AUTHOR_URL,
                               "P10387")
        );
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final String externalId = criteria.requireSid(getEngineId());
        final String url = String.format(BY_SID, externalId);
        final Document document = loadDocument(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, criteria.getFetchCovers(), book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());

        return search(context, codeStr, criteria.getFetchCovers());
    }

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

        return search(context, words.toString(), criteria.getFetchCovers());
    }

    @NonNull
    private Book search(@NonNull final Context context,
                        @NonNull final CharSequence queryParams,
                        @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, StorageException {
        final Book book = new Book();
        // Sanity check
        if (queryParams.length() == 0) {
            return book;
        }

        final String url = String.format(SEARCH_URL, queryParams);
        final Document document = loadDocument(context, url, null);

        if (!isCancelled()) {
            if (isMultiResult(document)) {
                multiResult(context, document, fetchCovers, book);
            } else {
                parse(context, document, fetchCovers, book);
            }
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context      Current context
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
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws SearchException, CredentialsException, StorageException {

        final String url = parseMultiResult(document);
        if (url == null) {
            return;
        }
        final Document redirected = loadDocument(context, url, null);
        if (!isCancelled()) {
            // sanity check
            if (!isMultiResult(redirected)) {
                parse(context, redirected, fetchCovers, book);
            }
        }
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param document to parse
     *
     * @return the url to redirect to, or {@code null} if parsing failed.
     */
    @VisibleForTesting
    @Nullable
    String parseMultiResult(@NonNull final Document document) {
        final Element urlElement = document.selectFirst("p.new a.new");
        if (urlElement == null) {
            return null;
        }
        String url = urlElement.attr("href");
        if (url.isBlank()) {
            return null;
        }
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = HOST_URL + url;
        }
        return url;
    }

    private boolean isMultiResult(@NonNull final Document document) {
        return document.title().startsWith(MULTI_RESULT_PAGE_TITLE);
    }

    /**
     * Parses the downloaded {@link Document}.
     * We only parse the <strong>first book</strong> found.
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

        @Nullable
        final String sid = parseMetaTags(document, book);

        Element element;

        // Parsed from 'document'.
        element = document.selectFirst("div.gridMain p");
        if (element != null) {
            parseDescription(element, book);
        }

        final Element bookRightDiv = document.selectFirst("div.bookRightDiv");
        if (bookRightDiv == null) {
            return;
        }

        element = bookRightDiv.selectFirst("a[href^=/autori/]");
        if (element != null) {
            final Elements aas = element.select("a");
            parseAuthors(aas, AuthorRole.WRITER, book);
        }

        element = bookRightDiv.selectFirst("h1");
        if (element != null) {
            book.setTitle(SearchEngineUtils.cleanText(element.text()));
        }

        element = bookRightDiv.selectFirst("div.ratValue");
        if (element != null) {
            parseRating(element, book);
        }

        element = bookRightDiv.selectFirst("a[href^=/serie/]");
        if (element != null) {
            parseSeries(document, element, book);
        }

        final List<String> tagNames = bookRightDiv.select("a[href^=/zanry/]")
                                                 .stream()
                                                 .map(Element::text)
                                                 .collect(Collectors.toList());
        bookParserHelper.setTags(tagNames, book);

        // Publishers
        final Elements pubElements = bookRightDiv.select("a[href^=/nakladatelstvi/]");
        for (final Element pubElement : pubElements) {
            final String name = SearchEngineUtils.cleanName(pubElement.text());
            if (!name.isBlank()) {
                book.add(Publisher.from(name));
            }
        }

        // This is VERY tricky....
        element = bookRightDiv.selectFirst("br");
        if (element != null) {
            final Node issuedNode = element.nextSibling();
            if (issuedNode != null) {
                final String issued = issuedNode.toString().strip();
                if (!issued.isBlank() && !"?".equals(issued)) {
                    partialDateParser.parse(issued).ifPresent(book::setPublicationDate);
                }
            }
        }

        // Sanity check
        if (sid != null && !sid.isEmpty()) {
            // fetch the "more details" and parse
            final String url = String.format(MORE_DETAILS_URL, sid);
            final Document d2 = loadDocument(context, url, null);
            parseLabelValueTable(d2, book);
        }

        // Check if there is TOC: there will be a link on the lower menu bar.
        final Element linksElement = document.selectFirst("ul#newIcons");
        if (linksElement != null) {
            final Element a = linksElement.selectFirst("a[href^=/povidky-z-knihy/]");
            if (a != null) {
                String url = a.attr("href");
                if (!url.isEmpty()) {
                    // url is relative, prefix the host
                    url = HOST_URL + url;
                    final Document d2 = loadDocument(context, url, null);
                    parseToc(context, d2, book);
                }
            }
        }

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCover(context, document, book.getRawProductCode(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void parseSeries(@NonNull final Document document,
                             @NonNull final Element element,
                             @NonNull final Book book) {
        final String seriesName = SearchEngineUtils.cleanName(element);
        if (seriesName.isEmpty()) {
            return;
        }
        // some series will parse wrong.
        // Example "Lucky Luke (Crew)"
        // The "Crew" part is actually the publisher, but we parse it as the number.
        // Not much we can do about that, as we need to rely on "()" parsing
        // for many sites to BE the number.
        final Series series = Series.from(seriesName);
        final Element nrElement = document.selectFirst(
                "span.nowrap > span.odright_pet, span.nowrap > span.odleft_pet ");
        if (nrElement != null) {
            String nr = nrElement.text();
            if (!nr.isEmpty()) {
                // these usually/always end with ". díl" == "episode"; remove
                if (nr.endsWith(". díl")) {
                    nr = nr.substring(0, nr.length() - 5);
                }
                series.setNumber(nr);
            }
        }
        book.add(series);
    }

    private void parseRating(@NonNull final Element element,
                             @NonNull final Book book) {
        final Node percentage = element.firstChild();
        if (percentage != null) {
            try {
                // 0..100 / 20 -> 0.0..5.0
                final String s = percentage.toString().strip();
                final float rating = (float) Integer.parseInt(s) / 20;
                ratingParser.normalise(rating).ifPresent(book::setRating);
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }
    }

    private void parseDescription(@NonNull final Element desc,
                                  @NonNull final Book book) {
        String text = desc.wholeText();
        // Check/skip if it is "no description"
        if (!NO_DESCRIPTION_TEXT.equals(text)) {
            // text contains \n and lots of whitespace, clean-up
            text = Arrays.stream(text.split("\n"))
                         .map(String::strip)
                         // remove the "click to see more" if present
                         .filter(t -> !CELY_TEXT.equals(t))
                         .collect(Collectors.joining("\n"))
                         // empty lines at end
                         .stripTrailing();

            // depending on the formatting it might still have the "click" text
            if (text.endsWith(CELY_TEXT)) {
                text = text.substring(0, text.length() - 13);
            }
            book.setDescription(text);
        }
    }

    /**
     * Parse the retrieved document after we requested "Více info...".
     *
     * @param root to parse
     * @param book to update
     */
    private void parseLabelValueTable(@NonNull final Document root,
                                 @NonNull final Book book) {

        final Elements infoRows = root.select("div.book-details__row");
        for (final Element row: infoRows) {
            final Element labelElement = row.selectFirst("dt");
            final Element valueElement = row.selectFirst("dd");
            if (labelElement == null || valueElement == null) {
                continue;
            }
            switch (labelElement.text()) {
                case "Originální název": {
                    // "original title, year"
                    parseTitle(valueElement, book);
                    break;
                }
                case "Překlad": {
                    // Překlad: translators
                    final Elements translators = valueElement.select("a[href^=/prekladatele/]");
                    if (!translators.isEmpty()) {
                        parseAuthors(translators, AuthorRole.TRANSLATOR, book);
                    }
                    break;
                }
                case "Počet stran": {
                    // number of pages
                    book.setPages(valueElement.text().strip());
                    break;
                }
                case "Délka": {
                    // Audiobooks duration
                    book.setPages(valueElement.text().strip());
                    break;
                }
                case "Další název": {
                    // Alternative title
                    parseAlternativeTitle(valueElement, book);
                    break;
                }
                case "Jazyk vydání": {
                    // language
                    book.setLanguage(mapLanguage(valueElement.text()));
                    break;
                }
                case "Autor obálky": {
                    // Autor obálky:  covers
                    final Elements coverArtist = valueElement.select("a[href^=/autori-obalek/]");
                    if (!coverArtist.isEmpty()) {
                        parseAuthors(coverArtist, AuthorRole.COVER_ARTIST, book);
                    }
                    break;
                }
                case "Forma": {
                    // Format
                    // This field contains one of:
                    // "klasická kniha" (klassiek boek)
                    // "ekniha" (eBook)
                    // "audiokniha" (audio-book)
                    // Not seen other entries, but not looked to exhaustion...
                    final String text = valueElement.text().strip();
                    if (!text.isEmpty()) {
                        if (EBOOK.equals(text)) {
                            book.setFormat(EBOOK);
                        } else if (AUDIOBOOK.equals(text)) {
                            book.setFormat(AUDIOBOOK);
                        } else if (!CLASSIC_BOOK.equals(text)) {
                            LoggerFactory.getLogger().w(TAG, "found Format=" + text);
                        }
                    }
                    break;
                }
                case "Vazba knihy": {
                    // Binding, more Format info
                    book.setFormat(valueElement.text().strip());
                    break;
                }
                case "ISBN": {
                    // there can be more than one isbn. First one "wins"
                    if (!book.hasProductCode()) {
                        final String[] split = valueElement.text().strip().split(",");
                        if (split.length > 0) {
                            book.setRawProductCode(ISBN.cleanText(split[0].strip()));
                        }
                    }
                    break;
                }
                case "Ilustrace/foto": {
                    // Illustrations or photographers:
                    final Elements aas = valueElement.select("a[href^=/ilustratori/]");
                    if (!aas.isEmpty()) {
                        parseAuthors(aas, AuthorRole.ARTIST, book);
                    }
                    break;
                }


                case "Interpreti": {
                    // Audiobooks, narrator
                    final Elements aas = valueElement.select("a[href^=/interpreti/]");
                    if (!aas.isEmpty()) {
                        parseAuthors(aas, AuthorRole.NARRATOR, book);
                    }
                    break;
                }
                case "Náklad": {
                    // Náklad == circulation
                    book.setPrintRun(valueElement.toString().strip());
                    break;
                }
                case "Rok 1. vydání": {
                    // Year of first publication.
                    // Overwrite anything we potentially had before
                    partialDateParser.parse(valueElement.text().strip())
                                     .ifPresent(book::setFirstPublicationDate);
                    break;
                }
            }
        }
    }

    private void parseTitle(@NonNull final Element valueElement,
                            @NonNull final Book book) {
        final String text = SearchEngineUtils.cleanText(valueElement.text());
        final Matcher matcher = TITLE_YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            if (book.getTranslatedFromTitle().isBlank()) {
                //noinspection DataFlowIssue
                book.setTranslatedFromTitle(matcher.group(1).strip());
            }
            @Nullable
            final String originalPub = matcher.group(2);
            if (originalPub != null) {
                partialDateParser.parse(text).ifPresent(book::setFirstPublicationDate);
            }
        }
    }

    private void parseAlternativeTitle(@NonNull final Element valueElement,
                                       @NonNull final Book book) {
        final String text = SearchEngineUtils.cleanText(valueElement.text());
        final Matcher matcher = TITLE_YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            if (book.getTitle().isBlank()) {
                //noinspection DataFlowIssue
               book.setTitle(matcher.group(1).strip());
            }

            if (book.getPublicationDate() == PartialDate.NOT_SET) {
                @Nullable
                final String originalPub = matcher.group(2);
                if (originalPub != null) {
                    partialDateParser.parse(text).ifPresent(book::setPublicationDate);
                }
            }
        }
    }

    @NonNull
    private String mapLanguage(@NonNull final String s) {
        return Objects.requireNonNullElse(LANG_MAPPING.get(s), s);
    }

    @VisibleForTesting
    void parseToc(@NonNull final Context context,
                  @NonNull final Document root,
                  @NonNull final Book book) {

        final Element element = root.selectFirst("table.new.odtop_big");
        if (element != null) {
            final Elements tds = element.select("td");

            Author primaryAuthor = book.getPrimaryAuthor();
            if (primaryAuthor == null) {
                primaryAuthor = Author.createUnknownAuthor(context);
            }

            final List<TocEntry> toc = new ArrayList<>();
            for (final Element td : tds) {
                final Element a = td.selectFirst("a");
                if (a != null) {
                    // ENHANCE: if we follow the link, we can get the actual author
                    //  and original lang. title... but we don't support the latter yet
                    // https://www.databazeknih.cz/povidky-z-knihy/fantasy-a-science-fiction-1994-04-87630
                    final String title = a.text();
                    if (!title.isEmpty()) {
                        final TocEntry tocEntry = new TocEntry(primaryAuthor, title);
                        final Element year = a.nextElementSibling();
                        if (year != null) {
                            partialDateParser.parse(year.text()).ifPresent(
                                    tocEntry::setFirstPublicationDate);
                        }
                        toc.add(tocEntry);
                    }
                }
            }
            if (!toc.isEmpty()) {
                book.setToc(toc);
            }
        }
    }

    /**
     * Parse all "a" links in the given element for Authors.
     *
     * @param aas  to parse
     * @param type of author
     * @param book to update
     */
    private void parseAuthors(@NonNull final Collection<Element> aas,
                              @AuthorRole.Role final int type,
                              @NonNull final Book book) {
        for (final Element a : aas) {
            final String text = a.text();
            if (!text.isEmpty()) {
                parseAuthor(a, text, type, book);
            }
        }
    }

    /**
     * Parse the given link/text for an Author.
     *
     * @param a    to parse
     * @param text author name
     * @param type of author
     * @param book to update
     */
    private void parseAuthor(@NonNull final Element a,
                             @NonNull final String text,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {
        final String s = SearchEngineUtils.cleanName(text);
        if (s.isBlank()) {
            return;
        }

        final Author author = Author.from(s);

        final String url = a.attr("href");
        // see class docs!
        if (url.contains("/autori/")) {
            final int index = url.lastIndexOf('-');
            if (index > 0 && (index + 1) < url.length()) {
                final String id = url.substring(index + 1);
                if (!id.isEmpty()) {
                    author.setIdentifierValue(Identifier.SID_DATABAZE_KNIH, id);
                }
            }
        }
        bookParserHelper.addAuthor(author, type, book, false);
    }

    @Nullable
    private String parseMetaTags(@NonNull final Document document,
                                 @NonNull final Book book) {
        String id = null;

        final Elements metaElements = document.head().select("meta");
        for (final Element meta : metaElements) {
            final String property = meta.attr("property");
            final String content = meta.attr("content");
            if ("og:url".equals(property)) {
                // https://www.databazeknih.cz/prehled-knihy/pripad-levoruke-damy-546691
                final int index = content.lastIndexOf('-');
                if (index > 0 && (index + 1) < content.length()) {
                    id = content.substring(index + 1);
                    book.setIdentifierValue(Identifier.SID_DATABAZE_KNIH, id);
                }
                // case "og:image": this is a small thumbnail only
            }
        }

        return id;
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

        String url = null;
        final Element img = document.selectFirst("img.kniha_img, img.kniha_img_audiobook");
        if (img != null) {
            url = img.attr("src");
        }
        if (url == null || url.contains("empty_bmid.jpg")) {
            return Optional.empty();
        }

        return saveImage(context, url, null, bookId, cIdx, null);
    }
}
