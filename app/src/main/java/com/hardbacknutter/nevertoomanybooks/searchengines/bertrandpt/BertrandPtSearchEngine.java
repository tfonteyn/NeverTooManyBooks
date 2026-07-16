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

package com.hardbacknutter.nevertoomanybooks.searchengines.bertrandpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.HttpConstants;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.bertrand.pt">https://www.bertrand.pt</a>
 * Shopping site from Portugal.
 * <p>
 * All genres; portuguese and some other languages.
 * <p>
 * Added upon recommendation from our portuguese translator maverick74
 * who stated this site is much better than amazon in portugal.
 */
public class BertrandPtSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText,
                   SearchEngine.SearchOnSite {

    private static final String PREFERENCE_KEY = "bertrandpt";

    /** Website character encoding. */
    private static final String CHARSET = "UTF-8";

    /** Search url. Just concat whatever 'words' (or isbn) we're searching for. */
    private static final String SEARCH = "/pesquisa/";
    private static final String TAG = "BertrandPtSearchEngine";
    private final Map<String, String> extraRequestProperties;

    /**
     * Constructor.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param appContext The <strong>application</strong> context
     * @param config     the search engine configuration
     */
    @Keep
    public BertrandPtSearchEngine(@NonNull final Context appContext,
                                  @NonNull final SearchEngineConfig config) {
        super(appContext, config);
        // based on wget experimentation
        extraRequestProperties = Map.of(
                HttpConstants.REFERER, getHostUrl(),
                HttpConstants.SEC_FETCH_SITE, HttpConstants.SEC_FETCH_MODE_SAME_ORIGIN);
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
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_bertrand_pt,
                                    List.of(R.string.site_description_portuguese_and_more,
                                            R.string.site_description_shop),
                                    "https://www.bertrand.pt",
                                    new Locale("pt", "PT"))
                .setPreferenceFragmentClazz(BertrandPtPreferencesFragment.class)
                .setConfig(cb -> cb
                        .setTagsToIgnore(Set.of("Livros", "Livros em Português"))
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());

        final String url = getHostUrl() + SEARCH + codeStr;
        final Document document = loadDocument(context, url, extraRequestProperties);

        final Book book = new Book();
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            multiResult(context, document, criteria.getFetchCovers(), book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            final String codeStr = productCode.getFormatted(getEngineId());
            if (!codeStr.isBlank()) {
                words.add(codeStr);
            }
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        final String url = getHostUrl() + SEARCH + words;
        final Document document = loadDocument(context, url, extraRequestProperties);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            multiResult(context, document, criteria.getFetchCovers(), book);
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
     *
     * @throws CredentialsException on authentication/login failures
     * @throws StorageException     on storage related failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    @WorkerThread
    private void multiResult(@NonNull final Context context,
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        final String url = parseMultiResult(document);
        if (url == null) {
            return;
        }
        final Document redirected = loadDocument(context, url, extraRequestProperties);
        if (!isCancelled()) {
            parse(context, redirected, fetchCovers, book);
        }
    }

    /**
     * A multi result page was returned. Try and parse it.
     * The <strong>first book</strong> link will be extracted and retrieved.
     *
     * @param document to parse
     *
     * @return the url to redirect to, or {@code null} if parsing failed.
     */
    @VisibleForTesting
    @Nullable
    String parseMultiResult(@NonNull final Document document) {

        final Element urlElement = document.selectFirst(
                "div[data-product-position='1'] div.product-info a.title-lnk");
        if (urlElement == null) {
            return null;
        }
        String url = urlElement.attr("href");
        if (url.isBlank()) {
            return null;
        }
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        return url;
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

        final String title = SearchEngineUtils.cleanText(titleElement);
        if (title.isBlank()) {
            return;
        }
        book.setTitle(title);

        // sibling to the title:  class="right-title-details subtitle"
        // ==> *can* contain Series with number
        // see Asimov foundation:  Fundação - Livro 3
        // BUT https://www.bertrand.pt/ebook/galaxy-s-isaac-asimov-collection-volume-2-isaac-asimov/19866052
        //==> NOT the series...

        // Use the site locale for all parsing!
        final Locale siteLocale = getLocale(context, document.location().split("/")[2]);

        // The author is often missing when the book is not a 'standard' portuguese book.
        final Elements authorElements = bookInfo.select(
                "div#productPageSectionDetails-collapseDetalhes-content-author > a");
        authorElements.stream()
                      .map(SearchEngineUtils::cleanName)
                      .filter(name -> !name.isBlank())
                      .map(Author::from)
                      .forEach(author -> addAuthor(author, AuthorRole.UNKNOWN, book, false));

        Element element;

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-isbn > div.info");
        if (element != null) {
            final String isbn = ISBN.cleanText(element.text().strip());
            if (!isbn.isBlank()) {
                book.setRawProductCode(isbn);
            }
        }

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-year > div.info");
        if (element != null) {
            final String s = element.text().strip();
            if (!s.isBlank()) {
                final String[] split = s.split("-");
                if (split.length == 1) {
                    // not seen during testing, but assumed to be year only;
                    book.setPublicationDate(s);
                } else if (split.length == 2) {
                    // as seen in testing: MM-YYYY, convert to YYYY-MM
                    book.setPublicationDate(split[1] + "-" + split[0]);
                }
            }
        }

        // The "Editor", i.e. the publisher is a pain... it does not have an easy div id
        element = bookInfo.selectFirst(":containsOwn(Editor:) > div.info");
        if (element != null) {
            final String s = SearchEngineUtils.cleanName(element);
            if (!s.isBlank()) {
                book.add(Publisher.from(s));
            }
        }

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-language > div.info");
        if (element != null) {
            book.setLanguage(element.text().strip());
        }

        // Encadernação.
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-bookbinding > div.info");
        if (element != null) {
            book.setFormat(element.text().strip());
        }

        // Tipo de Produto
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-type > div.info");
        if (element != null) {
            final String s = element.text().strip();
            if (!"Livro".equals(s)) {
                // If it's NOT a book, then overwrite the format key (i.e. ebook, audiobook...)
                book.setFormat(s);
            }
        }

        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-nrPages > div.info");
        if (element != null) {
            book.setPages(element.text().strip());
        }

        // Coleção -> can be Series or Collection. Just use it as Series and leave it to the user.
        // The number is usually added to the title and cannot be parsed.
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-collection > div.info");
        if (element != null) {
            final String s = SearchEngineUtils.cleanName(element);
            if (!s.isBlank()) {
                book.add(Series.from(s));
            }
        }

        // Classificação  Temática
        element = bookInfo.selectFirst(
                "div#productPageSectionDetails-collapseDetalhes-content-themes > div.info");
        if (element != null) {
            final List<String> tagNames = element.select("a")
                                                 .stream()
                                                 .map(Element::text)
                                                 .collect(Collectors.toList());
            setTags(tagNames, book);
        }

        final Element priceElement = document.selectFirst(
                "div#productPageRightSectionTop-saleAction-price-current");
        if (priceElement != null) {
            final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
            final List<Locale> allLocales = LocaleListUtils.asList(siteLocale, userLocales);
            final MoneyParser parser = new MoneyParser(siteLocale, allLocales);
            addPriceListed(context, parser, priceElement.text(), MoneyParser.EUR, book);
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
                book.setRating(stars.size());
            }
        }

        final Element description = document.selectFirst(
                "div#productPageSectionAboutBook-sinopse > p");
        if (description != null) {
            book.setDescription(description.html().strip());
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCovers(context, document, book.getRawProductCode(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    /**
     * Parses the given {@link Document} for the cover and fetches it when present.
     *
     * @param context  Current context
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
    @VisibleForTesting
    @NonNull
    private Optional<String> parseCovers(@NonNull final Context context,
                                         @NonNull final Document document,
                                         @Nullable final String bookId,
                                         @SuppressWarnings("SameParameterValue")
                                             @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        final Element img = document.selectFirst("div.cover > picture > img");
        if (img == null) {
            return Optional.empty();
        }
        final String url = img.attr("src");
        return saveImage(context, url, null, bookId, cIdx, null);
    }

    @Override
    public boolean isShowSearchOnSiteMenu(@NonNull final Context context) {
        final String key = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU;

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false);
        } else {
            return ServiceLocator.getInstance().getLanguages().isUserLanguage(context, "por");
        }
    }

    @NonNull
    @Override
    public String createSearchOnSiteUrl(@NonNull final Context context,
                                        @Nullable final Author author,
                                        @Nullable final Series series) {
        if (BuildConfig.DEBUG /* always */) {
            if (author == null && series == null) {
                throw new IllegalArgumentException("both author and series are null");
            }
        }

        final StringJoiner fields = new StringJoiner(" ");

        if (author != null) {
            final String cAuthor = SearchEngineUtils
                    .encodeSearchString(author.getFormattedName(true));
            if (!cAuthor.isEmpty()) {
                try {
                    fields.add(URLEncoder.encode(cAuthor, CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        if (series != null) {
            final String cSeries = SearchEngineUtils
                    .encodeSearchString(series.getTitle());
            if (!cSeries.isEmpty()) {
                try {
                    fields.add(URLEncoder.encode(cSeries, CHARSET));
                } catch (@NonNull final UnsupportedEncodingException ignore) {
                    // ignore
                }
            }
        }

        return getHostUrl() + SEARCH + fields;
    }
}
