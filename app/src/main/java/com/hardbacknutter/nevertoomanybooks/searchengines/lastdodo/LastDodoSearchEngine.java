/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * Current hardcoded to only search comics; could be extended to also search generic books.
 */
public class LastDodoSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId {

    /**
     * Param 1: external book ID; really a 'long'.
     * Param 2: 147==comics
     */
    private static final String BY_EXTERNAL_ID = "/search?q=%1$s&type=147";
    /**
     * Param 1: ISBN.
     * Param 2: 147==comics.
     */
    private static final String BY_ISBN = "/search?q=%1$s&type=147";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public LastDodoSearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(LastDodoSearchEngine.class,
                                              SearchSites.LAST_DODO,
                                              R.string.site_lastdodo_nl,
                                              "lastdodo",
                                              "https://www.lastdodo.nl/")
                .setCountry("NL", "nl")
                .setFilenameSuffix("LDD")

                .setDomainKey(DBKey.SID_LAST_DODO_NL)
                .setDomainViewId(R.id.site_last_dodo_nl)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_LAST_DODO_NL)
                .build();
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
    }

    @NonNull
    public Bundle searchByExternalId(@NonNull final String externalId,
                                     @NonNull final boolean[] fetchCovers)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
        final Document document = loadDocument(url);
        if (!isCancelled()) {
            parse(document, fetchCovers, bookData);
        }
        return bookData;
    }

    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(url);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(document, fetchCovers, bookData);
        }
        return bookData;
    }


    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retries.
     *
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.cw-lot_content");
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                final Document redirected = loadDocument(urlElement.attr("href"));
                if (!isCancelled()) {
                    parse(redirected, fetchCovers, bookData);
                }
            }
        }
    }

    @Override
    @VisibleForTesting
    public void parse(@NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException, SearchException {
        super.parse(document, fetchCovers, bookData);

        //noinspection NonConstantStringShouldBeStringBuffer
        String tmpSeriesNr = null;

        final Element container = document.getElementById("catalogue_information");

        String tmpString;

        for (final Element tr : container.select("tr")) {
            final Element th = tr.child(0);
            final Element td = tr.child(1);
            switch (th.text()) {
                case "LastDodo nummer:":
                    processText(td, DBKey.SID_LAST_DODO_NL, bookData);
                    break;

                case "Titel:":
                    processText(td, DBKey.KEY_TITLE, bookData);
                    break;

                case "Serie / held:":
                    processSeries(td);
                    break;

                case "Reeks:":
                    processText(td.child(0), SiteField.KEY_REEKS, bookData);
                    break;

                case "Nummer in reeks:":
                    tmpSeriesNr = td.text().trim();
                    break;

                case "Nummertoevoeging:":
                    tmpString = td.text().trim();
                    if (!tmpString.isEmpty()) {
                        //noinspection StringConcatenationInLoop
                        tmpSeriesNr += '|' + tmpString;
                    }
                    break;

                case "Tekenaar:": {
                    processAuthor(td, Author.TYPE_ARTIST);
                    break;
                }
                case "Scenarist:": {
                    processAuthor(td, Author.TYPE_WRITER);
                    break;
                }
                case "Uitgeverij:":
                    processPublisher(td);
                    break;

                case "Jaar:":
                    processText(td, DBKey.DATE_BOOK_PUBLICATION, bookData);
                    break;

                case "Cover:":
                    processText(td, DBKey.KEY_FORMAT, bookData);
                    break;

                case "Druk:":
                    processText(td, SiteField.KEY_PRINTING, bookData);
                    break;

                case "Inkleuring:":
                    processText(td, DBKey.KEY_COLOR, bookData);
                    break;

                case "ISBN:":
                    tmpString = td.text();
                    if (!"Geen".equals(tmpString)) {
                        tmpString = digits(tmpString, true);
                        if (!tmpString.isEmpty()) {
                            bookData.putString(DBKey.KEY_ISBN, tmpString);
                        }
                    }
                    break;

                case "Oplage:":
                    processText(td, DBKey.KEY_PRINT_RUN, bookData);
                    break;

                case "Aantal bladzijden:":
                    processText(td, DBKey.KEY_PAGES, bookData);
                    break;

                case "Afmetingen:":
                    if (!"? x ? cm".equals(td.text())) {
                        processText(td, SiteField.KEY_SIZE, bookData);
                    }
                    break;

                case "Taal / dialect:":
                    processLanguage(td, bookData);
                    break;

                case "Soort:":
                    processType(td, bookData);
                    break;

                case "Bijzonderheden:":
                    processText(td, DBKey.KEY_DESCRIPTION, bookData);
                    break;

                default:
                    break;
            }
        }

        // It seems the site only lists a single number, although a book can be in several
        // Series.
        if (tmpSeriesNr != null && !tmpSeriesNr.isEmpty()) {
            if (mSeries.size() == 1) {
                final Series series = mSeries.get(0);
                series.setNumber(tmpSeriesNr);
            } else if (mSeries.size() > 1) {
                // tricky.... add it to a single series ? which one ? or to all ? or none ?
                // Whatever we choose, it's probably wrong.
                // We'll arbitrarily go with a single one, the last one.
                final Series series = mSeries.get(mSeries.size() - 1);
                series.setNumber(tmpSeriesNr);
            }
        }

        final ArrayList<TocEntry> toc = parseToc(document);
        // We DON'T store a toc with a single entry (i.e. the book title itself).
        if (toc != null && toc.size() > 1) {
            bookData.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
            bookData.putLong(DBKey.BITMASK_TOC, Book.TOC_MULTIPLE_WORKS);
        }

        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_LIST, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, mPublishers);
        }

        // It's extremely unlikely, but should the language be missing, add dutch.
        if (!bookData.containsKey(DBKey.KEY_LANGUAGE)) {
            bookData.putString(DBKey.KEY_LANGUAGE, "nld");
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0] || fetchCovers[1]) {
            final String isbn = bookData.getString(DBKey.KEY_ISBN);
            parseCovers(document, isbn, fetchCovers, bookData);
        }
    }

    private void parseCovers(@NonNull final Document document,
                             @Nullable final String isbn,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Bundle bookData)
            throws DiskFullException, CoverStorageException {
        final Element images = document.getElementById("images_container");
        if (images != null) {
            final Elements aas = images.select("a");
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                if (isCancelled()) {
                    return;
                }
                if (fetchCovers[cIdx] && aas.size() > cIdx) {
                    final String url = aas.get(cIdx).attr("href");
                    final String fileSpec = saveImage(url, isbn, cIdx, null);
                    if (fileSpec != null) {
                        final ArrayList<String> list = new ArrayList<>();
                        list.add(fileSpec);
                        bookData.putStringArrayList(
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
     * @param document to parse
     *
     * @return toc list
     */
    @Nullable
    private ArrayList<TocEntry> parseToc(@NonNull final Document document) {
        Element section = document.selectFirst("legend:contains(Verhalen in dit Album)");
        if (section != null) {
            final ArrayList<TocEntry> toc = new ArrayList<>();
            section = section.nextElementSibling();
            for (final Element tr : section.select("tr:contains(Verhaaltitel)")) {
                toc.add(new TocEntry(mAuthors.get(0), tr.child(1).text(), null));
            }
            return toc;
        }
        return null;
    }


    /**
     * Found an Author.
     *
     * @param td                data td
     * @param currentAuthorType of this entry
     */
    private void processAuthor(@NonNull final Element td,
                               @Author.Type final int currentAuthorType) {

        for (final Element a : td.select("a")) {
            final String name = a.text();
            final Author currentAuthor = Author.from(name);
            boolean add = true;
            // check if already present
            for (final Author author : mAuthors) {
                if (author.equals(currentAuthor)) {
                    // merge types.
                    author.addType(currentAuthorType);
                    add = false;
                    // keep looping
                }
            }

            if (add) {
                currentAuthor.setType(currentAuthorType);
                mAuthors.add(currentAuthor);
            }
        }
    }

    /**
     * Found a Series.
     *
     * @param td data td
     */
    private void processSeries(@NonNull final Element td) {
        for (final Element a : td.select("a")) {
            final String name = a.text();
            final Series currentSeries = Series.from(name);
            // check if already present
            if (mSeries.stream().anyMatch(series -> series.equals(currentSeries))) {
                return;
            }
            // just add
            mSeries.add(currentSeries);
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td data td
     */
    private void processPublisher(@NonNull final Element td) {
        for (final Element a : td.select("a")) {
            final String name = cleanText(a.text());
            final Publisher currentPublisher = Publisher.from(name);
            // check if already present
            if (mPublishers.stream().anyMatch(pub -> pub.equals(currentPublisher))) {
                return;
            }
            // just add
            mPublishers.add(currentPublisher);
        }

    }

    private void processLanguage(@NonNull final Element td,
                                 @NonNull final Bundle bookData) {
        processText(td, DBKey.KEY_LANGUAGE, bookData);
        String lang = bookData.getString(DBKey.KEY_LANGUAGE);
        if (lang != null && !lang.isEmpty()) {
            lang = ServiceLocator.getInstance().getLanguages()
                                 .getISO3FromDisplayName(getLocale(), lang);
            bookData.putString(DBKey.KEY_LANGUAGE, lang);
        }
    }

    private void processType(@NonNull final Element td,
                             @NonNull final Bundle bookData) {
        // there might be more than one; we only grab the first one here
        final Element a = td.child(0);
        if (a != null) {
            bookData.putString(SiteField.KEY_TYPE, a.text());
        }
    }

    /**
     * Process a td which is pure text.
     *
     * @param td       label td
     * @param key      for this field
     * @param bookData Bundle to update
     */
    private void processText(@Nullable final Element td,
                             @NonNull final String key,
                             @NonNull final Bundle bookData) {
        if (td != null) {
            final String text = cleanText(td.text().trim());
            if (!text.isEmpty()) {
                bookData.putString(key, text);
            }
        }
    }

    /**
     * LastDodoField specific field names we add to the bundle based on parsed XML data.
     */
    public static final class SiteField {

        /** The barcode (e.g. the EAN code) is not always an ISBN. */
        static final String KEY_PRINTING = "__printing";
        static final String KEY_SIZE = "__size";
        static final String KEY_TYPE = "__type";
        static final String KEY_REEKS = "__reeks";

        private SiteField() {
        }
    }
}
