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

package com.hardbacknutter.nevertoomanybooks.searchengines.bookfinder;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

public class BookFinderSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    private static final String TAG = "BookFinderSearchEngine";

    private static final String BY_ISBN = "/search_s/?st=sr&ac=qr&mode=basic"
                                          + "&author="
                                          + "&title="
                                          + "&isbn=%1$s"
                                          + "&lang=en"
                                          + "&destination=us"
                                          + "&currency=USD"
                                          + "&binding=*"
                                          + "&keywords="
                                          + "&publisher="
                                          + "&min_year="
                                          + "&max_year="
                                          + "&minprice="
                                          + "&maxprice=";

    private final RatingParser ratingParser;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BookFinderSearchEngine(@NonNull final Context appContext,
                                  @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        ratingParser = new RatingParser(5);
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.
        return getLocale(context, getHostUrl(context));
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final String url = getHostUrl(context) + String.format(BY_ISBN, validIsbn);
        final Document document = loadDocument(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Element bookInfo = document.selectFirst("div#book-info");
        if (bookInfo == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no bookInfo?");
            return;
        }

        final Element titleElement = bookInfo.selectFirst("h1.bf-content-header-book-title > a");
        if (titleElement == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no title?");
            return;
        }

        final String title = titleElement.text();
        book.putString(DBKey.TITLE, title);

        final RealNumberParser realNumberParser = getRealNumberParser(context, getLocale(context));

        final Element authorElement = bookInfo.selectFirst(
                "div.bf-content-header-book-author > p > strong > a");
        if (authorElement != null) {
            addAuthor(Author.from(authorElement.text()), Author.TYPE_UNKNOWN, book);
        }
        final Element ratingElement = bookInfo.selectFirst("div.rating"
                                                           + " > span.book-rating-average");
        if (ratingElement != null) {
            final String[] s = ratingElement.text().split(" ", 2);
            ratingParser.parse(s[0]).ifPresent(
                    rating -> book.putFloat(DBKey.RATING, rating));
        }

        final Elements details = bookInfo.select("div > strong");
        for (final Element label : details) {
            final Node valueElement = label.nextSibling();
            if (valueElement != null) {
                // The value has CR or LF's and spaces. use strip() !
                final String value = valueElement.toString().strip();
                if (!value.isBlank()) {
                    switch (label.text()) {
                        case "ISBN:": {
                            final String[] s = value.split("/");
                            if (s.length > 0 && !s[0].isBlank()) {
                                book.putString(DBKey.BOOK_ISBN, s[0].strip());
                            }
                            break;
                        }
                        case "Publisher:": {
                            final String[] s = value.split(",");
                            if (s.length > 0 && !s[0].isBlank()) {
                                book.add(Publisher.from(s[0].strip()));
                                if (s.length > 1 && !s[1].isBlank()) {
                                    addPublicationDate(context, getLocale(context),
                                                       s[1].strip(), book);
                                }
                            }
                            break;
                        }
                        case "Edition:": {
                            book.putString(DBKey.FORMAT, value);
                            break;
                        }
                        case "Language:": {
                            book.putString(DBKey.LANGUAGE, value);
                            break;
                        }
                    }
                }
            }
        }

        final Element description = document.selectFirst("div#bookSummary > p");
        if (description != null) {
            final String text = description.html();
            if (!text.isBlank()) {
                book.putString(DBKey.DESCRIPTION, text.strip());
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCovers(context, document, book).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    /**
     * Parses the downloaded {@link Document} for the cover and fetches it when present.
     *
     * @param context  Current context
     * @param document to parse
     * @param book     to update
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @VisibleForTesting
    @NonNull
    private Optional<String> parseCovers(@NonNull final Context context,
                                         @NonNull final Document document,
                                         @NonNull final Book book)
            throws StorageException {

        final Element img = document.selectFirst("div#header-img > img");
        if (img != null) {
            final String url = img.attr("src");
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            return saveImage(context, url, isbn, 0, null);
        }
        return Optional.empty();
    }
}
