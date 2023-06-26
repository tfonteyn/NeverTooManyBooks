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

package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BertrandPtSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    private static final String TAG = "BertrandPtSearchEngine";

    /** Website character encoding. */
    static final String CHARSET = "UTF-8";

    static final String SEARCH = "/pesquisa/";
    private static final String DELIM = "+";

    private final Map<String, String> extraRequestProperties;

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BertrandPtSearchEngine(@NonNull final Context appContext,
                                  @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        extraRequestProperties = Map.of(HttpConstants.REFERER, getHostUrl(appContext),
                                        HttpConstants.SEC_FETCH_SITE, "same-origin");

    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final String url = getHostUrl(context) + SEARCH + DELIM + validIsbn;
        final Document document = loadDocument(context, url, extraRequestProperties);

        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @Nullable final String code,
                       @Nullable final String author,
                       @Nullable final String title,
                       @Nullable final String publisher,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final StringJoiner words = new StringJoiner(DELIM);
        if (author != null && !author.isEmpty()) {
            words.add(author);
        }
        if (title != null && !title.isEmpty()) {
            words.add(title);
        }
        if (publisher != null && !publisher.isEmpty()) {
            words.add(publisher);
        }
        if (code != null && !code.isEmpty()) {
            words.add(code);
        }

        final String url = getHostUrl(context) + SEARCH + words;
        final Document document = loadDocument(context, url, extraRequestProperties);
        final Book book = new Book();
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
        //  9789899087774
        // Grab the first search result, and redirect to that page
        Element dataElement = document.selectFirst("div[data-product-position='1']");
        if (dataElement != null) {
            dataElement = dataElement.selectFirst("div.product-info");
            if (dataElement != null) {
                dataElement = dataElement.selectFirst("a.title-lnk");
                if (dataElement != null) {
                    String url = dataElement.attr("href");
                    // sanity check - it normally does NOT have the protocol/site part
                    if (url.startsWith("/")) {
                        url = getHostUrl(context) + url;
                    }
                    final Document redirected = loadDocument(context, url, extraRequestProperties);
                    if (!isCancelled()) {
                        parse(context, redirected, fetchCovers, book);
                    }
                }
            }
        }
    }

    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final Element bookInfo = document.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content");
        if (bookInfo == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no bookInfo?");
            return;
        }

        final Element titleElement = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-title");
        if (titleElement == null) {
            // If we find no title, we assume parsing is broken
            LoggerFactory.getLogger().w(TAG, "parse", "no title?");
            return;
        }

        final String title = titleElement.text();
        book.putString(DBKey.TITLE, title);

        // sibling to the title:  class="right-title-details subtitle"
        // ==> *can* contain Series with number
        // see Asimov foundation:  Fundação - Livro 3
        // BUT https://www.bertrand.pt/ebook/galaxy-s-isaac-asimov-collection-volume-2-isaac-asimov/19866052
        //==> NOT the series...

        // Use the site locale for all parsing!
        final Locale siteLocale = getLocale(context, document.location().split("/")[2]);
        final List<Locale> locales = LocaleListUtils.asList(context, siteLocale);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);
        final MoneyParser moneyParser = new MoneyParser(siteLocale, realNumberParser);

        // The author is often missing when the book is not a 'standard' portuguese book.
        final Elements authorElements = bookInfo.select(
                "div#productPageSectionDetails-collapseDetalhes-content-author > a");
        for (final Element ae : authorElements) {
            final Author author = Author.from(ae.text());
            processAuthor(author, Author.TYPE_UNKNOWN, book);
        }

        Element element;
        String s;

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-isbn > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.putString(DBKey.BOOK_ISBN, s);
            }
        }

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-year > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                final String[] split = s.split("-");
                if (split.length == 1) {
                    // not seen during testing, but assume year only;
                    book.putString(DBKey.BOOK_PUBLICATION__DATE, s);
                } else if (split.length == 2) {
                    // as seen in testing: MM-YYYY, convert to YYYY-MM
                    book.putString(DBKey.BOOK_PUBLICATION__DATE, split[1] + "-" + split[0]);
                }
            }
        }

        // The "Editor", i.e. the publisher is a pain... it does not have an easy div id
        element = bookInfo.selectFirst(":containsOwn(Editor:) > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.add(Publisher.from(s));
            }
        }

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-language > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.putString(DBKey.LANGUAGE, s);
            }
        }

        // Encadernação.
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-bookbinding > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.putString(DBKey.FORMAT, s);
            }
        }

        // Tipo de Produto
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-type > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!"Livro".equals(s)) {
                // If it's NOT a book, then overwrite the format key (i.e. ebook, audiobook...)
                book.putString(DBKey.FORMAT, s);
            }
        }

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-nrPages > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.putString(DBKey.PAGE_COUNT, s);
            }
        }

        // Coleção -> can be Series or Collection. Just use it as Series and leave it to the user.
        // The number is usually added to the title and cannot be parsed.
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-collection > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.add(Series.from(s));
            }
        }

        // There can be multiple "div.info", i.e. a list of genres. We only take the first one.
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-themes > div.info");
        if (element != null) {
            s = element.text().strip();
            if (!s.isBlank()) {
                book.putString(DBKey.GENRE, s);
            }
        }

        final Element priceElement = document.selectFirst(
                "div#productPageRightSectionTop-saleAction-price-current");
        if (priceElement != null) {
            final String tmpString = priceElement.text();
            final Money money = moneyParser.parse(tmpString);
            if (money != null) {
                book.putMoney(DBKey.PRICE_LISTED, money);
            } else {
                // parsing failed, store the string as-is;
                // no separate currency!
                book.putString(DBKey.PRICE_LISTED, priceElement.text());
                // log this as we need to understand WHY it failed
                LoggerFactory.getLogger().w(TAG, "Failed to parse",
                                            DBKey.PRICE_LISTED,
                                            "text=" + tmpString);
            }
        }

        // First try for the readers rating
        Element ratingElement = document.selectFirst(
                "div#productPageRightSectionTop-rating-evaluation");
        if (ratingElement == null) {
            // If no reader-rating found, then try the seller rating
            ratingElement = document.selectFirst(
                    "div#productPageRightSectionTop-libraries-rating-evaluation");
        }
        if (ratingElement != null) {
            // The nest sibling should be the ""div.stars"
            ratingElement = ratingElement.nextElementSibling();
            if (ratingElement != null) {
                // count the 'active' stars
                final Elements stars = ratingElement.select("span.active");
                book.putFloat(DBKey.RATING, stars.size());
            }
        }

        final Element description = document.selectFirst(
                "div#productPageSectionAboutBook-sinopse > p");
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
            book.setCoverFileSpecList(0, parseCovers(context, document, book));
        }
    }

    @WorkerThread
    @VisibleForTesting
    @NonNull
    private List<String> parseCovers(@NonNull final Context context,
                                     @NonNull final Document document,
                                     @NonNull final Book book)
            throws StorageException {

        final List<String> imageList = new ArrayList<>();

        final Element img = document.selectFirst("div.cover > picture > img");
        if (img != null) {
            final String url = img.attr("src");
            final String isbn = book.getString(DBKey.BOOK_ISBN);
            final String fileSpec = saveImage(context, url, isbn, 0, null);
            if (fileSpec != null) {
                imageList.add(fileSpec);
            }
        }
        return imageList;
    }
}
