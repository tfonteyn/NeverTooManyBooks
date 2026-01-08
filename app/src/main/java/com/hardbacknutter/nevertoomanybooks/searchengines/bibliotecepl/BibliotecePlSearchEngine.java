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

package com.hardbacknutter.nevertoomanybooks.searchengines.bibliotecepl;

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
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
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
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BibliotecePlSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByText {

    public static final String SITE_URL = "https://w.bibliotece.pl";
    public static final String BOOK_URL = "https://w.bibliotece.pl/%s";
    // The site has no author ids - they use a pure text search when searching
    // all books for a specific author.
    public static final String AUTHOR_URL = null;

    private static final Locale SITE_LOCALE = new Locale("pl", "PL");
    // also used as the identifier value
    private static final String PREFERENCE_KEY = "bibliotecepl";
    /**
     * Optional prefixes for the search.
     *
     * <pre>
     *      t: Titles of series, works and volumes
     *      o: author(s)
     *      w: publisher
     *      r: publication year
     *      isbn: ISBNs
     *      issn: ISSNs
     *      tag: tag names
     * </pre>
     *
     * @see <a href="https://w.bibliotece.pl/info/search/">advanced search info</a>
     */
    private static final String SEARCH = "/search/?q=";
    private static final String TAG = "BibliotecePlSearchEng";
    private static final Pattern SID_FROM_LOCATION_PATTERN = Pattern.compile(
            "https://w\\.bibliotece\\.pl/(\\d+).*/.*");
    private static final Pattern AUTHOR_DATE_SUFFIX = Pattern.compile(
            "^(.*?)\\s*(?:\\(|$)");

    // Use all lowercase labels and include the trailing ':'
    private static final String LABEL_SUBTITLE = "inne tytuły:";
    private static final String LABEL_ORIGINAL_TITLE = "tytuł oryginalny:";

    private static final String LABEL_AUTHOR = "autor:";
    private static final String LABEL_AUTHORS = "autorzy:";

    private static final String LABEL_AFTERWORD = "posłowie:";
    private static final String LABEL_CONTRIBUTOR = "oraz:";
    private static final String LABEL_EDITOR = "redakcja:";
    private static final String LABEL_FOREWORD = "przedmowa:";
    private static final String LABEL_ILLUSTRATOR = "ilustracje:";
    private static final String LABEL_INTRODUCTION = "wstęp:";
    private static final String LABEL_NARRATOR = "lektor:";
    private static final String LABEL_NARRATORS = "lektorzy:";
    private static final String LABEL_SCENARIST = "scenariusz:";
    private static final String LABEL_TRANSLATOR = "tłumacz:";
    private static final String LABEL_TRANSLATORS = "tłumaczenie:";

    private static final String LABEL_FIRST_PUB_DATE = "wyd. w latach:";
    private static final String LABEL_PUBLISHER = "wydawca:";
    private static final String LABEL_PUBLISHERS = "wydawcy:";
    private static final String LABEL_SERIES = "wydane w seriach:";
    private static final String LABEL_TAGS = "autotagi:";

    private static final String SPAN_DATA_IPUB_SEARCH_O = "span[data-ipub-search=o]";
    private static final String SPAN_DATA_IPUB_SEARCH_T = "span[data-ipub-search=t]";
    private static final String SPAN_DATA_IPUB_SEARCH_W = "span[data-ipub-search=w]";

    private static final String SEARCH_PREFIX_ISBN = "isbn:";
    private static final String SEARCH_PREFIX_TITLE = "t:";
    private static final String SEARCH_PREFIX_PERSON = "o:";
    private static final String SEARCH_PREFIX_PUBLISHER = "w:";
    private static final String AUTHOR_IS_CREATOR = "creator";
    private static final String AUTHOR_IS_CONTRIBUTOR = "contributor";
    @NonNull
    private final RatingParser ratingParser;
    private final AuthorResolverHelper authorResolverHelper;


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
    public BibliotecePlSearchEngine(@NonNull final Context appContext,
                                    @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
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
        return new EngineId.Builder(PREFERENCE_KEY,
                                    R.string.site_bibliotece_pl,
                                    List.of(R.string.site_description_polish,
                                            R.string.site_description_catalog),
                                    SITE_URL,
                                    SITE_LOCALE)
                .setIdentifierKey(Identifier.SID_BIBLIOTECE_PL)
                .setPreferenceFragmentClazz(BibliotecePlPreferencesFragment.class)
                .setConfig(cb -> cb
                        // "books"
                        .setTagsToIgnore(Set.of("książki"))
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final Book book = new Book();

        final String url = getHostUrl() + '/' + externalId;
        final Document document = loadDocument(context, url, null);

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
        final String url = getHostUrl() + SEARCH + SEARCH_PREFIX_ISBN + validIsbn;
        final Document document = loadDocument(context, url, null);

        final Book book = new Book();
        // force the isbn here as the result (single book) can contain multiple isbn
        book.setIsbn(validIsbn);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @Nullable final String code,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final StringJoiner words = new StringJoiner(" ");
        String tmp;
        // t: Titles of series, works and volumes
        // o: author(s)
        // w: publisher
        tmp = criteria.getTitle();
        if (!tmp.isEmpty()) {
            words.add(SEARCH_PREFIX_TITLE).add(tmp);
        }
        tmp = criteria.getAuthor();
        if (!tmp.isEmpty()) {
            words.add(SEARCH_PREFIX_PERSON).add(tmp);
        }
        tmp = criteria.getPublisher();
        if (!tmp.isEmpty()) {
            words.add(SEARCH_PREFIX_PUBLISHER).add(tmp);
        }

        // hardcoded to isbn although this could also be "issn:"
        if (code != null && !code.isEmpty()) {
            words.add(SEARCH_PREFIX_ISBN).add(code);
        }

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        final String url = getHostUrl() + SEARCH + words;
        final Document document = loadDocument(context, url, null);
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
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     * @throws StorageException     on storage related failures
     */
    @VisibleForTesting
    @WorkerThread
    public void parseMultiResult(@NonNull final Context context,
                                 @NonNull final Document document,
                                 @NonNull final boolean[] fetchCovers,
                                 @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {
        // Grab the first search result, and redirect to that page
        Element dataElement = document.selectFirst("div#results");
        if (dataElement != null) {
            dataElement = dataElement.selectFirst("a.result-title");
            // Will be null when no book(s) found
            if (dataElement != null) {
                String url = dataElement.attr("href");
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

        final Element work = document.selectFirst("div#work");
        if (work == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no work?");
            return;
        }

        final Element bookData = work.selectFirst("table.book-data");
        if (bookData == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no book-data?");
            return;
        }

        final Element titleElement = work.selectFirst("span.main-title");
        if (titleElement == null) {
            LoggerFactory.getLogger().w(TAG, "parse", "no main-title?");
            return;
        }

        book.setTitle(titleElement.text());
        // Some books have a subtitle as an element next to the title
        parseSubtitle(work, "h2.subtitle", book);

        parseSid(document, book);
        parseMetas(document, book);

        // parsed by scanning for labels
        parseMain(bookData, book);
        // parsed without label usage
        parseIsbn(bookData, book);
        parseRating(bookData, book);
        parseDescription(context, bookData, book);

        // Optional secondary details section
        final Element bookDetails = work.selectFirst("table#details");
        if (bookDetails != null) {
            parseSecondary(bookDetails, book);
            // Some books have no isbn in the main section, so try again.
            parseIsbn(bookDetails, book);
        }

        // Language is not available, we presume these are all polish.
        book.setLanguage("pl");

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCovers(context, work, book.getIsbn(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void parseSubtitle(@NonNull final Element work,
                               @NonNull final String cssQuery,
                               @NonNull final Book book) {
        // We only get the first element. There can be a second,
        // but from the limited tests done, those are sticker-text, author name repeating...
        final Element stElement = work.selectFirst(cssQuery);
        if (stElement != null) {
            final String text = stElement.text();
            if (!text.isEmpty()) {
                // some translated books have their original title in
                // both LABEL_ORIGINAL_TITLE and LABEL_SUBTITLE (in the details section)
                // check this before accepting the subtitle.
                // This will not always work, as the site is a bit sloppy...
                // "The well..." versus "Well..."
                if (!text.equalsIgnoreCase(book.getString(DBKey.TRANSLATION_ORIGINAL_TITLE))) {
                    // just concat it; if both main and secondary sections had a subtitle,
                    // we end up with the concatenation of ALL titles.
                    // But we've not observed this during testing.
                    book.setTitle(book.getTitle() + " - " + text);
                }
            }
        }
    }

    private void parseIsbn(@NonNull final Element bookData,
                           @NonNull final Book book) {
        // There can be multiple ISBN's listed
        final Elements isbnElements = bookData.select("span[data-ipub-search=isbn]");
        if (isbnElements.isEmpty()) {
            return;
        }

        if (isbnElements.size() == 1) {
            final String isbnStr = ISBN.cleanText(isbnElements.get(0).text());
            if (book.hasIsbn()) {
                // If it's an isbn-10 equal to the one we searched for, grab it.
                final ISBN siteIsbn = new ISBN(isbnStr, true);
                final ISBN searchIsbn = new ISBN(book.getIsbn(), true);
                if (siteIsbn.isType(ISBN.Type.Isbn10) && searchIsbn.isType(ISBN.Type.Isbn13)
                    && siteIsbn.equals(searchIsbn)) {
                    book.setIsbn(isbnStr);
                }
            } else {
                // we got here searching-by-text, grab it
                book.setIsbn(isbnStr);
            }
        } else if (!book.hasIsbn()) {
            // if we don't have an isbn already, simply grab the first
            book.setIsbn(ISBN.cleanText(isbnElements.get(0).text()));
        }
    }

    private void parseSid(@NonNull final Document document,
                          @NonNull final Book book) {
        // There should always be a sid in the url... but paranoia
        final Matcher matcher = SID_FROM_LOCATION_PATTERN.matcher(document.location());
        if (matcher.find()) {
            final String sid = matcher.group(1);
            if (sid != null) {
                book.setIdentifierValue(Identifier.SID_BIBLIOTECE_PL, sid);
            }
        }
    }

    private void parseMetas(@NonNull final Document document,
                            @NonNull final Book book) {
        // These meta elements are not always present.
        // There is a description property, but the text is cut short.
        final Elements metaElements = document.head().select("meta");
        for (final Element meta : metaElements) {
            final String property = meta.attr("property");
            final String content = meta.attr("content");
            switch (property) {
                case "books:release_date": {
                    // 4 digit year
                    book.setPublicationDate(content);
                    break;
                }
                case "books:initial_release_date": {
                    // 4 digit year
                    book.setFirstPublicationDate(content);
                    break;
                }
                case "books:isbn": {
                    if (!book.hasIsbn()) {
                        // there might be multiple, separated by a space; grab the first
                        book.setIsbn(ISBN.cleanText(content.split(" ")[0]));
                    }
                    break;
                }
                case "books:rating:value": {
                    ratingParser.parse(content).ifPresent(book::setRating);
                    break;
                }
                case "og:image": {
                    break;
                }
            }
        }
    }

    private void parseMain(@NonNull final Element bookData,
                           @NonNull final Book book) {
        // Rather annoying...
        // all authors names are listed with the "o" indicator.
        //     <span data-ipub-search="o" itemprop="name">blah</span>
        // These spans are inside a div with an itemprop set to one of these values:
        //    creator, contributor
        // But the actual type can only be derived from the label.
        bookData.select("th")
                .forEach(th -> {
                    final Element td = th.nextElementSibling();
                    if (td != null && "td".equals(td.tagName())) {
                        final String lcLabel = th.text().toLowerCase(SITE_LOCALE);
                        switch (lcLabel) {
                            case LABEL_AUTHOR:
                            case LABEL_AUTHORS: {
                                parseAuthor(td, AUTHOR_IS_CREATOR,
                                            AuthorRole.WRITER, book);
                                break;
                            }
                            case LABEL_CONTRIBUTOR: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.CONTRIBUTOR,
                                            book);
                                break;
                            }
                            case LABEL_FOREWORD: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.FOREWORD, book);
                                break;
                            }
                            case LABEL_AFTERWORD: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.AFTERWORD, book);
                                break;
                            }
                            case LABEL_INTRODUCTION: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.INTRODUCTION,
                                            book);
                                break;
                            }
                            case LABEL_ILLUSTRATOR: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.ARTIST, book);
                                break;
                            }
                            case LABEL_EDITOR: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.EDITOR, book);
                                break;
                            }
                            case LABEL_NARRATOR:
                            case LABEL_NARRATORS: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.NARRATOR, book);
                                break;
                            }
                            case LABEL_SCENARIST: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.WRITER, book);
                                break;
                            }
                            case LABEL_TRANSLATOR:
                            case LABEL_TRANSLATORS: {
                                parseAuthor(td, AUTHOR_IS_CONTRIBUTOR,
                                            AuthorRole.TRANSLATOR, book);
                                break;
                            }

                            case LABEL_SUBTITLE: {
                                parseSubtitle(td, SPAN_DATA_IPUB_SEARCH_T, book);
                                break;
                            }
                            case LABEL_PUBLISHER:
                            case LABEL_PUBLISHERS: {
                                parsePublishers(td, book);
                                break;
                            }
                            case LABEL_SERIES: {
                                parseSeries(td, book);
                                break;
                            }
                            case LABEL_TAGS: {
                                parseTags(td, "div", book);
                                break;
                            }

                            // Do NOT add the below to parseAuthors2(..)
                            case LABEL_ORIGINAL_TITLE: {
                                final Element element = td.selectFirst(SPAN_DATA_IPUB_SEARCH_T);
                                if (element != null) {
                                    final String text = element.text();
                                    if (!text.isEmpty()) {
                                        book.setTranslatedFromTitle(text);
                                    }
                                }
                                break;
                            }

                            case LABEL_FIRST_PUB_DATE: {
                                parsePublicationDate(td, book);
                                break;
                            }

                            // Other labels we know about, but ignore
                            // "Źródło opisu" -> Source of description

                        }
                    }
                });
    }

    private void parseAuthor(@NonNull final Element td,
                             @NonNull final String property,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {
        // There can be multiple authors listed under each
        td.select("div[itemprop=" + property + "]")
          .stream()
          .flatMap(pe -> pe.select(SPAN_DATA_IPUB_SEARCH_O).stream())
          .map(Element::text)
          .forEach(text -> parseAuthorText(text, type, book));
    }

    private void parseAuthorText(@NonNull final CharSequence text,
                                 @AuthorRole.Role final int type,
                                 @NonNull final Book book) {
        final Matcher matcher = AUTHOR_DATE_SUFFIX.matcher(text);
        if (matcher.find()) {
            final String g1 = matcher.group(1);
            if (g1 != null) {
                final String s = cleanName(g1);
                if (!s.isBlank()) {
                    addAuthor(Author.from(s), type, book);
                }
            }
        }
    }

    private void parseSeries(@NonNull final Element td,
                             @NonNull final Book book) {
        td.select(SPAN_DATA_IPUB_SEARCH_T)
          .stream()
          .map(this::cleanName)
          .filter(name -> !name.isBlank())
          .map(Series::from)
          .filter(series -> !book.getSeries().contains(series))
          .forEach(book::add);
    }

    private void parsePublishers(@NonNull final Element bookData,
                                 @NonNull final Book book) {
        // This is extremely annoying...
        // There can be multiple publisher, with a year or year-range next to this element.
        // But it seems impossible to know which publisher matches the ISBN.
        // The best we can do is to add all of them without bothering with the year
        // and let the user sort them out manually.
        bookData.select(SPAN_DATA_IPUB_SEARCH_W)
                .stream()
                .map(this::cleanName)
                .filter(name -> !name.isBlank())
                .map(Publisher::from)
                .filter(publisher -> !book.getPublishers().contains(publisher))
                .forEach(book::add);
    }

    private void parseTags(@NonNull final Element bookData,
                           @NonNull final String subElement,
                           @NonNull final Book book) {
        // We're not using the helper 'setTags(tagNames, book) because
        // this site can have tags in two different sections.
        //noinspection DataFlowIssue
        final Set<String> tagsToIgnore = getEngineId().getConfig().getTagsToIgnore();
        // the cssQuery is based on the page source, and not on the page-inspect
        // as the td.tags element is transformed by JavaScript by the time we inspect it.
        // The sub element can be a 'div' or a 'span'
        final List<Tag> tags = bookData.select(subElement)
                                       .stream()
                                       .map(Element::text)
                                       .filter(t -> !tagsToIgnore.contains(t))
                                       .map(Tag::new)
                                       .collect(Collectors.toList());
        if (!tags.isEmpty()) {
            book.addTags(tags);
        }
    }

    private void parseRating(@NonNull final Element bookData,
                             @NonNull final Book book) {
        // Might already have come from a head/meta tag
        if (book.contains(DBKey.RATING)) {
            return;
        }
        final Element ratingElement = bookData.selectFirst("span[itemprop=ratingValue]");
        if (ratingElement != null) {
            ratingParser.parse(ratingElement.text()).ifPresent(book::setRating);
        }
    }

    private void parseDescription(@NonNull final Context context,
                                  @NonNull final Element bookData,
                                  @NonNull final Book book)
            throws SearchException, CredentialsException {
        final Element summaryElement = bookData.selectFirst("tr.summary");
        if (summaryElement == null) {
            return;
        }
        final Element sumUrl = summaryElement.selectFirst("a[href*=summary]");
        if (sumUrl == null) {
            return;
        }

        String url = sumUrl.attr("href");
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }

        if (isCancelled()) {
            return;
        }
        final Element element = loadDocument(context, url, null)
                .selectFirst("div.summary");
        if (element != null) {
            final String text = cleanText(element);
            if (!text.isBlank()) {
                book.setDescription(text);
            }
        }
    }

    private void parsePublicationDate(@NonNull final Element td,
                                      @NonNull final Book book) {
        // publication year... but given as a range. Parse the first only
        if (!book.contains(DBKey.FIRST_PUBLICATION_DATE)) {
            final Element div = td.selectFirst("div");
            if (div != null) {
                final String year = div.text().split(" ")[0];
                if (year != null && year.length() == 4) {
                    book.setFirstPublicationDate(year);
                }
            }
        }
    }

    private void parseSecondary(@NonNull final Element bookDetails,
                                @NonNull final Book book) {
        bookDetails.select("th")
                   .forEach(th -> {
                       final Element td = th.nextElementSibling();
                       if (td != null) {
                           // Keep in sync with parseAuthors(..)
                           final String lcLabel = th.text().toLowerCase(SITE_LOCALE);
                           switch (lcLabel) {
                               case LABEL_AUTHOR:
                               case LABEL_AUTHORS:
                               case LABEL_SCENARIST: {
                                   parseAuthor2(td, AuthorRole.WRITER, book);
                                   break;
                               }
                               case LABEL_CONTRIBUTOR: {
                                   parseAuthor2(td, AuthorRole.CONTRIBUTOR, book);
                                   break;
                               }
                               case LABEL_FOREWORD: {
                                   parseAuthor2(td, AuthorRole.FOREWORD, book);
                                   break;
                               }
                               case LABEL_AFTERWORD: {
                                   parseAuthor2(td, AuthorRole.AFTERWORD, book);
                                   break;
                               }
                               case LABEL_INTRODUCTION: {
                                   parseAuthor2(td, AuthorRole.INTRODUCTION, book);
                                   break;
                               }
                               case LABEL_ILLUSTRATOR: {
                                   parseAuthor2(td, AuthorRole.ARTIST, book);
                                   break;
                               }
                               case LABEL_EDITOR: {
                                   parseAuthor2(td, AuthorRole.EDITOR, book);
                                   break;
                               }
                               case LABEL_NARRATOR:
                               case LABEL_NARRATORS: {
                                   parseAuthor2(td, AuthorRole.NARRATOR, book);
                                   break;
                               }
                               case LABEL_TRANSLATOR:
                               case LABEL_TRANSLATORS: {
                                   parseAuthor2(td, AuthorRole.TRANSLATOR, book);
                                   break;
                               }

                               case LABEL_SUBTITLE: {
                                   parseSubtitle(td, SPAN_DATA_IPUB_SEARCH_T, book);
                                   break;
                               }
                               case LABEL_PUBLISHER:
                               case LABEL_PUBLISHERS: {
                                   parsePublishers(td, book);
                                   break;
                               }
                               // label: Serie wydawnicze
                               // The publication series... and it's a mess.
                               // Some of the entries are genuine, but others are totally
                               // generic (e.g. 'Audiobook'). Ignore for now...

                               case LABEL_TAGS: {
                                   parseTags(td, "span", book);
                                   break;
                               }
                           }
                       }
                   });
    }

    private void parseAuthor2(@NonNull final Element td,
                              @AuthorRole.Role final int type,
                              @NonNull final Book book) {
        td.select(SPAN_DATA_IPUB_SEARCH_O)
          .stream()
          .map(Element::text)
          .forEach(text -> parseAuthorText(text, type, book));
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
                                         @NonNull final Element document,
                                         @Nullable final String bookId,
                                         @SuppressWarnings("SameParameterValue")
                                         @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        final Element coversDiv = document.selectFirst("div[data-ipub-contains^=covers]");
        if (coversDiv == null) {
            return Optional.empty();
        }

        // There can be many covers and even back-covers; but it's not
        // possible to distinguish them properly.
        // Grab the first cover and hope for the best
        final Element img = coversDiv.selectFirst("img");
        if (img == null) {
            return Optional.empty();
        }
        // example data-ipub-src: "//dziupla.sowa.pl/f/f9ezb_995m9y1.jpg?imwh=131x200"
        // (don't use 'src'... it seems to be dynamic)
        // we need to cut off the sizing and add the protocol
        String url = img.attr("data-ipub-src").split("\\?")[0];
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        return saveImage(context, url, null, bookId, cIdx, null);
    }
}
