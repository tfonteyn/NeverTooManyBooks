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
package com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.StringCoder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.bedetheque.BedethequeAuthorResolver;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Current hardcoded to only search comics; could be extended to also search generic books.
 */
public class LastDodoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId {

    static final String PK_RESOLVE_AUTHORS_ON_BEDETHEQUE = "lastdodo.resolve.authors.bedetheque";

    /**
     * Param 1: external book ID; really a 'long'.
     * Param 2: 147==comics
     */
    private static final String BY_EXTERNAL_ID = "/nl/items/%1$s";
    /**
     * Param 1: ISBN. Must include the '-' characters! (2022-05-31)
     * Param 2: 147==comics.
     */
    private static final String BY_ISBN = "/nl/areas/search?q=%1$s&type_id=147";
    private static final Pattern REAL_NAME_BRACKET_ALIAS_BRACKET =
            Pattern.compile("(.*)\\(([a-z].*)\\)",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public LastDodoSearchEngine(@NonNull final Context appContext,
                                @NonNull final SearchEngineConfig config) {
        super(appContext, config);
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
     * 4: presumably there are 3 Don's?
     * <p>
     * Assumption is that if the part between brackets starts with a alpha char,
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

    @Nullable
    private AuthorResolver getAuthorResolver(@NonNull final Context context) {
        if (ServiceLocator.getInstance().getGlobalFieldVisibility()
                          .isShowField(DBKey.AUTHOR_REAL_AUTHOR).orElse(false)
            && PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(PK_RESOLVE_AUTHORS_ON_BEDETHEQUE, false)) {
            return new BedethequeAuthorResolver(context, this);
        } else {
            return null;
        }
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final Context context,
                                   @NonNull final String externalId) {
        return getHostUrl(context) + String.format(BY_EXTERNAL_ID, externalId);
    }

    @NonNull
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book, getAuthorResolver(context));
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        // This is silly...
        // 2022-05-31: searching the site with the ISBN now REQUIRES the dashes between
        // the digits.
        final String url = getHostUrl(context) + String.format(BY_ISBN, ISBN.formatIsbn(validIsbn));
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
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.card-body");
        // it will be null if there were no results.
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                String url = urlElement.attr("href");
                // sanity check - it normally does NOT have the protocol/site part
                if (url.startsWith("/")) {
                    url = getHostUrl(context) + url;
                }
                final Document redirected = loadDocument(context, url, null);
                if (!isCancelled()) {
                    parse(context, redirected, fetchCovers, book, getAuthorResolver(context));
                }
            }
        }
    }

    private void parseCovers(@NonNull final Context context,
                             @NonNull final Document document,
                             @Nullable final String isbn,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        final Element images = document.getElementById("images_container");
        if (images != null) {
            final Elements aas = images.select("a");
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                if (isCancelled()) {
                    return;
                }
                if (fetchCovers[cIdx] && aas.size() > cIdx) {
                    final String url = aas.get(cIdx).attr("href");
                    final String fileSpec = saveImage(context, url, isbn, cIdx, null);
                    if (fileSpec != null) {
                        final ArrayList<String> list = new ArrayList<>();
                        list.add(fileSpec);
                        book.putStringArrayList(
                                SearchCoordinator.BKEY_FILE_SPEC_ARRAY[cIdx], list);
                    }
                }
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
     * table: books, with a primary title, and a list of secondary titles (i.e the toc).
     * (All of which referencing the 'titles' table)
     * <p>
     * This is not practical in the scope of this application.
     *
     * @param sections to parse
     * @param book     Bundle to update
     *
     * @return the toc list with either {@code 0} or {@code 2} or more entries
     */
    @NonNull
    private List<TocEntry> parseToc(@NonNull final Collection<Element> sections,
                                    @NonNull final Book book) {

        // section 0 was the "Catalogusgegevens"; normally section 3 is the one we need here...
        Element tocSection = null;
        for (final Element section : sections) {
            final Element sectionTitle = section.selectFirst("h2.section-title");
            if (sectionTitle != null) {
                if ("Verhalen in dit album" .equals(sectionTitle.text())) {
                    tocSection = section;
                    break;
                }
            }
        }


        if (tocSection != null) {
            final List<TocEntry> toc = new ArrayList<>();
            for (final Element divRows : tocSection.select("div.row-information")) {
                final Element th = divRows.selectFirst("div.label");
                final Element td = divRows.selectFirst("div.value");
                if (th != null && td != null) {
                    if ("Verhaaltitel" .equals(th.text())) {
                        toc.add(new TocEntry(book.getAuthors().get(0), td.text()));
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
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context        Current context
     * @param document       to parse
     * @param fetchCovers    Set to {@code true} if we want to get covers
     *                       The array is guaranteed to have at least one element.
     * @param book           Bundle to update
     * @param authorResolver (optional) {@link AuthorResolver} to use
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
                      @NonNull final Book book,
                      @Nullable final AuthorResolver authorResolver)
            throws StorageException, SearchException, CredentialsException {

        //noinspection NonConstantStringShouldBeStringBuffer
        String tmpSeriesNr = null;

        final Elements sections = document.select("section.inner");
        if (sections.size() < 1) {
            return;
        }

        final Element sectionTitle = sections.get(0).selectFirst("h2.section-title");
        if (sectionTitle == null || !"Catalogusgegevens" .equals(sectionTitle.text())) {
            return;
        }

        String tmpString;

        for (final Element divRows : sections.get(0).select("div.row-information")) {
            final Element th = divRows.selectFirst("div.label");
            final Element td = divRows.selectFirst("div.value");
            if (th != null && td != null) {

                switch (th.text()) {
                    case "LastDodo nummer":
                        processText(td, DBKey.SID_LAST_DODO_NL, book);
                        break;

                    case "Titel":
                        processText(td, DBKey.TITLE, book);
                        break;

                    case "Serie / held":
                        processSeries(td, book);
                        break;

                    case "Reeks":
                        processText(td.child(0), SiteField.REEKS, book);
                        break;

                    case "Nummer in reeks":
                        tmpSeriesNr = td.text();
                        break;

                    case "Nummertoevoeging":
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

                    case "Tekenaar": {
                        processAuthor(td, Author.TYPE_ARTIST, book);
                        break;
                    }
                    case "Scenarist": {
                        processAuthor(td, Author.TYPE_WRITER, book);
                        break;
                    }
                    case "Uitgeverij":
                        processPublisher(td, book);
                        break;

                    case "Jaar":
                        processText(td, DBKey.BOOK_PUBLICATION__DATE, book);
                        break;

                    case "Cover":
                        processText(td, DBKey.FORMAT, book);
                        break;

                    case "Druk":
                        processText(td, SiteField.PRINTING, book);
                        break;

                    case "Inkleuring":
                        processText(td, DBKey.COLOR, book);
                        break;

                    case "ISBN":
                        tmpString = td.text();
                        if (!"Geen" .equals(tmpString)) {
                            tmpString = ISBN.cleanText(tmpString);
                            if (!tmpString.isEmpty()) {
                                book.putString(DBKey.BOOK_ISBN, tmpString);
                            }
                        }
                        break;

                    case "Oplage":
                        processText(td, DBKey.PRINT_RUN, book);
                        break;

                    case "Aantal bladzijden":
                        processText(td, DBKey.PAGE_COUNT, book);
                        break;

                    case "Afmetingen":
                        if (!"? x ? cm" .equals(td.text())) {
                            processText(td, SiteField.SIZE, book);
                        }
                        break;

                    case "Taal / dialect":
                        processText(td, DBKey.LANGUAGE, book);
                        break;

                    case "Soort":
                        processType(td, book);
                        break;

                    case "Bijzonderheden":
                        processText(td, DBKey.DESCRIPTION, book);
                        break;

                    default:
                        break;
                }
            }
        }

        // post-process all found data.

        // It seems the site only lists a single number, although a book can be in several
        // Series.
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
        final List<TocEntry> toc = parseToc(sections, book);
        if (!toc.isEmpty()) {
            book.setToc(toc);
            if (TocEntry.hasMultipleAuthors(toc)) {
                book.putLong(DBKey.BOOK_CONTENT_TYPE, Book.ContentType.Anthology.getId());
            } else {
                book.putLong(DBKey.BOOK_CONTENT_TYPE, Book.ContentType.Collection.getId());
            }
        }

        if (!book.getAuthors().isEmpty() && authorResolver != null) {
            for (final Author author : book.getAuthors()) {
                authorResolver.resolve(author);
            }
        }

        // It's extremely unlikely, but should the language be missing, add dutch.
        if (!book.contains(DBKey.LANGUAGE)) {
            book.putString(DBKey.LANGUAGE, "nld");
        }


        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0] || fetchCovers[1]) {
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            parseCovers(context, document, isbn, fetchCovers, book);
        }
    }

    /**
     * Found an Author.
     *
     * @param td   data td
     * @param type of this entry
     * @param book Bundle to update
     */
    private void processAuthor(@NonNull final Element td,
                               @Author.Type final int type,
                               @NonNull final Book book) {

        for (final Element a : td.select("a")) {
            processAuthor(Author.from(a.text()), type, book);
        }
    }

    /**
     * Found a Series.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void processSeries(@NonNull final Element td,
                               @NonNull final Book book) {
        for (final Element a : td.select("a")) {
            final String name = a.text();
            final Series currentSeries = Series.from(name);
            // check if already present
            if (book.getSeries().stream().anyMatch(series -> series.equals(currentSeries))) {
                return;
            }
            // just add
            book.add(currentSeries);
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void processPublisher(@NonNull final Element td,
                                  @NonNull final Book book) {
        for (final Element a : td.select("a")) {
            final String name = SearchEngineUtils.cleanText(a.text());
            final Publisher currentPublisher = Publisher.from(name);
            // check if already present
            if (book.getPublishers().stream().anyMatch(pub -> pub.equals(currentPublisher))) {
                return;
            }
            // just add
            book.add(currentPublisher);
        }

    }

    private void processType(@NonNull final Element td,
                             @NonNull final Book book) {
        // there might be more than one; we only grab the first one here
        final Element a = td.child(0);
        book.putString(SiteField.TYPE, a.text());
    }

    /**
     * Process a td which is pure text.
     *
     * @param td   label td
     * @param key  for this field
     * @param book Bundle to update
     */
    private void processText(@Nullable final Element td,
                             @NonNull final String key,
                             @NonNull final Book book) {
        if (td != null) {
            final String text = SearchEngineUtils.cleanText(td.text());
            if (!text.isEmpty()) {
                book.putString(key, text);
            }
        }
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
