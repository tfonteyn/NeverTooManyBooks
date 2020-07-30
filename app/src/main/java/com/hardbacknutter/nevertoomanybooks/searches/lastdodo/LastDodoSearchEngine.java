/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.lastdodo;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.util.ArrayList;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

/**
 * Current hardcoded to only search comics; could be extended to also search generic books.
 */
@SearchEngine.Configuration(
        id = SearchSites.LAST_DODO,
        nameResId = R.string.site_lastdodo_nl,
        url = "https://www.lastdodo.nl/",
        prefKey = "lastdodo",
        lang = "nl",
        country = "NL",
        domainKey = DBDefinitions.KEY_EID_LAST_DODO_NL,
        domainViewId = R.id.site_last_dodo_nl,
        domainMenuId = R.id.MENU_VIEW_BOOK_AT_LAST_DODO_NL,
        filenameSuffix = "LDD"
)
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
     * Constructor.
     *
     * @param appContext Application context
     */
    public LastDodoSearchEngine(@NonNull final Context appContext) {
        super(appContext);
    }

    @Override
    public void checkForSeriesNameInTitle(@NonNull final Bundle bookData) {
        // This site always has clean titles and uses ()
        // which causes the default cleaner to give false positives.
    }

    @NonNull
    @Override
    public String createUrl(@NonNull final String externalId) {
        return getSiteUrl() + String.format(BY_EXTERNAL_ID, externalId);
    }

    @NonNull
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

        final Bundle bookData = new Bundle();

        final String url = getSiteUrl() + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(url);
        if (document != null && !isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(document, fetchThumbnail, bookData);
        }
        return bookData;
    }


    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retries.
     *
     * @param document       to parse
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update
     *
     * @throws IOException on failure
     */
    @WorkerThread
    void parseMultiResult(@NonNull final Document document,
                          @NonNull final boolean[] fetchThumbnail,
                          @NonNull final Bundle bookData)
            throws IOException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.cw-lot_content");
        final Element urlElement = section.selectFirst("a");
        if (urlElement != null) {
            final Document redirected = loadDocument(urlElement.attr("href"));
            if (redirected != null && !isCancelled()) {
                parse(redirected, fetchThumbnail, bookData);
            }
        }
    }

    @Override
    @VisibleForTesting
    public void parse(@NonNull final Document document,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData) {

        //noinspection NonConstantStringShouldBeStringBuffer
        String tmpSeriesNr = null;

        final Element container = document.getElementById("catalogue_information");
        final Elements trs = container.select("tr");

        String tmpString;

        for (Element tr : trs) {
            final Element th = tr.child(0);
            final Element td = tr.child(1);
            switch (th.text()) {
                case "LastDodo nummer:":
                    processText(td, DBDefinitions.KEY_EID_LAST_DODO_NL, bookData);
                    break;

                case "Titel:":
                    processText(td, DBDefinitions.KEY_TITLE, bookData);
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
                    processText(td, DBDefinitions.KEY_DATE_PUBLISHED, bookData);
                    break;

                case "Cover:":
                    processText(td, DBDefinitions.KEY_FORMAT, bookData);
                    break;

                case "Druk:":
                    processText(td, SiteField.KEY_PRINTING, bookData);
                    break;

                case "Inkleuring:":
                    processText(td, DBDefinitions.KEY_COLOR, bookData);
                    break;

                case "ISBN:":
                    tmpString = td.text();
                    if (!"Geen".equals(tmpString)) {
                        tmpString = digits(tmpString, true);
                        if (!tmpString.isEmpty()) {
                            bookData.putString(DBDefinitions.KEY_ISBN, tmpString);
                        }
                    }
                    break;

                case "Oplage:":
                    processText(td, DBDefinitions.KEY_PRINT_RUN, bookData);
                    break;

                case "Aantal bladzijden:":
                    processText(td, DBDefinitions.KEY_PAGES, bookData);
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
                    processText(td, DBDefinitions.KEY_DESCRIPTION, bookData);
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
            bookData.putParcelableArrayList(Book.BKEY_TOC_ARRAY, toc);
            bookData.putLong(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS);
        }

        if (!mAuthors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mSeries.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_SERIES_ARRAY, mSeries);
        }
        if (!mPublishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY, mPublishers);
        }

        if (isCancelled()) {
            return;
        }

        if (fetchThumbnail[0] || fetchThumbnail[1]) {
            final String isbn = bookData.getString(DBDefinitions.KEY_ISBN);
            final Element images = document.getElementById("images_container");
            if (images != null) {
                final Elements aas = images.select("a");
                for (int cIdx = 0; cIdx < 2; cIdx++) {
                    if (isCancelled()) {
                        return;
                    }
                    if (fetchThumbnail[cIdx] && aas.size() > cIdx) {
                        final String url = aas.get(cIdx).attr("href");
                        final String tmpName = createFilename(isbn, cIdx, null);
                        final String fileSpec = saveImage(url, tmpName);
                        if (fileSpec != null) {
                            final ArrayList<String> imageList = new ArrayList<>();
                            imageList.add(fileSpec);
                            bookData.putStringArrayList(Book.BKEY_FILE_SPEC_ARRAY[cIdx], imageList);
                        }
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
            final Elements entries = section.select("tr:contains(Verhaaltitel)");
            for (Element tr : entries) {
                final String title = tr.child(1).text();
                toc.add(new TocEntry(mAuthors.get(0), title, null));
            }
            return toc;
        }
        return null;
    }


    /**
     * Found a Series.
     *
     * @param td data td
     */
    private void processSeries(@NonNull final Element td) {
        final Elements aas = td.select("a");
        for (Element a : aas) {
            final String name = a.text();
            final Series currentSeries = Series.from(name);
            // check if already present
            for (Series series : mSeries) {
                if (series.equals(currentSeries)) {
                    return;
                }
            }
            // just add
            mSeries.add(currentSeries);
        }
    }

    /**
     * Found an Author.
     *
     * @param td                data td
     * @param currentAuthorType of this entry
     */
    private void processAuthor(@NonNull final Element td,
                               @Author.Type final int currentAuthorType) {

        final Elements aas = td.select("a");
        for (Element a : aas) {
            final String name = a.text();
            final Author currentAuthor = Author.from(name);
            boolean add = true;
            // check if already present
            for (Author author : mAuthors) {
                if (author.equals(currentAuthor)) {
                    // merge types.
                    author.addType(currentAuthorType);
                    add = false;
                }
            }

            if (add) {
                currentAuthor.setType(currentAuthorType);
                mAuthors.add(currentAuthor);
            }
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td data td
     */
    private void processPublisher(@NonNull final Element td) {
        final Elements aas = td.select("a");
        for (Element a : aas) {
            final String name = cleanText(a.text());
            final Publisher currentPublisher = Publisher.from(name);
            // check if already present
            for (Publisher publisher : mPublishers) {
                if (publisher.equals(currentPublisher)) {
                    return;
                }
            }
            // just add
            mPublishers.add(currentPublisher);
        }

    }

    private void processLanguage(@NonNull final Element td,
                                 @NonNull final Bundle bookData) {
        processText(td, DBDefinitions.KEY_LANGUAGE, bookData);
        String lang = bookData.getString(DBDefinitions.KEY_LANGUAGE);
        if (lang != null && !lang.isEmpty()) {
            lang = LanguageUtils.getISO3FromDisplayName(mAppContext, getLocale(), lang);
            bookData.putString(DBDefinitions.KEY_LANGUAGE, lang);
        }
    }

    private void processType(@NonNull final Element td,
                             @NonNull final Bundle bookData) {
        // there might be more then one; we only grab the first one here
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
        public static final String KEY_PRINTING = "__printing";
        public static final String KEY_SIZE = "__size";
        public static final String KEY_TYPE = "__type";
        public static final String KEY_REEKS = "__reeks";

        private SiteField() {
        }
    }
}
