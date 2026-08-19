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

package com.hardbacknutter.nevertoomanybooks.searchengines.bookfinder;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * This is a portal site to other shopping sites.
 * <p>
 * All genres. English only?
 * <p>
 * Can find books which are harder to find on other sites,
 * but will only show minimal information.
 * <p>
 * This is an experiment... the site is NOT exposed in release builds.
 * <p>
 * Searching by ISBN seems always to return a single book, but we have not done
 * any exhaustive tests.
 */
public class BookFinderSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    private static final String HOST_URL = "https://www.bookfinder.com";
    /** {@link EngineId#getDefaultLocale()}. */
    private static final Locale HOST_LOCALE = Locale.US;
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "bookfinder";

    private static final String TAG = "BookFinderSearchEngine";

    private static final String BY_ISBN = HOST_URL + "/search_s/?"
                                          + "st=sr"
                                          + "&ac=qr"
                                          + "&mode=basic"
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
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context. NOT stored.
     * @param config  the search engine configuration
     *
     * @see EngineId#createSearchEngine(Context)
     */
    @Keep
    public BookFinderSearchEngine(@NonNull final Context context,
                                  @NonNull final SearchEngineConfig config) {
        super(context, config);

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
                                    R.string.site_bookfinder,
                                    List.of(R.string.site_description_various_languages,
                                            R.string.site_description_shop),
                                    HOST_URL,
                                    HOST_LOCALE)
                .setPreferenceFragmentClazz(BookFinderPreferencesFragment.class);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());

        final String url = String.format(BY_ISBN, codeStr);
        final Document document = loadHtml(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, criteria.getFetchCovers(), book);
        }
        return book;
    }

    /**
     * Parses the downloaded {@link org.jsoup.nodes.Document}.
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

        book.setTitle(titleElement.text());

        final Element authorElement = bookInfo.selectFirst(
                "div.bf-content-header-book-author > p > strong > a");
        if (authorElement != null) {
            final String s = SearchEngineUtils.cleanName(authorElement);
            if (!s.isBlank()) {
                bookParserHelper.addAuthor(Author.from(s), AuthorRole.UNKNOWN, book, false);
            }
        }
        final Element ratingElement = bookInfo.selectFirst("div.rating"
                                                           + " > span.book-rating-average");
        if (ratingElement != null) {
            final String[] s = ratingElement.text().split(" ", 2);
            ratingParser.parse(s[0]).ifPresent(book::setRating);
        }

        final Locale siteLocale = getLocale(context);

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
                                book.setRawProductCode(s[0].strip());
                            }
                            break;
                        }
                        case "Publisher:": {
                            processPublisher(context, siteLocale, value, book);
                            break;
                        }
                        case "Edition:": {
                            // This field is actually the format
                            book.setFormat(value);
                            break;
                        }
                        case "Language:": {
                            book.setLanguage(value);
                            break;
                        }
                    }
                }
            }
        }

        final Element description = document.selectFirst("div#bookSummary > p");
        if (description != null) {
            final String s = SearchEngineUtils.cleanText(description.html());
            if (!s.isBlank()) {
                book.setDescription(s);
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCover(document, book.getRawProductCode(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void processPublisher(@NonNull final Context context,
                                  @NonNull final Locale siteLocale,
                                  @NonNull final String value,
                                  @NonNull final Book book) {
        final String[] parts = value.split(",");
        if (parts.length > 0) {
            final String s = SearchEngineUtils.cleanName(parts[0]);
            if (!s.isBlank()) {
                book.add(Publisher.from(s.strip()));
                if (parts.length > 1 && !parts[1].isBlank()) {
                    final String dateStr = parts[1].strip();
                    bookParserHelper.addPublicationDate(context, siteLocale, dateStr, book);
                }
            }
        }
    }

    /**
     * Parses the given {@link Document} for the cover and fetches it when present.
     *
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
    private Optional<String> parseCover(@NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                            @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        final Element img = document.selectFirst("div#header-img > img");
        if (img == null) {
            return Optional.empty();
        }
        final String url = img.attr("src");
        return getHttpCallFactory().saveImage(url, null, bookId, cIdx, null);
    }
}
