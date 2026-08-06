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

package com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.search.ScanMode;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <a href="https://books.google.com">Google books</a>.
 * <p>
 * {@link SearchEngine.ByExternalId} can be supported, but the IDs are for example "9ygPPQAACAAJ".
 * It's not practical for the user to enter those manually.
 * <p>
 * There is a query parameter "langRestrict" but that does not seem to work properly.
 * https://www.googleapis.com/books/v1/volumes?q=intitle:flowers+inauthor:keyes
 * => es and en books
 * https://www.googleapis.com/books/v1/volumes?q=intitle:flowers+inauthor:keyes&langRestrict=es
 * => NO books
 * https://www.googleapis.com/books/v1/volumes?q=intitle:flowers+inauthor:keyes&langRestrict=en
 * => es and en books
 *
 * @see <a href="https://developers.google.com/books/docs/v1/getting_started?csw=1">
 *         Getting started</a>
 * @see <a href="https://developers.google.com/books/docs/v1/reference/volumes#resource-representations">
 *         resource-representations</a>
 * @see <a href="https://developers.google.com/books/docs/static-links>static-links</a>
 */
public class GoogleBooksSearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText,
                   SearchEngine.CoverByEdition {

    private static final String SITE_URL = "https://books.google.com";
    // TODO: 2024-12-30: google has a new link as beta:
    //  "https://www.google.com/books/edition/_/" + externalId;
    private static final String BOOK_URL = "https://books.google.co.uk/books?id=%s";

    private static final Pattern SPACE_LITERAL = Pattern.compile(" ", Pattern.LITERAL);
    private static final String SEARCH = "/books/v1/volumes?q=";
    private final RatingParser ratingParser;
    @Nullable
    private FutureHttp<String> httpGet;

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
    public GoogleBooksSearchEngine(@NonNull final Context appContext,
                                   @NonNull final SearchEngineConfig config) {
        super(appContext, config);

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
        return new EngineId.Builder("googlebooks",
                                    R.string.site_google_books,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    "https://www.googleapis.com",
                                    Locale.US)
                .setPreferenceFragmentClazz(GoogleBooksPreferencesFragment.class)
                .setIdentifierKey(Identifier.EntityType.Book, Identifier.SID_GOOGLE)
                .setMultipleCoverSizes(true);
    }

    /**
     * Called at <strong>installation/upgrade</strong> time to create the initial set
     * in the database.
     * <p>
     * Called by reflection; <strong>MUST</strong> be {@code public}
     * and annotated with {@code @Keep}
     *
     * @param context Current context
     *
     * @return list
     */
    @Keep
    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = context.getString(R.string.identifier_google_books);
        return Set.of(
                new Identifier(Identifier.EntityType.Book,
                               Identifier.Type.Text,
                               Identifier.SID_GOOGLE,
                               name,
                               SITE_URL,
                               BOOK_URL,
                               "P675")
        );
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());
        // %3A  :
        final String url = getHostUrl() + SEARCH + "isbn%3A" + codeStr;

        final Book book = new Book();
        fetchBook(context, url, criteria.getFetchCovers(), book);
        return book;
    }

    /**
     * Criteria supported: title, author, publisher.
     * Code: supports "isbn" only.
     * <p>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    @WorkerThread
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException {

        final Book book = new Book();

        // %2B  +
        final StringJoiner args = new StringJoiner("%2B");

        final String title = criteria.getTitle();
        if (!title.isEmpty()) {
            args.add("intitle%3A" + encodeSpaces(title));
        }

        final String author = criteria.getAuthor();
        if (!author.isEmpty()) {
            args.add("inauthor%3A" + encodeSpaces(author));
        }

        final String publisher = criteria.getPublisher();
        if (!publisher.isEmpty()) {
            args.add("inpublisher%3A" + encodeSpaces(publisher));
        }

        final ProductCode productCode = criteria.getProductCode();
        if (productCode != null) {
            final String codeStr = productCode.getFormatted(getEngineId());
            if (!codeStr.isBlank()) {
                args.add("isbn%3A" + encodeSpaces(codeStr));
            }
        }
        // Sanity check
        if (args.length() == 0) {
            return book;
        }

        // %3A  :
        final String url = getHostUrl() + SEARCH + args;
        fetchBook(context, url, criteria.getFetchCovers(), book);
        return book;
    }

    /**
     * Fetch a book by url.
     *
     * @param context     Current context
     * @param url         to fetch
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws StorageException      on storage related failures
     * @throws SearchException       on generic exceptions (wrapped) during search
     * @throws IllegalStateException if the SAX parser could not be created
     */
    private void fetchBook(@NonNull final Context context,
                           @NonNull final String url,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Book book)
            throws StorageException,
                   SearchException {

        httpGet = createGetDocumentRequest(context);

        try {
            // get and store the result into a string.
            final String response = httpGet.getAsString(url, (con, s) -> s);

            final JSONObject document = new JSONObject(response);
            // https://www.googleapis.com/books/v1/volumes?q=intitle:flowers+inauthor:keyes
            //
            // {
            //  "kind": "books#volumes",
            //  "totalItems": 4,
            //  "items": [
            //    {
            //      "kind": "books#volume",
            // ...
            final int numFound = document.optInt("totalItems");
            if (numFound < 1) {
                return;
            }

            // Grab the first one found
            final JSONObject edition = document.getJSONArray("items")
                                               .getJSONObject(0);
            parse(context, edition, fetchCovers, book);

        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            httpGet = null;
        }
    }

    /**
     * Parse the results, and build the book.
     * <p>
     * <a href="https://developers.google.com/books/docs/v1/reference/volumes">
     * all possible result fields</a>
     * <p>
     * Example:
     * <pre>
     *     {
     *       "kind": "books#volume",
     *       "id": "9ygPPQAACAAJ",
     *       "etag": "f1WSNi06Bns",
     *       "selfLink": "https://www.googleapis.com/books/v1/volumes/9ygPPQAACAAJ",
     *       "volumeInfo": {
     *         "title": "Flores para Algernon",
     *         "authors": [
     *           "Daniel Keyes"
     *         ],
     *         "publisher": "Lectorum Publications",
     *         "publishedDate": "2004",
     *         "description": "After a mouse gets out of a maze faster ...",
     *         "industryIdentifiers": [
     *           {
     *             "type": "ISBN_10",
     *             "identifier": "8467503483"
     *           },
     *           {
     *             "type": "ISBN_13",
     *             "identifier": "9788467503487"
     *           }
     *         ],
     *         "readingModes": {
     *           "text": false,
     *           "image": false
     *         },
     *         "pageCount": 0,
     *         "printType": "BOOK",
     *         "categories": [
     *           "Brain"
     *         ],
     *         "averageRating": 5,
     *         "ratingsCount": 1,
     *         "maturityRating": "NOT_MATURE",
     *         "allowAnonLogging": false,
     *         "contentVersion": "preview-1.0.0",
     *         "panelizationSummary": {
     *           "containsEpubBubbles": false,
     *           "containsImageBubbles": false
     *         },
     *         "imageLinks": {
     *           "smallThumbnail": "http://books.google.com/books/content?id=9ygPPQAACAAJ&printsec=frontcover&img=1&zoom=5&source=gbs_api",
     *           "thumbnail": "http://books.google.com/books/content?id=9ygPPQAACAAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api"
     *         },
     *         "language": "es",
     *         "previewLink": "http://books.google.co.uk/books?id=9ygPPQAACAAJ&dq=intitle:flowers+inauthor:keyes&hl=&cd=1&source=gbs_api",
     *         "infoLink": "http://books.google.co.uk/books?id=9ygPPQAACAAJ&dq=intitle:flowers+inauthor:keyes&hl=&source=gbs_api",
     *         "canonicalVolumeLink": "https://books.google.com/books/about/Flores_para_Algernon.html?hl=&id=9ygPPQAACAAJ"
     *       },
     *       "saleInfo": {
     *         "country": "GB",
     *         "saleability": "NOT_FOR_SALE",
     *         "isEbook": false
     *       },
     *       "accessInfo": {
     *         "country": "GB",
     *         "viewability": "NO_PAGES",
     *         "embeddable": false,
     *         "publicDomain": false,
     *         "textToSpeechPermission": "ALLOWED",
     *         "epub": {
     *           "isAvailable": false
     *         },
     *         "pdf": {
     *           "isAvailable": false
     *         },
     *         "webReaderLink": "http://play.google.com/books/reader?id=9ygPPQAACAAJ&hl=&source=gbs_api",
     *         "accessViewStatus": "NONE",
     *         "quoteSharingAllowed": false
     *       },
     *       "searchInfo": {
     *         "textSnippet": "After a mouse gets out of a maze faster than he does, ..."
     *       }
     *     }
     * </pre>
     *
     * @param context     Current context
     * @param edition     JSON result data
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws StorageException on storage related failures
     */
    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final JSONObject edition,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws StorageException {

        final String googleId = edition.optString("id", null);
        book.setIdentifierValue(Identifier.SID_GOOGLE, googleId);

        final JSONObject volumeInfo = edition.optJSONObject("volumeInfo");
        if (volumeInfo == null) {
            return;
        }

        parseVolumeInfo(context, volumeInfo, book);

        final JSONObject saleInfo = edition.optJSONObject("saleInfo");
        if (saleInfo != null) {
            parseSaleInfo(context, saleInfo, book);
        }

        if (isCancelled()) {
            return;
        }

        final JSONObject imageLinks = volumeInfo.optJSONObject("imageLinks");
        if (imageLinks != null && fetchCovers[0]) {
            searchBestCover(context, imageLinks, book.getRawProductCode()).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }


    private void parseVolumeInfo(@NonNull final Context context,
                                 @NonNull final JSONObject volumeInfo,
                                 @NonNull final Book book) {
        JSONArray a;
        String s;

        final Locale locale = getLocale(context);

        s = volumeInfo.optString("title", null);
        if (s != null && !s.isBlank()) {
            book.setTitle(s);
        }

        a = volumeInfo.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            parseAuthors(a, book);
        }

        s = volumeInfo.optString("publisher", null);
        if (s != null && !s.isBlank()) {
            book.add(Publisher.from(s));
        }
        s = volumeInfo.optString("publishedDate", null);
        if (s != null && !s.isBlank()) {
            parserHelper.addPublicationDate(context, locale, s, book);
        }

        s = volumeInfo.optString("description", null);
        if (s != null && !s.isBlank()) {
            book.setDescription(s);
        }

        a = volumeInfo.optJSONArray("industryIdentifiers");
        if (a != null && !a.isEmpty()) {
            parseIdentifiers(a, book);
        }

        final int pageCount = volumeInfo.optInt("pageCount");
        if (pageCount > 0) {
            book.setPages(pageCount);
        }

        // Google documents this is a "double" with values 0..5,
        // so we rely on decimal separator "." ... flw...
        final float rating = volumeInfo.optFloat("averageRating");
        if (!Float.isNaN(rating) && rating > 0) {
            ratingParser.normalise(rating).ifPresent(book::setRating);
        }

        s = volumeInfo.optString("language", null);
        if (s != null && !s.isEmpty()) {
            book.setLanguage(s);
        }

        a = volumeInfo.optJSONArray("categories");
        if (a != null && !a.isEmpty()) {
            final List<String> tags = new ArrayList<>();
            for (int g = 0; g < a.length(); g++) {
                final String category = a.optString(g);
                if (!category.isEmpty()) {
                    tags.add(category);
                }
            }
            parserHelper.setTags(tags, book);
        }
        // BOOK or MAGAZINE : ignored
        //s = volumeInfo.optString("printType", null);
    }

    private void parseSaleInfo(@NonNull final Context context,
                               @NonNull final JSONObject saleInfo,
                               @NonNull final Book book) {
        final boolean isEbook = saleInfo.optBoolean("isEbook");
        if (isEbook) {
            book.setFormat(context.getString(R.string.book_format_ebook));
        }

        final JSONObject listPrice = saleInfo.optJSONObject("listPrice");
        if (listPrice == null) {
            return;
        }
        final String currencyCode = listPrice.optString("currencyCode", null);
       if (currencyCode == null || currencyCode.isEmpty()) {
            return;
        }
        // Google documents this as a "double", hence, we rely on decimal separator "." ... flw...
        // o.optBigDecimal() will use doubleValue(), so its useless.
        final Object rawPrice = listPrice.opt("amount");
        if (rawPrice instanceof Number) {
            final BigDecimal price = new BigDecimal(rawPrice.toString());
            book.setPriceListed(MoneyParser.parse(price, currencyCode));
        }
    }

    /**
     * Authors are a plain array of name strings.
     *
     * @param a    array with author elements
     * @param book destination
     */
    private void parseAuthors(@NonNull final JSONArray a,
                              @NonNull final Book book) {
        for (int i = 0; i < a.length(); i++) {
            final String name = a.optString(i);
            parserHelper.addAuthor(Author.from(name), AuthorRole.UNKNOWN, book, false);
        }
    }

    /**
     * Industry standard identifiers for this volume.
     * Identifier type. Possible values are ISBN_10, ISBN_13, ISSN and OTHER.
     *
     * @param a    array with identifier elements
     * @param book destination
     */
    private void parseIdentifiers(@NonNull final JSONArray a,
                                  @NonNull final Book book) {
        final Map<String, String> all = new HashMap<>();

        for (int i = 0; i < a.length(); i++) {
            final JSONObject entry = a.optJSONObject(i);
            if (entry != null) {
                final String type = entry.optString("type");
                final String identifier = entry.optString("identifier");
                if (!type.isEmpty() && !identifier.isEmpty()) {
                    all.put(type, identifier);
                }
            }
        }

        // Just grab the "best" one we can get; but ignore "OTHER"
        Stream.of("ISBN_13", "ISBN_10", "ISSN")
              .filter(all::containsKey)
              .findFirst()
              .ifPresent(key -> book.setRawProductCode(all.get(key)));
    }

    @NonNull
    private Optional<String> searchBestCover(@NonNull final Context context,
                                             @NonNull final JSONObject imageLinks,
                                             @NonNull final String isbn)
            throws StorageException {

        Optional<String> oFileSpec = searchCover(context, imageLinks, ImageWebSize.Large, isbn);
        if (oFileSpec.isEmpty()) {
            oFileSpec = searchCover(context, imageLinks, ImageWebSize.Medium, isbn);
            if (oFileSpec.isEmpty()) {
                oFileSpec = searchCover(context, imageLinks, ImageWebSize.Small, isbn);
            }
        }
        return oFileSpec;
    }

    /**
     * Common code to do the actual cover search.
     *
     * @param context    Current context
     * @param imageLinks the list (JSON object) with image links
     * @param size       of image to get.
     * @param isbn       of the book
     *
     * @return File fileSpec, or {@code Optional.empty()} on failure
     *
     * @throws StorageException on storage related failures
     */
    @NonNull
    private Optional<String> searchCover(@NonNull final Context context,
                                         @NonNull final JSONObject imageLinks,
                                         @NonNull final ImageWebSize size,
                                         @NonNull final String isbn)
            throws StorageException {

        Optional<String> oUrl = Optional.empty();
        /*
         * Possible element strings:
         * <pre>
         *  thumbnail       thumbnail size (width of ~128 pixels).
         *  small           small size (width of ~300 pixels).
         *  medium          medium size (width of ~575 pixels).
         *  large           large size (width of ~800 pixels).
         *  smallThumbnail  small thumbnail size (width of ~80 pixels).
         *  extraLarge      extra large size (width of ~1280 pixels).
         * </pre>
         */
        switch (size) {
            case Large:
                oUrl = parseImageUrl(imageLinks, "extraLarge");
                if (oUrl.isEmpty()) {
                    oUrl = parseImageUrl(imageLinks, "large");
                }
                break;
            case Medium:
                oUrl = parseImageUrl(imageLinks, "medium");
                if (oUrl.isEmpty()) {
                    oUrl = parseImageUrl(imageLinks, "small");
                }
                break;
            case Small:
                oUrl = parseImageUrl(imageLinks, "thumbnail");
                if (oUrl.isEmpty()) {
                    oUrl = parseImageUrl(imageLinks, "smallThumbnail");
                }
                break;
        }

        if (oUrl.isPresent()) {
            return saveImage(context, oUrl.get(), null, isbn, 0, size);
        }
        return Optional.empty();
    }

    @NonNull
    private Optional<String> parseImageUrl(@NonNull final JSONObject imageLinks,
                                           @NonNull final String key) {
        String url = imageLinks.optString(key, null);
        if (url == null || url.isEmpty()) {
            return Optional.empty();
        }
        // 2024-10-31: the urls are "http", seriously Google?
        if (url.startsWith("http:")) {
            url = "https:" + url.substring(5);
        }
        return Optional.of(url);
    }

    @Override
    @AnyThread
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (httpGet != null) {
                httpGet.cancel();
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
        // return URLEncoder.encode(s, "UTF-8");
        return SPACE_LITERAL.matcher(s).replaceAll("%20");
    }

    @NonNull
    @Override
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 0) final int cIdx,
                                                 @Nullable final ImageWebSize size)
            throws StorageException, SearchException, CredentialsException {
        if (altEdition instanceof AltEditionProductCode) {
            final AltEditionProductCode edition = (AltEditionProductCode) altEdition;
            final BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setProductCode(edition.getCode(), ScanMode.Off);
            return searchByIsbn(context, criteria)
                    .getImage(context, cIdx)
                    .map(File::getAbsolutePath);
        }
        return Optional.empty();
    }
}


