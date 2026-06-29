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
package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.core.utils.StringCoder;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Dutch language (and to some extent other languages) comics.
 * <p>
 * Current hardcoded to only search comics; could be extended to also search generic books.
 * <p>
 * {@link SearchEngine.ByBarcode}: for barcodes (explicitly supported by the site
 * and invalid ISBNs (which the site stores as-is on purpose)
 * <p>
 * ENHANCE: check if we can implement {@link SearchEngine.AlternativeEditions}
 * and consequently {@link SearchEngine.CoverByEdition}
 * Comics re-published by a different publisher do have different ISBN's.
 * We need to check if there is a way of finding those alternative ISBN numbers.
 * Same remark for StripInfo
 */
public class LastDodoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByBarcode,
                   SearchEngine.ByText,
                   SearchEngine.ByExternalId {

    private static final String SITE_URL = "https://www.lastdodo.nl";
    private static final String BOOK_URL = "https://www.lastdodo.nl/nl/items/%s";
    private static final String AUTHOR_URL = "https://www.lastdodo.nl/nl/areas/%s";
    private static final String SERIES_URL = "https://www.lastdodo.nl/nl/areas/%s";

    /**
     * Param 1: external book ID; really a 'long'.
     * Param 2: 147==comics
     */
    private static final String BY_EXTERNAL_ID = "/nl/items/%1$s";
    /**
     * Hardcoded to: 147==comics.
     * Param 1: The search word(s)
     * When searching for an ISBN, it must include the '-' characters! (2022-05-31)
     */
    private static final String SEARCH = "/nl/areas/search?type_id=147&q=%1$s";
    private static final Pattern REAL_NAME_BRACKET_ALIAS_BRACKET =
            Pattern.compile("(.*)\\(([a-z].*)\\)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Parse an id from the Author and Series urls It's a relative url. */
    private static final Pattern AREAS_ID = Pattern.compile(".*/areas/(\\d+)-.*");

    private final DateParser<PartialDate> dateParser = new PartialDateParser();
    private final AuthorResolverHelper authorResolverHelper;
    private final SeriesDao seriesDao;

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
    public LastDodoSearchEngine(@NonNull final Context appContext,
                                @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        seriesDao = ServiceLocator.getInstance().getSeriesDao();
        authorResolverHelper = new AuthorResolverHelper();
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
        return new EngineId.Builder("lastdodo",
                                    R.string.site_lastdodo_nl,
                                    List.of(R.string.site_description_dutch_and_more,
                                            R.string.site_description_catalog,
                                            R.string.site_description_eu_comics),
                                    SITE_URL,
                                    new Locale("nl", "NL"))
                .setIdentifierKeys(Identifier.SID_LAST_DODO_NL)
                .setPreferenceFragmentClazz(LastDodoPreferencesFragment.class)
                .setAuthorResolverSupplier(LastdodoAuthorResolver::create)
                .setConfig(cb -> cb
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_lastdodo_nl);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Number,
                               Identifier.SID_LAST_DODO_NL,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P10419"),
                new Identifier(Identifier.EntityType.Author,
                               Identifier.Type.Number,
                               Identifier.SID_LAST_DODO_NL,
                               name,
                               SITE_URL,
                               AUTHOR_URL,
                               "P10419"),
                new Identifier(Identifier.EntityType.Series,
                               Identifier.Type.Number,
                               Identifier.SID_LAST_DODO_NL,
                               name,
                               SITE_URL,
                               SERIES_URL,
                               "P10419")
        );
    }

    /**
     * ENHANCE: This method is functional, but not used for now.
     * <p>
     *     // We can strip the pseudonyms but can't really use them:
     *     // The problem is that the website lists the real-author name
     *     // as the actual book author instead of the pseudonym as stated ON the book.
     *     // e.g. consider a book "Robbedoes; by Rob-Vel"
     *     // will have the author as "Velter, Robert (Rob-Vel,Bozz)"
     *     // and we are simply not able to determine what to use.
     *     // i.e. we should use "Rob-Vel" as the author,
     *     // and set "Robert Velter" as the realAuthor.
     * <p>
     * Parse a name with potential pseudonyms.
     * <ol>
     * <li>"Robert Velter (Rob-vel,Bozz)"</li>
     * <li>"Robert Velter (Rob Vel)"</li>
     * <li>"Ange (1/2)"</li>
     * <li>"Don (*3)"</li>
     * </ol>
     * 1+2: The () part are pseudonyms.
     * 3: there are 2 people with the same name "Ange"; 1/2 and 2/2 makes the distinction.
     * 4: presumably there are 3 "Don"'s?
     * <p>
     * Assumption is that if the part between brackets starts with an alpha char,
     * then we assume that part to be a csv list of pseudonyms.
     * We decode the part before the brackets as a normal name.
     * <p>
     * In the case of a non-alpha, we will take the entire "(...)" part as the last name.
     * This is obviously not the best, but backwards compatible with what we did before.
     * <p>
     * See {@link Author#from(String)} for more notes on brackets.
     *
     * @param name to parse
     *
     * @return an array with [0] being the real-author-name;
     *         and optional [1..] with the pseudonyms found.
     *         The array is guaranteed to be length >= 1.
     */
    @VisibleForTesting
    @NonNull
    public static String[] parseAuthorNames(@NonNull final String name) {
        String uName = StringCoder.unEscape(name);
        final Matcher brackets = REAL_NAME_BRACKET_ALIAS_BRACKET.matcher(uName);
        if (brackets.find()) {
            String group = brackets.group(1);
            if (group != null) {
                uName = group.strip();
            }
            int len = 1;
            String[] pseudonyms = null;

            if (brackets.groupCount() > 1) {
                group = brackets.group(2);
                if (group != null) {
                    pseudonyms = group.strip().split(",");
                    len += pseudonyms.length;
                }
            }

            final String[] names = new String[len];
            names[0] = uName;
            if (pseudonyms != null) {
                System.arraycopy(pseudonyms, 0, names, 1, len - 1);
            }
            return names;
        } else {
            return new String[]{uName};
        }
    }

    /**
     * Takes a string which (hopefully) contains a 10 or 13 digit ISBN,
     * and formats it in the traditional way with '-' characters.
     * Any string of a different length is returned as-is;  a {@code null} becomes {@code ""}
     *
     * @param s to format
     *
     * @return dash formatted isbn
     */
    @NonNull
    private static String formatIsbnWithDashes(@Nullable final String s) {
        if (s == null) {
            return "";
        }

        if (s.length() == 10) {
            return s.substring(0, 2) + '-'
                   + s.substring(2, 6) + '-'
                   + s.substring(6, 9) + '-'
                   + s.charAt(9);
        }

        if (s.length() == 13) {
            return s.substring(0, 3) + '-'
                   + s.substring(3, 5) + '-'
                   + s.substring(5, 9) + '-'
                   + s.substring(9, 12) + '-'
                   + s.charAt(12);
        }

        return s;
    }

    @NonNull
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, CoverStorageException {

        final Book book = new Book();

        final String url = getHostUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final ProductCode productCode,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String codeStr = SearchEngineUtils.formatIsbn(getEngineId(), productCode);

        final Book book = new Book();

        // Reformat 10 or 13 digit codes to the site-required format,
        // whether they are valid ISBN or not.
        final String url = getHostUrl() + String.format(SEARCH, formatIsbnWithDashes(codeStr));

        final Document document = loadDocument(context, url, null);
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

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            final String codeStr = SearchEngineUtils.formatIsbn(getEngineId(), productCode);
            if (!codeStr.isEmpty()) {
                // Reformat 10 or 13 digit codes to the site-required format,
                // whether they are valid ISBN or not.
                words.add(formatIsbnWithDashes(codeStr));
            }
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        final String url = getHostUrl() + String.format(SEARCH, words);
        final Document document = loadDocument(context, url, null);
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
     * @throws CredentialsException  on authentication/login failures
     * @throws SearchException       on generic exceptions (wrapped) during search
     * @throws CoverStorageException The covers directory is not available
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws SearchException, CredentialsException, CoverStorageException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.card-body");
        // it will be null if there were no results.
        if (section == null) {
            return;
        }
        final Element urlElement = section.selectFirst("a");
        if (urlElement == null) {
            return;
        }
        String url = urlElement.attr("href");
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        final Document redirected = loadDocument(context, url, null);
        if (!isCancelled()) {
            parse(context, redirected, fetchCovers, book);
        }
    }

    private void parseCovers(@NonNull final Context context,
                             @NonNull final Document document,
                             @Nullable final String isbn,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws CoverStorageException {

        // https://assets.lastdodo.com/image/ld_medium/plain/assets/catalog/assets/1/4/8/d/pdf_48dea410-1a0e-012b-985d-f5c6b2a918e0.jpg

        // 2023-09-28: "thumbnails"
        // https://assets.lastdodo.com/image/ld_thumb1/plain/assets/catalog/assets/1/4/8/d/pdf_48dea410-1a0e-012b-985d-f5c6b2a918e0.jpg
        // https://assets.lastdodo.com/image/ld_thumb1/plain/assets/catalog/assets/1/4/8/e/pdf_48e35cb0-1a0e-012b-985d-f5c6b2a918e0.jpg
        // -> replace the substring to get the full size image

        final Elements container = document.getElementsByClass("thumbnails");
        // 0==front-cover; 1==back-cover; 2+ are extra images
        final Elements images = container.get(0).select("img");
        for (int cIdx = 0; cIdx < fetchCovers.length; cIdx++) {
            if (isCancelled()) {
                return;
            }
            // Should we fetch && is there one to fetch?
            if (fetchCovers[cIdx] && images.size() > cIdx) {
                final String url = images.get(cIdx).attr("src")
                                         .replace("/ld_thumb1/", "/ld_medium/");
                final int finalCIdx = cIdx;
                saveImage(context, url, null, isbn, cIdx, null).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, finalCIdx, fileSpec));
            }
        }
    }

    /**
     * Extract the (optional) table of content from the header.
     * <p>
     * <strong>Note:</strong> should only be called <strong>AFTER</strong> we have processed
     * the authors as we use the first Author of the book for all TOCEntries.
     * <p>
     * This is likely not correct, but the alternative is to store each entry in a TOC
     * as an individual book, and declare a Book TOC as a list of books.
     * i.o.w. the database structure would need to become
     * table: titles (book and toc-entry titles) each entry referencing 1..n authors.
     * table: books, with a primary title, and a list of secondary titles (i.e. the toc).
     * (All of which referencing the 'titles' table)
     * <p>
     * This is not practical in the scope of this application.
     *
     * @param context  Current context
     * @param sections to parse
     * @param book     Bundle to update
     *
     * @return the toc list with either {@code 0} or {@code 2} or more entries
     */
    @NonNull
    private List<TocEntry> parseToc(@NonNull final Context context,
                                    @NonNull final Collection<Element> sections,
                                    @NonNull final Book book) {

        // section 0 was the "Catalogusgegevens"; normally section 3 is the one we need here...
        Element tocSection = null;
        for (final Element section : sections) {
            final Element sectionTitle = section.selectFirst("h2.section-title");
            if (sectionTitle != null) {
                if ("Verhalen in dit album".equals(sectionTitle.text())) {
                    tocSection = section;
                    break;
                }
            }
        }


        if (tocSection != null) {
            // always use the first author only for TOC entries.
            Author tocAuthor = book.getPrimaryAuthor();
            if (tocAuthor == null) {
                tocAuthor = Author.createUnknownAuthor(context);
            }

            final List<TocEntry> toc = new ArrayList<>();
            for (final Element divRows : tocSection.select("div.row-information")) {
                final Element th = divRows.selectFirst("div.label");
                final Element td = divRows.selectFirst("div.value");
                if (th != null && td != null) {
                    if ("Verhaaltitel".equals(th.text())) {
                        toc.add(new TocEntry(tocAuthor, td.text()));
                    }
                }
            }

            if (toc.size() > 1) {
                return toc;
            }
        }
        return List.of();
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
     * @throws CoverStorageException The covers directory is not available
     * @throws SearchException       on generic exceptions (wrapped) during search
     * @throws CredentialsException  on authentication/login failures
     *                               This should only occur if the engine calls/relies on
     *                               secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws SearchException, CredentialsException, CoverStorageException {

        //noinspection NonConstantStringShouldBeStringBuffer
        String tmpSeriesNr = null;

        final Elements sections = document.select("section.inner");
        if (sections.isEmpty()) {
            return;
        }

        final Element sectionTitle = sections.get(0).selectFirst("h2.section-title");
        if (sectionTitle == null || !"Catalogusgegevens".equals(sectionTitle.text())) {
            return;
        }

        String tmpString;

        for (final Element divRows : sections.get(0).select("div.row-information")) {
            final Element th = divRows.selectFirst("div.label");
            final Element td = divRows.selectFirst("div.value");
            if (th != null && td != null) {

                switch (th.text()) {
                    case "LastDodo nummer": {
                        final String sid = SearchEngineUtils.cleanText(td);
                        book.setIdentifierValue(Identifier.SID_LAST_DODO_NL, sid);
                        break;
                    }
                    case "Titel": {
                        book.setTitle(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    case "Serie / held": {
                        parseSeries(td, book);
                        break;
                    }
                    case "Reeks": {
                        final String text = SearchEngineUtils.cleanText(td.child(0));
                        if (!text.isBlank()) {
                            book.putString(SiteField.REEKS, text);
                        }
                        break;
                    }
                    case "Nummer in reeks": {
                        tmpSeriesNr = td.text();
                        break;
                    }
                    case "Nummertoevoeging": {
                        tmpString = td.text();
                        if (!tmpString.isEmpty()) {
                            // this entry (number-suffix) can exist without a previous
                            // number field.
                            if (tmpSeriesNr == null || tmpSeriesNr.isEmpty()) {
                                tmpSeriesNr = tmpString;
                            } else {
                                //noinspection StringConcatenationInLoop
                                tmpSeriesNr += '|' + tmpString;
                            }
                        }
                        break;
                    }
                    case "Tekenaar": {
                        parseAuthor(td, AuthorRole.ARTIST, book);
                        break;
                    }
                    case "Scenarist": {
                        parseAuthor(td, AuthorRole.WRITER, book);
                        break;
                    }
                    case "Vertaler": {
                        parseAuthor(td, AuthorRole.TRANSLATOR, book);
                        break;
                    }
                    case "Inkter": {
                        parseAuthor(td, AuthorRole.INKING, book);
                        break;
                    }
                    case "Inkleurder": {
                        parseAuthor(td, AuthorRole.COLORIST, book);
                        break;
                    }
                    case "Uitgeverij": {
                        parsePublisher(td, book);
                        break;
                    }
                    case "Jaar": {
                        final String text = SearchEngineUtils.cleanText(td);
                        if (!text.isBlank()) {
                            dateParser.parse(text).ifPresent(book::setPublicationDate);
                        }
                        break;
                    }
                    case "Cover": {
                        book.setFormat(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    case "Druk": {
                        final String text = SearchEngineUtils.cleanText(td);
                        if (!text.isBlank()) {
                            book.putString(SiteField.PRINTING, text);
                        }
                        break;
                    }
                    case "Inkleuring": {
                        book.setColor(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    case "ISBN": {
                        tmpString = td.text();
                        if (!"Geen".equals(tmpString)) {
                            book.setRawProductCode(ISBN.cleanText(tmpString));
                        }
                        break;
                    }
                    case "Oplage": {
                        book.setPrintRun(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    case "Aantal bladzijden": {
                        book.setPages(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    case "Afmetingen": {
                        if (!"? x ? cm".equals(td.text())) {
                            final String text = SearchEngineUtils.cleanText(td);
                            if (!text.isBlank()) {
                                book.putString(SiteField.SIZE, text);
                            }
                        }
                        break;
                    }
                    case "Soort": {
                        processType(td, book);
                        break;
                    }
                    case "Taal / dialect": {
                        book.setLanguage(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    case "Bijzonderheden": {
                        book.setDescription(SearchEngineUtils.cleanText(td));
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        // post-process all found data.


        normaliseSeriesTitles(context, book);

        // It seems the site only lists a single number,
        // although a book can be in several Series.
        if (tmpSeriesNr != null && !tmpSeriesNr.isEmpty()) {
            final List<Series> seriesList = book.getSeries();
            if (seriesList.size() == 1) {
                final Series series = seriesList.get(0);
                series.setNumber(tmpSeriesNr);
            } else if (seriesList.size() > 1) {
                // tricky.... add it to a single series ? which one ? or to all ? or none ?
                // Whatever we choose, it's probably wrong.
                // We'll arbitrarily go with a single one, the last one.
                final Series series = seriesList.get(seriesList.size() - 1);
                series.setNumber(tmpSeriesNr);
            }
        }

        // We DON'T store a toc with a single entry (i.e. the book title itself).
        final List<TocEntry> toc = parseToc(context, sections, book);
        if (!toc.isEmpty()) {
            book.setToc(toc);
            if (TocEntry.hasMultipleAuthors(toc)) {
                book.setContentType(Book.ContentType.Anthology);
            } else {
                book.setContentType(Book.ContentType.Collection);
            }
        }

        authorResolverHelper.resolve(context, this, book);

        // It's extremely unlikely, but should the language be missing, add Dutch.
        if (!book.contains(DBKey.LANGUAGE)) {
            book.setLanguage("nld");
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0] || fetchCovers[1]) {
            parseCovers(context, document, book.getRawProductCode(), fetchCovers, book);
        }
    }

    /**
     * The site uses a mix of "The title" and "Title, The".
     * We need to explicitly re-normalise the second format.
     *
     * @param context Current context
     * @param book    to process
     */
    @VisibleForTesting
    public void normaliseSeriesTitles(@NonNull final Context context,
                                      @NonNull final Book book) {

        final List<Series> seriesList = book.getSeries();
        if (seriesList.isEmpty()) {
            return;
        }
        // Determine the book locale as best as we can
        final String language = book.getLanguage();
        @NonNull
        final Locale locale;
        if (language.isBlank()) {
            // No book language -> use site Locale
            locale = getLocale(context);
        } else {
            // Get the Locale from the language,
            // but if that fails use the site Locale
            final Locale userLocale = context.getResources().getConfiguration().getLocales()
                                             .get(0);
            locale = ServiceLocator.getInstance().getAppLocale()
                                   .getLocale(language, userLocale)
                                   .orElseGet(() -> getLocale(context));
        }

        // Force normalization!
        seriesDao.pruneList(context, seriesList, true, series -> locale);
    }

    /**
     * Found an Author.
     * <p>
     * The site uses a format of "family-name, givennames" but gets the typical
     * "Van", "De", ... family name prefixes wrongly in the given names part.
     * So we TRY to get around that before calling {@link Author#from(String)}.
     * <pre>
     *     "Astier, Laurent"
     *     "Tilburgh, Dieter Van"
     * </pre>
     *
     * @param td   data td
     * @param type of this entry
     * @param book Bundle to update
     */
    private void parseAuthor(@NonNull final Element td,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {

        for (final Element a : td.select("a")) {
            String text = a.text();
            if (text.contains(",")) {
                final String[] split = text.split(",");
                if (split.length == 2) {
                    text = split[1].strip() + ' ' + split[0].strip();
                }
            }
            final Author author = Author.from(SearchEngineUtils.cleanName(text));
            final String url = a.attr("href");
            final Matcher matcher = AREAS_ID.matcher(url);
            if (matcher.find()) {
                final String siId = matcher.group(1);
                if (siId != null) {
                    author.setIdentifierValue(Identifier.SID_LAST_DODO_NL, siId);
                }
            }
            addAuthor(author, type, book);
        }
    }

    /**
     * Found a Series.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void parseSeries(@NonNull final Element td,
                             @NonNull final Book book) {
        for (final Element a : td.select("a")) {
            final String title = SearchEngineUtils.cleanName(a);
            if (!title.isBlank()) {
                final Series series = Series.from(title);
                // "/nl/areas/4190831-venijn-het"
                final String url = a.attr("href");
                if (!url.isBlank()) {
                    final Matcher matcher = AREAS_ID.matcher(url);
                    if (matcher.find()) {
                        final String sid = matcher.group(1);
                        if (sid != null) {
                            series.setIdentifierValue(Identifier.SID_LAST_DODO_NL, sid);
                        }
                    }
                }
                book.add(series);
            }
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void parsePublisher(@NonNull final Element td,
                                @NonNull final Book book) {
        td.select("a")
          .stream()
          .map(SearchEngineUtils::cleanName)
          .filter(name -> !name.isBlank())
          .map(Publisher::from)
          .forEach(book::add);
    }

    private void processType(@NonNull final Element td,
                             @NonNull final Book book) {
        // there might be more than one; we only grab the first one here
        final Element a = td.child(0);
        book.putString(SiteField.TYPE, a.text());
    }

    /**
     * LastDodoField specific field names we add to the bundle based on parsed XML data.
     */
    public static final class SiteField {

        static final String PRINTING = "__printing";
        static final String SIZE = "__size";
        static final String TYPE = "__type";
        static final String REEKS = "__reeks";

        private SiteField() {
        }
    }
}
