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

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

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
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BolSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText {

    /** one of {"","be","nl"} */
    static final String PK_BOL_COUNTRY = EngineId.Bol.getPreferenceKey() + ".country";

    /** Website character encoding. */
    static final String CHARSET = "UTF-8";
    /**
     * param 1: the country "be" or "nl"
     * param 2: words, separated by spaces
     */
    static final String BY_TEXT = "/%1$s/nl/s/?searchtext=%2$s";
    /**
     * param 1: the country "be" or "nl"
     * param 2: the isbn
     */
    private static final String BY_ISBN = "/%1$s/nl/s/?searchtext=+%2$s+";

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
    static String getCountry(@NonNull final Context context) {
        String country = PreferenceManager.getDefaultSharedPreferences(context)
                                          .getString(PK_BOL_COUNTRY, null);
        if (country != null && !country.isEmpty()) {
            return country;
        } else {
            // Never configured, use the users actual country
            country = ServiceLocator.getInstance().getSystemLocaleList().get(0)
                                    .getCountry();
            if ("BE".equals(country)) {
                // Belgium
                return "be";
            } else {
                // The Netherlands + rest of the world.
                return "nl";
            }
        }
    }

    @NonNull
    @Override
    public Locale getLocale(@NonNull final Context context) {
        // The site can display in french, but we don't support this as
        // 1. they don't sell french books anyhow (at least for now)
        // 2. their french book pages don't display the book title (oh boy...)
        return new Locale("nl", getCountry(context).toUpperCase(Locale.ROOT));
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

        final StringJoiner words = new StringJoiner(" ");
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

        final String url = getHostUrl() + String.format(BY_TEXT, getCountry(context), words);
        final Document document = loadDocument(context, url, null);
        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final String url = getHostUrl() + String.format(BY_ISBN, getCountry(context), validIsbn);
        final Document document = loadDocument(context, url, null);
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
    @VisibleForTesting
    @WorkerThread
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Document document,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        final Element section = document.selectFirst("div.product-title--inline");
        // section will be null if there were no results.
        if (section != null) {
            // Grab the first search result, and redirect to that page
            final Element urlElement = section.selectFirst(
                    "a.product-title.px_list_page_product_click.list_page_product_tracking_target");
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
                            final Author author = Author.from(a.text());
                            author.setType(Author.TYPE_WRITER);
                            book.add(author);
                        }
                        break;
                    }
                    case "Hoofdillustrator":
                    case "Illustrateur en chef": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final Author author = Author.from(a.text());
                            author.setType(Author.TYPE_ARTIST);
                            book.add(author);
                        }
                        break;
                    }
                    case "Eerste Vertaler":
                    case "Tweede Vertaler":
                    case "Premier traducteur":
                    case "Deuxième traducteur": {
                        final Element a = value.selectFirst("a");
                        if (a != null) {
                            final Author author = Author.from(a.text());
                            author.setType(Author.TYPE_TRANSLATOR);
                            book.add(author);
                        }
                        break;
                    }
                    case "Originele titel":
                    case "Titre original": {
                        final String originalTitle = value.text();
                        if (!originalTitle.isEmpty()) {
                            book.putString(SiteField.ORIGINAL_TITLE, originalTitle);
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

        parseDescription(document, book);
        parseRating(document, book, realNumberParser);
        parsePrice(document, book, realNumberParser);

        if (fetchCovers[0]) {
            parseCovers(document, fetchCovers, book);
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {
        final Element descrElement = document.selectFirst("div.product-description");
        if (descrElement != null) {
            final String description = descrElement.text();
            if (!description.isEmpty()) {
                book.putString(DBKey.DESCRIPTION, description);
            }
        }
    }

    private void parseRating(@NonNull final Document document,
                             @NonNull final Book book,
                             final RealNumberParser realNumberParser) {
        final Element ratingElement = document.selectFirst("div.reviews-summary__avg-score");
        if (ratingElement != null) {
            try {
                final float rating = realNumberParser.parseFloat(ratingElement.text());
                book.putFloat(DBKey.RATING, rating);
            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }

    private void parsePrice(@NonNull final Document document,
                            @NonNull final Book book,
                            @NonNull final RealNumberParser realNumberParser) {
        //FIXME: if they are out of stock, these will NOT contain a price.
        // We should get the price from the buttons on the page just above this field
        // but those button elements are not easy to parse for.
        final Element priceElement = document.selectFirst("span.promo-price");
        if (priceElement != null) {
            try {
                // <span class="promo-price" data-test="price">22
                //    <sup class="promo-price__fraction" data-test="price-fraction">99</sup>
                // </span>
                // text() will get "22 99", so add a "," as decimal separator and parse as normal
                final String priceText = priceElement.text().replace(" ", ",");
                final double price = realNumberParser.parseDouble(priceText);
                book.putMoney(DBKey.PRICE_LISTED,
                              new Money(BigDecimal.valueOf(price), Money.EURO));

            } catch (@NonNull final IllegalArgumentException ignore) {
                // ignore
            }
        }
    }

    private void parseCovers(@NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException {
        final String isbn = book.getString(DBKey.BOOK_ISBN);

        final Element imageSlotConfig = document.selectFirst(
                "section[data-group-name='product-images'] script");
        if (imageSlotConfig != null
            && imageSlotConfig.hasAttr("data-image-slot-config")) {
            final String text = imageSlotConfig.data();
            final JSONObject json = new JSONObject(text);
            final JSONObject imageSlotSlider = json.optJSONObject("imageSlotSlider");
            if (imageSlotSlider != null) {
                final JSONObject currentItem = imageSlotSlider.optJSONObject("currentItem");
                if (currentItem != null) {
                    String coverImageUrl;
                    // There is more than 1 possible key for the frontcover
                    coverImageUrl = currentItem.optString("coverImageUrl");
                    boolean gotFrontCover = false;
                    if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
                        gotFrontCover = processCover(coverImageUrl, isbn, 0, book);
                    }
                    if (!gotFrontCover) {
                        // try alternative key.
                        coverImageUrl = currentItem.optString("imageUrl");
                        if (coverImageUrl != null && !coverImageUrl.isEmpty()) {
                            gotFrontCover = processCover(coverImageUrl, isbn, 0, book);
                        }
                    }

                    // only attempt to get the back-cover if we got a frontcover
                    // and (obv.) we want one.
                    if (gotFrontCover && fetchCovers.length > 1 && fetchCovers[1]) {
                        coverImageUrl = currentItem.optString("backImageUrl");
                        processCover(coverImageUrl, isbn, 1, book);
                    }
                }
            }
        }
    }

    /**
     * Fetch the given cover url if possible.
     *
     * @param url  to fetch
     * @param isbn of the book
     * @param cIdx 0..n image index
     * @param book Bundle to update
     *
     * @return {@code true} if an image was successfully saved.
     */
    private boolean processCover(@Nullable final String url,
                                 @NonNull final String isbn,
                                 @IntRange(from = 0, to = 1) final int cIdx,
                                 @NonNull final Book book)
            throws StorageException {

        if (url != null && !url.isEmpty()) {
            final String fileSpec = saveImage(url, isbn, cIdx, null);
            if (fileSpec != null && !fileSpec.isEmpty()) {
                final ArrayList<String> list = new ArrayList<>();
                list.add(fileSpec);
                book.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[cIdx], list);
                return true;
            }
        }
        return false;
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

    /**
     * BOL specific field names we add to the bundle based on parsed data.
     */
    public static final class SiteField {

        static final String ORIGINAL_TITLE = "__original_title";

        private SiteField() {
        }
    }
}
