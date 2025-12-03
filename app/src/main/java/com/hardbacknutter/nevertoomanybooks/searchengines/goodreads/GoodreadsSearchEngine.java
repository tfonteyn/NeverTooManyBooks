/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.searchengines.goodreads;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.network.FutureHttp;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.mappers.AuthorTypeMapper;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.goodreads.com">https://www.goodreads.com</a>
 * <p>
 * Goodreads is owned by Amazon and has shut their API down.
 * <p>
 * But in 2022 the html pages started to contain a json blob making them easy to parse.
 */
public class GoodreadsSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText,
                   SearchEngine.ByExternalId {

    public static final String SITE_URL = "https://www.goodreads.com";
    public static final String BOOK_URL = "https://www.goodreads.com/book/show/%s";
    public static final String AUTHOR_URL = "https://www.goodreads.com/author/show/%s";

    /**
     * Fetch the Goodreads id.
     * Param 1: isbn
     */
    private static final String GET_GOODREADS_ID = "/book/auto_complete?format=json&q=%s";

    /**
     * Search by text.
     * <p>
     * Param 1: url encoded keywords
     */
    private static final String BY_TEXT = "/search?search_type=books&search[query]=%s";
    /**
     * Search by Goodreads id.
     * <p>
     * Param 1: sid
     */
    private static final String BY_GOODREADS_ID = "/book/show/%s";

    /**
     * The site uses milliseconds from the epoch for timestamps.
     * We use this made-up default of 123 milliseconds to try and distinguish "not set".
     * (presuming rightly or wrongly that {@code 0} might be used by the site as their default).
     */
    private static final int EPOCH_NULL_VALUE = 123;
    /** divider to convert milliseconds TO SECONDS. */
    private static final int MILLI_TO_SECONDS = 1000;

    private static final Pattern PARAMS_BOOK_ID_PATTERN = Pattern.compile("(\\d+).*");

    private static final Pattern LANG_SPLITTER = Pattern.compile("[,;]");
    /** Example: {@code "https://www.goodreads.com/author/show/40652983.Nuanxed"}. */
    private static final Pattern AUTHOR_WEB_URL_ID = Pattern.compile(
            "https://www.goodreads.com/author/show/(\\d+)\\..*");

    private final RatingParser ratingParser;
    private final AuthorTypeMapper authorTypeMapper;
    private final AuthorResolverHelper authorResolverHelper;
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
    public GoodreadsSearchEngine(@NonNull final Context appContext,
                                 @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
        ratingParser = new RatingParser(5);
        authorTypeMapper = new AuthorTypeMapper();
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
        return new EngineId.Builder("goodreads",
                                    R.string.site_goodreads,
                                    List.of(R.string.site_description_english_and_more,
                                            R.string.site_description_catalog),
                                    "https://www.goodreads.com",
                                    Locale.US)
                .setIdentifierKey(Identifier.SID_GOODREADS)
                .setPreferenceFragmentClazz(GoodreadsPreferencesFragment.class);
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final String url = getHostUrl(context) + String.format(BY_GOODREADS_ID, externalId);
        final Document document = loadDocument(context, url, null);

        final Book book = new Book();
        if (!isCancelled()) {
            parse(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final long sid = getGoodreadsId(context, validIsbn);
        if (sid > 0) {
            return searchByExternalId(context, String.valueOf(sid), fetchCovers);
        }

        // Fallback to a text search for the ISBN
        return search(context, validIsbn, fetchCovers);
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @Nullable final String code,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");
        if (code != null && !code.isEmpty()) {
            words.add(code);
        }

        return search(context, words.toString(), fetchCovers);
    }

    @NonNull
    private Book search(@NonNull final Context context,
                        @NonNull final CharSequence queryParams,
                        @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, StorageException {
        final Book book = new Book();
        // Sanity check
        if (queryParams.length() == 0) {
            return book;
        }
        final String url = getHostUrl(context) + String.format(BY_TEXT, queryParams);
        final Document document = loadDocument(context, url, null);

        if (!isCancelled()) {
            if (document.head().select("meta").stream().anyMatch(
                    meta -> "og:type".equals(meta.attr("property"))
                            && "books.book".equals(meta.attr("content")))) {
                // we have a single book
                parse(context, document, fetchCovers, book);
            } else {
                parseMultiResult(context, document, fetchCovers, book);
            }
        }
        return book;
    }

    /**
     * Call the site with the ISBN and get the Goodreads id back.
     * <pre>
     *     Request for 9780062683250 returned:
     * [
     *   {
     *     "imageUrl": "https://i.gr-assets.com/images/S/compressed.photo.goodreads.com/books/1581368800i/49867186._SY75_.jpg",
     *     "bookId": "49867186",
     *     "workId": "67924695",
     *     "bookUrl": "/book/show/49867186-the-left-handed-booksellers-of-london",
     * SNIP
     *   }
     * ]
     * </pre>
     *
     * @param context   Current context
     * @param validIsbn to search for, <strong>must</strong> be valid.
     *
     * @return goodreads sid; or {@code 0} when not found
     *
     * @throws StorageException on storage related failures
     * @throws SearchException  on generic exceptions (wrapped) during search
     */
    private long getGoodreadsId(@NonNull final Context context,
                                @NonNull final String validIsbn)
            throws StorageException, SearchException {

        final String url = getHostUrl(context) + String.format(GET_GOODREADS_ID, validIsbn);
        httpGet = createGetDocumentRequest(context);

        try {
            // get and store the result into a string.
            final String response = httpGet.getAsString(url, (con, s) -> s);

            final JSONArray responseArray = new JSONArray(response);
            if (!responseArray.isEmpty()) {
                final JSONObject o = responseArray.getJSONObject(0);

                // We could use "bookUrl" but it's the same nr of steps.
                // Using the id only might result in less tracking?
                return o.optLong("bookId", 0);
            }


        } catch (@NonNull final IOException | JSONException e) {
            throw new SearchException(getEngineId(), e);
        } finally {
            httpGet = null;
        }

        return 0;
    }

    @VisibleForTesting
    void parseMultiResult(@NonNull final Context context,
                          @NonNull final Document document,
                          @NonNull final boolean[] fetchCovers,
                          @NonNull final Book book)
            throws SearchException, CredentialsException, StorageException {

        final Element table = document.selectFirst("table.tableList");
        if (table == null) {
            return;
        }
        final Element firstRow = table.selectFirst("tr");
        if (firstRow == null) {
            return;
        }
        final Elements tds = firstRow.select("td");
        if (tds.size() < 2) {
            return;
        }
        final Element a = tds.get(1).selectFirst("a.bookTitle");
        if (a != null) {
            final String href = a.attr("href");
            if (!href.isEmpty()) {
                // pretend we click on the first result
                final String url = getHostUrl(context) + href;
                final Document redirected = loadDocument(context, url, null);

                if (!isCancelled()) {
                    parse(context, redirected, fetchCovers, book);
                }
            }
        }
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final Document document,
               @NonNull final boolean[] fetchCovers,
               @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        try {
            final Element scriptTag = document.selectFirst("script#__NEXT_DATA__");
            if (scriptTag != null) {
                final JSONObject root = new JSONObject(scriptTag.data());
                parse(context, root, book, fetchCovers);
            }
        } catch (@NonNull final JSONException e) {
            throw new SearchException(getEngineId(), e);
        }
    }

    @VisibleForTesting
    void parse(@NonNull final Context context,
               @NonNull final JSONObject root,
               @NonNull final Book book,
               @NonNull final boolean[] fetchCovers)
            throws JSONException, StorageException, SearchException, CredentialsException {

        final JSONObject props = root.optJSONObject("props");
        if (props == null) {
            return;
        }
        final JSONObject pageProps = props.optJSONObject("pageProps");
        if (pageProps == null) {
            return;
        }
        final JSONObject apolloState = pageProps.optJSONObject("apolloState");
        if (apolloState == null) {
            return;
        }

        final JSONObject params = pageProps.optJSONObject("params");
        if (params != null) {
            final String bookIdStr = params.optString("book_id");
            final Matcher matcher = PARAMS_BOOK_ID_PATTERN.matcher(bookIdStr);
            if (matcher.find()) {
                // Paranoia: parse to make sure it's a number
                final long bookId = NumberParser.toLong(matcher.group(1));
                if (bookId > 0) {
                    book.setIdentifierValue(Identifier.SID_GOODREADS, bookId);
                }
            }
        }

        // There can be multiple Book keys: check for one with a "title" element.
        // Other entries seem to references to other editions.
        final Optional<JSONObject> bookObj = apolloState
                .keySet()
                .stream()
                .filter(key -> key.startsWith("Book:"))
                .map(apolloState::getJSONObject)
                .filter(o -> o.keySet().contains("title"))
                .findFirst();

        if (bookObj.isPresent()) {
            parseBook(context, apolloState, bookObj.get(), book, fetchCovers);
        }
    }

    private void parseBook(@NonNull final Context context,
                           @NonNull final JSONObject apolloState,
                           @NonNull final JSONObject o,
                           @NonNull final Book book,
                           @NonNull final boolean[] fetchCovers)
            throws JSONException, StorageException, CredentialsException {
        final String title = o.optString("title");
        if (title.isEmpty()) {
            return;
        }
        book.setTitle(title);

        if (book.getIdentifierValue(Identifier.SID_GOODREADS).isEmpty()) {
            final long legacyId = o.optLong("legacyId");
            if (legacyId > 0) {
                book.setIdentifierValue(Identifier.SID_GOODREADS, legacyId);
            }
        }

        final String description = o.optString("description", null);
        if (description != null) {
            book.setDescription(description);
        }

        final Locale locale = getLocale(context);

        parseContributors(context, apolloState, o.optJSONObject("primaryContributorEdge"),
                          locale, book);
        final JSONArray secondary = o.optJSONArray("secondaryContributorEdges");
        if (secondary != null) {
            for (int i = 0; i < secondary.length(); i++) {
                parseContributors(context, apolloState, secondary.optJSONObject(i), locale, book);
            }
        }

        final JSONArray bookSeries = o.optJSONArray("bookSeries");
        if (bookSeries != null) {
            parseSeries(apolloState, bookSeries, book);
        }

        final JSONObject details = o.optJSONObject("details");
        if (details != null) {
            parseBookDetails(details, book);
        }

        final JSONArray genres = o.optJSONArray("bookGenres");
        if (genres != null) {
            parseBookGenres(genres, book);
        }

        final JSONObject work = o.optJSONObject("work");
        if (work != null) {
            parseWork(apolloState, work, book);
        }

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String url = o.optString("imageUrl");
            if (!url.isBlank()) {
                saveImage(context, url, null, book.getIsbn(), 0, null).ifPresent(
                        fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
            }
        }
    }

    private void parseBookDetails(@NonNull final JSONObject details,
                                  @NonNull final Book book) {
        String s;
        s = details.optString("asin", null);
        if (s != null && !s.isEmpty()) {
            book.setIdentifierValue(Identifier.SID_ASIN, s);
        }
        s = details.optString("format", null);
        if (s != null && !s.isEmpty()) {
            book.setFormat(s);
        }
        s = details.optString("numPages", null);
        if (s != null && !s.isEmpty()) {
            book.setPages(s);
        }
        final long epochMillis = details.optLong("publicationTime", EPOCH_NULL_VALUE);
        if (epochMillis != EPOCH_NULL_VALUE) {
            // UTC... we could be one day off... oh well...
            book.setPublicationDate(LocalDateTime.ofEpochSecond(
                    epochMillis / MILLI_TO_SECONDS, 0, ZoneOffset.UTC));
        }
        s = details.optString("publisher", null);
        if (s != null && !s.isEmpty()) {
            book.add(Publisher.from(s));
        }
        s = details.optString("isbn13", null);
        if (s != null && !s.isEmpty()) {
            book.setIsbn(s);
        } else {
            s = details.optString("isbn", null);
            if (s != null && !s.isEmpty()) {
                book.setIsbn(s);
            }
        }
        final JSONObject lang = details.optJSONObject("language", null);
        if (lang != null) {
            s = lang.optString("name", null);
            if (s != null && !s.isEmpty()) {
                book.setLanguage(mapLanguage(s));
            }
        }
    }

    private void parseWork(@NonNull final JSONObject apolloState,
                           @NonNull final JSONObject bookWork,
                           @NonNull final Book book) {
        final String workRef = bookWork.optString("__ref");
        if (workRef.isEmpty()) {
            return;
        }
        final JSONObject work = apolloState.optJSONObject(workRef);
        if (work == null) {
            return;
        }

        JSONObject o;
        o = work.optJSONObject("details");
        if (o != null) {
            final long epochMillis = o.optLong("publicationTime", EPOCH_NULL_VALUE);
            if (epochMillis != EPOCH_NULL_VALUE) {
                // UTC... we could be one day off... oh well...
                book.setFirstPublicationDate(LocalDateTime.ofEpochSecond(
                        epochMillis / MILLI_TO_SECONDS, 0, ZoneOffset.UTC));
            }

            final String originalTitle = o.optString("originalTitle", null);
            if (originalTitle != null && !originalTitle.isEmpty()
                // sometimes it's a copy... ignore those
                && !originalTitle.equals(book.getTitle())) {
                book.setTranslatedFromTitle(originalTitle);
            }
        }

        o = work.optJSONObject("stats");
        if (o != null) {
            final float averageRating = o.optFloat("averageRating");
            ratingParser.normalize(averageRating).ifPresent(book::setRating);
        }
    }

    /**
     * A typical entry contains a single name.
     * <pre>{@code
     *     "Contributor:kca://author/amzn1.gr.author.v1.7-e8JQXbSdWm6r59JBewuw": {
     *        "__typename": "Contributor",
     *        "id": "kca://author/amzn1.gr.author.v1.7-e8JQXbSdWm6r59JBewuw",
     *        "name": "Anja Kootz",
     *        "webUrl": "https://www.goodreads.com/author/show/6552762.Anja_Kootz",
     *        "isGrAuthor": false
     *  },
     * }</pre>
     * <p>
     * A broken entry, this one has two names squashed into one record.
     * We're not going to attempt/parse this in any special way.
     * Github #139.
     *
     * <pre>{@code
     *     "Contributor:kca://author/amzn1.gr.author.v1.SSat0nVoXAkYHijbhiK5WA": {
     *         "__typename": "Contributor",
     *         "id": "kca://author/amzn1.gr.author.v1.SSat0nVoXAkYHijbhiK5WA",
     *         "legacyId": 13687877,
     *         "name": "Corinne Maier, Anne Simon",
     *         "description": "",
     *         "isGrAuthor": false,
     *         "works": {
     *             "__typename": "ContributorWorksConnection",
     *             "totalCount": 0
     *         },
     *         "profileImageUrl": "https://i.gr-assets.com/images/S/compressed.photo.goodreads.com/nophoto/user/u_700x933.png",
     *         "webUrl": "https://www.goodreads.com/author/show/13687877.Corinne_Maier_Anne_Simon",
     *         "viewerIsFollowing": null,
     *         "followers": {
     *             "__typename": "ContributorFollowersConnection",
     *             "totalCount": 0
     *         },
     *         "user": null
     *     },
     * }</pre>
     */
    private void parseContributors(@NonNull final Context context,
                                   @NonNull final JSONObject apolloState,
                                   @Nullable final JSONObject contributor,
                                   @NonNull final Locale locale,
                                   @NonNull final Book book) {
        if (contributor == null) {
            return;
        }

        final int role = authorTypeMapper.map(locale, contributor.optString("role"));
        final JSONObject node = contributor.optJSONObject("node");
        if (node == null) {
            return;
        }

        final String ref = node.optString("__ref");
        if (ref.isEmpty()) {
            return;
        }

        final JSONObject refObj = apolloState.optJSONObject(ref);
        if (refObj == null) {
            return;
        }

        final String name = refObj.optString("name");
        if (name.isEmpty()) {
            return;
        }

        final Author author = mapAuthor(context, name);
        author.setType(role);
        // Get the legacyId as the SID_GOODREADS_BOOK.
        // It is this one we need to construct url's.
        final String legacyId = refObj.optString("legacyId");
        if (!legacyId.isEmpty()) {
            author.setIdentifierValue(Identifier.SID_GOODREADS, legacyId);
        } else {
            // if the explicit legacyId is absent, try the webUrl
            final String webUrl = refObj.optString("webUrl");
            if (!webUrl.isEmpty()) {
                final Matcher matcher = AUTHOR_WEB_URL_ID.matcher(webUrl);
                if (matcher.find()) {
                    final String siId = matcher.group(1);
                    if (siId != null) {
                        author.setIdentifierValue(
                                Identifier.SID_GOODREADS, siId);
                    }
                }
            }
        }
        book.add(author);
    }

    private void parseSeries(@NonNull final JSONObject apolloState,
                             @NonNull final JSONArray bookSeries,
                             @NonNull final Book book) {
        for (int i = 0; i < bookSeries.length(); i++) {
            final JSONObject bs = bookSeries.optJSONObject(i);
            if (bs != null) {
                final String numberInSeries = bs.optString("userPosition");
                final JSONObject seriesRef = bs.optJSONObject("series");
                if (seriesRef != null) {
                    final String ref = seriesRef.optString("__ref");
                    if (!ref.isEmpty()) {
                        final JSONObject refObj = apolloState.optJSONObject(ref);
                        if (refObj != null) {
                            final String title = refObj.optString("title");
                            if (!title.isEmpty()) {
                                book.add(Series.from(title, numberInSeries));
                            }
                        }
                    }
                }
            }
        }
    }

    private void parseBookGenres(@NonNull final JSONArray genres,
                                 @NonNull final Book book) {
        //noinspection DataFlowIssue
        final Set<String> tagsToIgnore = getEngineId().getConfig().getTagsToIgnore();

        final Set<Tag> result = new HashSet<>();
        for (int i = 0; i < genres.length(); i++) {
            final JSONObject bg = genres.optJSONObject(i);
            if (bg != null) {
                final JSONObject genre = bg.optJSONObject("genre");
                if (genre != null) {
                    final String name = genre.optString("name");
                    if (!name.isEmpty() && !tagsToIgnore.contains(name)) {
                        result.add(new Tag(name));
                    }
                }
            }
        }
        if (!result.isEmpty()) {
            book.setTags(result);
        }
    }

    /**
     * Why goodreads... why...
     *
     * <pre>
     *     "Dutch; Flemish"
     *     "Greek, Modern (1453-)"
     *     "Spanish; Castilian"
     * </pre>
     *
     * @param s language
     *
     * @return cleaned string
     */
    @NonNull
    private String mapLanguage(@NonNull final String s) {
        if (s.contains(",") || s.contains(";")) {
            return LANG_SPLITTER.split(s)[0];
        }
        return s;
    }

    @NonNull
    Author mapAuthor(@NonNull final Context context,
                     @NonNull final String s) {
        if ("Unknown Author".equals(s)) {
            return Author.createUnknownAuthor(context);
        }
        return Author.from(s);
    }

    @Override
    @AnyThread
    public void cancel() {
        super.cancel();

        if (httpGet != null) {
            httpGet.cancel();
        }
    }
}

