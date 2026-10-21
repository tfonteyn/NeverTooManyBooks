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

package com.hardbacknutter.nevertoomanybooks.searchengines.kitapsec;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.LocaleResolverMoneyDecimalPoint;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class KitapsecSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn {

    /** {@link SearchEngineConfig#getHostUrl()}. */
    private static final String HOST_URL = "https://www.kitapsec.com";
    /** {@link EngineId#getPreferenceKey()}. */
    private static final String HOST_PREF_KEY = "kitapsec";
    /** {@link EngineId#getDefaultLocale()}. */
    @SuppressWarnings("deprecation")
    private static final Locale HOST_LOCALE = new Locale("tr", "TR");

    private static final String SEARCH_URL = HOST_URL + "/Arama/index.php?a=";

    private static final Pattern KITAPSEC_PRODUCT_PATTERN = Pattern.compile(
            "^https://www\\.kitapsec\\.com/Products/.+\\.html$",
            Pattern.CASE_INSENSITIVE);

    private static final String LABEL_ISBN = "ISBN / BARKOD";
    private static final String LABEL_PUBLISHER = "Yayınevi / Marka";
    private static final String LABEL_WRITER = "Yazar";
    // not actually seen this used. Added from the original Pull Request.
    private static final String LABEL_TRANSLATOR = "Çevirmen";
    private static final String LABEL_PAGES = "Sayfa Sayısı";
    // not actually seen this used. Added from the original Pull Request.
    private static final String LABEL_LANGUAGE = "Dil";

    /** The rating is given as the width in percentage inside a style attribute. */
    private static final Pattern RATING_PATTERN = Pattern.compile(
            "width:\\s*(\\d+)%");
    private static final Pattern SERIES_PATTERN = Pattern.compile(
            "^(?:(.*?)(?:\\s+(\\d+))?\\s*:\\s+)?(.*)$");

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
    public KitapsecSearchEngine(@NonNull final Context context,
                                @NonNull final SearchEngineConfig config) {
        super(context, config);
        bookParserHelper.setMoneyParserLocaleResolver(LocaleResolverMoneyDecimalPoint.INSTANCE);
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
                                    R.string.site_kitapsec,
                                    List.of(R.string.site_description_turkish,
                                            R.string.site_description_shop),
                                    HOST_URL,
                                    HOST_LOCALE)
                .setPreferenceFragmentClazz(KitapsecPreferencesFragment.class);
    }

    @Override
    @WorkerThread
    @NonNull
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws SearchException, StorageException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());
        final String url = SEARCH_URL + codeStr;
        final Document document = loadHtml(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            multiResult(context, document, criteria.getFetchCovers(), book, productCode);
        }
        return book;
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param context      Current context
     * @param document     to parse
     * @param fetchCovers  Set array indexes to {@code true} to fetch a cover for that index.
     *                     Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book         to update
     * @param searchedIsbn ISBN from user-search
     *
     * @throws SearchException  on generic exceptions (wrapped) during search
     * @throws StorageException on image storage failures
     */
    @WorkerThread
    private void multiResult(@NonNull final Context context,
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book,
                             @NonNull final ProductCode searchedIsbn)
            throws SearchException, StorageException {
        final String url = parseMultiResult(document, searchedIsbn);
        if (url == null) {
            return;
        }

        final Document redirected = loadHtml(context, url, null);
        if (!isCancelled()) {
            parse(context, redirected, fetchCovers, book);
        }
    }

    /**
     * Parse the initial search page. Grab the first matching url.
     *
     * @param document     to parse
     * @param searchedIsbn ISBN from user-search
     *
     * @return url to redirect to
     */
    @VisibleForTesting
    @Nullable
    public String parseMultiResult(@NonNull final Document document,
                                   @NonNull final ProductCode searchedIsbn) {
        // The list of ALL results
        for (final Element item : document.select(".urunListeleDiv .Ks_UrunSatir")) {
            // The ISBN
            final Element sku = item.selectFirst("meta[itemprop=sku]");
            // The redirect url
            final Element url = item.selectFirst("meta[itemprop=url]");

            if (sku != null && url != null) {
                final String codeStr = sku.attr("content");
                //  Only accept matching ISBN's
                if (!codeStr.isBlank() && ISBN.parse(codeStr).equals(searchedIsbn)) {
                    try {
                        final String redirect = URI.create(HOST_URL + "/")
                                                   .resolve(url.attr("content"))
                                                   .toString();

                        // only accept links on the site itself
                        if (KITAPSEC_PRODUCT_PATTERN.matcher(redirect).matches()) {
                            return redirect;
                        }
                    } catch (@NonNull final IllegalArgumentException ignored) {
                        // A malformed url must not stop us checking later matches.
                    }
                }
            }
        }
        return null;
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
     * @throws StorageException on image storage failures
     */
    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException {

        final Element details = document.selectFirst(".Ks_DetayCerceveUst .dty_SagBlok");
        if (details == null) {
            return;
        }

        final Element titleElement = details.selectFirst(".dty_SagBlok > h1");
        if (titleElement == null || titleElement.text().isBlank()) {
            return;
        }
        // The title is an utter mess...
        //
        // When a book is part of a series, the series name+nr is prepended.
        //
        // The site MAY append the writer.
        // The site MAY append the publisher.
        // We try to clean these up during parsing of authors and publisher.

        String title = titleElement.text();
        title = parseSeries(title, book);
        book.setTitle(title);

        final Elements labels = details.select(".detayBilgiDivIc .baslikText");
        final Elements values = details.select(".detayBilgiDivIc .sonucText");

        for (int i = 0; i < labels.size(); i++) {
            final Element value = values.get(i);
            switch (labels.get(i).text()) {
                case LABEL_ISBN: {
                    final String s = ISBN.cleanText(value.text());
                    if (!s.isBlank()) {
                        book.setRawProductCode(s);
                    }
                    break;
                }
                case LABEL_PUBLISHER: {
                    parsePublisher(value, book);
                    break;
                }
                case LABEL_WRITER: {
                    parseAuthor(value, AuthorRole.WRITER, book);
                    break;
                }
                case LABEL_TRANSLATOR: {
                    parseAuthor(value, AuthorRole.TRANSLATOR, book);
                    break;
                }
                case LABEL_PAGES: {
                    book.setPages(value.text());
                    break;
                }
                case LABEL_LANGUAGE: {
                    book.setLanguage(value.text());
                    break;
                }
            }
        }

        // The first price element is the list-price.
        // There is a second one with the discounted/actual price, but we ignore that
        // as users can/will have bought their books elsewhere.
        final Element firstPrice = details.selectFirst(".piyasa > *");
        if (firstPrice != null) {
            final String text = firstPrice.text();
            bookParserHelper.addPriceListed(context, HOST_LOCALE, text, null, book);
        }

        final Element ratingElement = document.selectFirst("#ktTitleYuzde");
        if (ratingElement != null) {
            parseRating(ratingElement, book);
        }

        final Element descElement = document.selectFirst(".TabDivList > #tab1");
        if (descElement != null) {
            // Can have a repeat of the title in an 'h2' block
            // Can have a youtube iframe
            descElement.select("h2, iframe").remove();
            // Can have style attributes
            descElement.select("[style]").removeAttr("style");

            book.setDescription(SearchEngineUtils.cleanText(descElement));
        }

        if (isCancelled()) {
            return;
        }

        // Unless present, add the default language
        if (!book.contains(DBKey.LANGUAGE)) {
            book.setLanguage("tur");
        }

        if (fetchCovers[0]) {
            parseCover(document, book.getRawProductCode(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));

        }
    }

    @NonNull
    private String parseSeries(@NonNull final String text,
                               @NonNull final Book book) {
        final Matcher matcher = SERIES_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        final String seriesTitle = matcher.group(1);
        final String seriesNr = matcher.group(2);
        // Only accept when both are non-null.
        // This will hopefully avoid false matches.
        if (seriesTitle != null && !seriesTitle.isBlank()
            && seriesNr != null && !seriesNr.isBlank()) {

            book.add(Series.from(seriesTitle, seriesNr));

            // the remainder will become the book title
            //noinspection DataFlowIssue
            return matcher.group(3);
        }

        // fallback when seriesTitle/seriesNr was not deemed acceptable
        return text;
    }

    private void parseRating(@NonNull final Element ratingElement,
                             @NonNull final Book book) {
        final String style = ratingElement.attr("style");
        final Matcher matcher = RATING_PATTERN.matcher(style);
        if (matcher.find()) {
            try {
                //noinspection DataFlowIssue
                final int percentage = Integer.parseInt(matcher.group(1));
                // reduce to 0..5 in 0.5 steps
                final double rating = Math.round(percentage / 10.0) / 2.0;
                if (rating > 0) {
                    book.setRating((float) rating);
                }

            } catch (@NonNull final NumberFormatException ignore) {
                // ignored
            }
        }
    }

    private void parsePublisher(@NonNull final Element element,
                                @NonNull final Book book) {
        final String text = element.text();
        if (text.isBlank()) {
            return;
        }
        book.add(Publisher.from(text));
        cleanTitleSuffix(text, book);
    }

    private void parseAuthor(@NonNull final Element element,
                             @AuthorRole.Role final int role,
                             @NonNull final Book book) {

        // If there are no links inside, use the text itself
        final Elements aas = element.select("a");
        if (aas.isEmpty()) {
            parseAuthor2(element, role, book);
        } else {
            for (final Element a : aas) {
                parseAuthor2(a, role, book);
            }
        }
    }

    private void parseAuthor2(@NonNull final Element element,
                              @AuthorRole.Role final int role,
                              @NonNull final Book book) {
        final String text = element.text();
        if (text.isBlank()) {
            return;
        }
        bookParserHelper.addAuthor(Author.from(text), role, book, false);
        cleanTitleSuffix(text, book);
    }

    /**
     * Remove the training {@code text} from the book title if applicable.
     *
     * @param text to remove
     * @param book to update
     */
    private void cleanTitleSuffix(@NonNull final String text,
                                  @NonNull final Book book) {
        final String title = book.getTitle();
        if (!title.endsWith(text)) {
            return;
        }

        final String cleanTitle = title
                .substring(0, title.length() - text.length())
                .strip();

        if (!cleanTitle.isBlank()) {
            book.setTitle(cleanTitle);
        }
    }

    @NonNull
    private Optional<String> parseCover(@NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                        @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        final Element a = document.selectFirst("#dtyResimlerIc a");
        if (a == null) {
            return Optional.empty();
        }
        // The href is relative, and starts with "//cdn.kitapsec.com"
        // Let jsoup resolve it
        final String url = a.absUrl("href");

        if (url.isBlank()) {
            return Optional.empty();
        }

        return httpCallFactory.saveImage(url, null, bookId, cIdx, null);
    }
}
