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
package com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.core.parsers.UncheckedSAXException;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleListUtils;

import org.xml.sax.SAXException;

/**
 * ENHANCE: migrate to new googlebooks API or drop Google altogether?
 * <p>
 * The urls and xml formats used here are deprecated (but still works fine)
 * <a href="https://developers.google.com/gdata/docs/directory">gdata directory</a>
 * <a href="https://developers.google.com/gdata/docs/2.0/reference">gdata reference</a>
 * <p>
 * The new API:
 * <a href="https://developers.google.com/books/docs/v1/getting_started?csw=1">Getting started</a>
 * <p>
 * <a href="https://developers.google.com/books/terms.html">T&C</a>
 * You may not charge users any fee for the use of your application,...
 * => so it seems if this SearchEngine is included, the entire app has to be free.
 * <p>
 * example:
 * <a href="https://stackoverflow.com/questions/7908954">google-books-api-searching-by-isbn</a>
 */
public class GoogleBooksSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    private static final Pattern SPACE_LITERAL = Pattern.compile(" ", Pattern.LITERAL);
    @Nullable
    private FutureHttpGet<Boolean> futureHttpGet;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public GoogleBooksSearchEngine(@NonNull final Context appContext,
                                   @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        final Book book = new Book();

        // %3A  :
        final String url = getHostUrl(context) + "/books/feeds/volumes?q=ISBN%3A" + validIsbn;
        fetchBook(context, url, fetchCovers, book);
        return book;
    }

    @NonNull
    @Override
    @WorkerThread
    public Book search(@NonNull final Context context,
                       @Nullable final /* not supported */ String code,
                       @Nullable final String author,
                       @Nullable final String title,
                       @Nullable final /* not supported */ String publisher,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        final Book book = new Book();

        // %2B  +
        // %3A  :
        if (author != null && !author.isEmpty()
            && title != null && !title.isEmpty()) {
            final String url = getHostUrl(context) + "/books/feeds/volumes?q="
                               + "intitle%3A" + encodeSpaces(title)
                               + "%2B"
                               + "inauthor%3A" + encodeSpaces(author);
            fetchBook(context, url, fetchCovers, book);
        }
        return book;
    }

    /**
     * Fetch a book by url.
     *
     * @param context     Current context
     * @param url         to fetch
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @throws StorageException on storage related failures
     */
    private void fetchBook(@NonNull final Context context,
                           @NonNull final String url,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Book book)
            throws StorageException,
                   SearchException {

        futureHttpGet = createFutureGetRequest(context);

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        // get the booklist, can return multiple books ('entry' elements)
        final GoogleBooksListHandler listHandler = new GoogleBooksListHandler();

        try {
            final SAXParser parser = factory.newSAXParser();
            futureHttpGet.get(url, request -> {
                try (BufferedInputStream bis = new BufferedInputStream(request.getInputStream())) {
                    parser.parse(bis, listHandler);
                    return true;

                } catch (@NonNull final IOException e) {
                    throw new UncheckedIOException(e);
                } catch (@NonNull final SAXException e) {
                    throw new UncheckedSAXException(e);
                }
            });

            if (isCancelled()) {
                return;
            }

            final List<String> urlList = listHandler.getResult();

            if (!urlList.isEmpty()) {
                // The entry handler takes care of an individual book ('entry')
                final GoogleBooksEntryHandler handler = new GoogleBooksEntryHandler(
                        this, fetchCovers, book,
                        LocaleListUtils.asList(context),
                        getLocale(context));

                // only using the first one found, maybe future enhancement?
                futureHttpGet.get(urlList.get(0), request -> {
                    try (BufferedInputStream bis = new BufferedInputStream(
                            request.getInputStream())) {
                        parser.parse(bis, handler);
                        checkForSeriesNameInTitle(book);
                        return true;
                    } catch (@NonNull final IOException e) {
                        throw new UncheckedIOException(e);
                    } catch (@NonNull final SAXException e) {
                        throw new UncheckedSAXException(e);
                    }

                });

            }

        } catch (@NonNull final SAXException e) {
            // unwrap SAXException using getException() !
            final Exception cause = e.getException();
            if (cause instanceof StorageException) {
                throw (StorageException) cause;
            }
            // wrap other parser exceptions
            throw new SearchException(getName(context), e);

        } catch (@NonNull final ParserConfigurationException | IOException e) {
            throw new SearchException(getName(context), e);
        } finally {
            futureHttpGet = null;
        }
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
            }
        }
    }

    /**
     * replace spaces with %20.
     *
     * @param s String to encode
     *
     * @return encodes string
     */
    @NonNull
    private String encodeSpaces(@NonNull final CharSequence s) {
//        return URLEncoder.encode(s, "UTF-8");
        return SPACE_LITERAL.matcher(s).replaceAll("%20");
    }
}
