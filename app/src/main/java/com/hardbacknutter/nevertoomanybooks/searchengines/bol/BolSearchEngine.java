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

package com.hardbacknutter.nevertoomanybooks.searchengines.bol;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BolSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    private static final String TAG = "BolSearchEngine";
    private static final String SEARCH = "/s/?searchtext=+%1$s+";

    /**
     * Constructor. Called using reflections, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BolSearchEngine(@NonNull final Context appContext,
                           @NonNull final SearchEngineConfig config) {
        super(appContext, config);
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // Derive the Locale from the user configured url.

        // BOL is from The Netherlands -> "nl/nl" is the default
        // https://www.bol.com
        // https://www.bol.com/be
        // https://www.bol.com/nl
        // https://www.bol.com/be/nl
        // https://www.bol.com/nl/nl
        // https://www.bol.com/be/fr
        // https://www.bol.com/nl/fr

        final String url = getHostUrl();
        final String language;
        if (url.contains("fr")) {
            language = "fr";
        } else {
            language = "nl";
        }
        final String country;
        if (url.contains("be")) {
            country = "BE";
        } else {
            country = "NL";
        }

        return new Locale(language, country);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Document document = loadDocument(context, getHostUrl()
                                                        + String.format(SEARCH, validIsbn),
                                               null);
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
     * @throws StorageException     on storage related failures
     */
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst(
                "p.px_list_page_product_click list_page_product_tracking_target");
        // it will be null if there were no results.
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
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
        }
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

        final Element titleElement = document.selectFirst("span[data-test='title']");
        if (titleElement == null || titleElement.text().isEmpty()) {
            // well, this is unexpected...
            // however, when accessing the site in french and looking for
            // a dutch book.... the site simply does not list the title... anywhere! ... ouch...
            return;
        }
        processText(titleElement, DBKey.TITLE, book);

        final Elements specs = document.select("div.specs > dl.specs__list");
        if (specs.isEmpty()) {
            return;
        }

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        final List<Locale> locales = LocaleListUtils.asList(context, getLocale(context));

        final DateParser dateParser = new FullDateParser(systemLocale, locales);
        final RealNumberParser realNumberParser = new RealNumberParser(locales);

        for (final Element spec_row : specs.select("div.specs__row")) {
            final Element label = spec_row.selectFirst("dt.specs__title");
            final Element value = spec_row.selectFirst("dd.specs__value");
            if (label != null && value != null) {
                switch (label.text()) {
                    case "Taal":
                    case "Langue": {
                        processText(value, DBKey.LANGUAGE, book);
                        break;
                    }
                    case "Bindwijze":
                    case "Binding": {
                        processText(value, DBKey.FORMAT, book);
                        break;
                    }
                    case "Oorspronkelijke releasedatum":
                    case "Date de sortie initiale": {
                        final String text = SearchEngineUtils.cleanText(value.text());
                        if (!text.isEmpty()) {
                            final LocalDateTime date = dateParser.parse(text, getLocale(context));
                            if (date != null) {
                                book.putString(DBKey.BOOK_PUBLICATION__DATE,
                                               date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                            }
                        }
                        break;
                    }
                    case "Aantal pagina's":
                    case "Nombre de pages": {
                        processText(value, DBKey.PAGE_COUNT, book);
                        break;
                    }
                    case "Hoofdauteur":
                    case "Auteur principal": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            book.add(Author.from(a.text()));
                        }
                        break;
                    }
                    case "Hoofduitgeverij":
                    case "Editeur principal": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            book.add(Publisher.from(a.text()));
                        }
                        break;
                    }
                    case "EAN": {
                        processText(value, DBKey.BOOK_ISBN, book);
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        final Element ratingElement = document.selectFirst("div.reviews-summary__avg-score");
        if (ratingElement != null) {
            try {
                final float rating = realNumberParser.parseFloat(ratingElement.text());
                book.putFloat(DBKey.RATING, rating);
            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }

        //FIXME: if they are out of stock, these will NOT contain a price.
        // We should get the price from the buttons on the page just above this field
        // but those button elements are not easy to parse for.
        final Element priceElement = document.selectFirst("span.promo-price");
        if (priceElement != null) {
            try {
                double price = realNumberParser.parseDouble(priceElement.text());
                final Element priceFractionElement = document.selectFirst(
                        "span.promo-price__fraction");
                if (priceFractionElement != null) {
                    final double fraction = realNumberParser.parseDouble(
                            priceFractionElement.text());
                    price += (fraction / 100);
                }
                book.putMoney(DBKey.PRICE_LISTED,
                              new Money(BigDecimal.valueOf(price), Money.EURO));

            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }

    /**
     * Process a value which is pure text.
     *
     * @param value value element
     * @param key   for this field
     * @param book  Bundle to update
     */
    private void processText(@Nullable final Element value,
                             @NonNull final String key,
                             @NonNull final Book book) {
        // some 'specs' can appear more than once (e.g. "Taal")
        if (!book.contains(key)) {
            if (value != null) {
                final String text = SearchEngineUtils.cleanText(value.text());
                if (!text.isEmpty()) {
                    book.putString(key, text);
                }
            }
        }
    }
}
