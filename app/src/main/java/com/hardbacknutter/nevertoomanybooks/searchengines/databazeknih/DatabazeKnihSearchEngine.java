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

package com.hardbacknutter.nevertoomanybooks.searchengines.databazeknih;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolver;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverFactory;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinatorCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.util.logger.LoggerFactory;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * Catalog site serving Czech and Slovak mainly.
 */
public class DatabazeKnihSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByText,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByBarcode {

    public static final String SITE_URL = "https://www.databazeknih.cz";
    public static final String BOOK_URL = "https://www.databazeknih.cz/prehled-knihy/x-%s";
    public static final String AUTHOR_URL = "https://www.databazeknih.cz/autori/x-%s";

    private static final String TAG = "DatabazeKnihSearchEngin";

    /**
     * Search by isbn, or any other set of keywords.
     * Param 1: url encoded isbn/keywords
     */
    private static final String SEARCH = "/search?in=books&q=%1$s";
    /**
     * Search by sid.
     * Param 1: sid
     */
    private static final String BY_SID = "/prehled-knihy/x-%1$s";
    /**
     * Fetch a lazy-loading subsection of the book page.
     * Param 1: sid
     */
    private static final String MORE_DETAILS_URL = "/book-detail-more-info/%1$s";

    /** The html title attribute starts with this if we get a list back. */
    private static final String MULTI_RESULT_PAGE_TITLE = "Vyhledávání";
    /** a link txt we need to remove from the description field. */
    private static final String CELY_TEXT = "... celý text";
    /** website shows this if the book has no description. */
    private static final String NO_DESCRIPTION_TEXT = "Popis knihy zde zatím bohužel není...";
    /** website indication it's an eBook. */
    private static final String EBOOK = "ekniha";
    /** website indication it's an eBook. */
    private static final String AUDIOBOOK = "audiokniha";
    /** website indication it's a normal/paper book. */
    private static final String CLASSIC_BOOK = "klasická kniha";

    /**
     * The site uses non-standard language names.
     *
     * @see #mapLanguage(String)
     */
    private static final Map<String, String> LANG_MAPPING = Map.of(
            "český", "ces",
            "slovenský", "slo",
            "německý", "deu",
            "polský", "pol",
            "anglický", "eng",
            "francouzský", "fre",
            "španělský", "spa",
            "italský", "ita",
            // literally "other", we remove it
            "jiný", ""
    );

    @NonNull
    private final RatingParser ratingParser;
    private final DateParser<PartialDate> partialDateParser = new PartialDateParser();

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
    public DatabazeKnihSearchEngine(@NonNull final Context appContext,
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
        return new EngineId.Builder("databazeknih",
                                    R.string.site_databazeknih_cz,
                                    List.of(R.string.site_description_czech,
                                            R.string.site_description_catalog),
                                    "https://www.databazeknih.cz",
                                    new Locale("cs", "CZ"))
                .setIdentifierKey(Identifier.SID_DATABAZE_KNIH)
                .setPreferenceFragmentClazz(DatabazeKnihPreferencesFragment.class);
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final Book book = new Book();

        final String url = getHostUrl(context) + String.format(BY_SID, externalId);
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

        return search(context, validIsbn, fetchCovers);
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final SearchCoordinatorCriteria criteria,
                       @Nullable final String code,
                       @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concat(" ");
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

        final String url = getHostUrl(context) + String.format(SEARCH, queryParams);
        final Document document = loadDocument(context, url, null);

        if (!isCancelled()) {
            if (isMultiResult(document)) {
                parseMultiResult(context, document, fetchCovers, book);
            } else {
                parse(context, document, fetchCovers, book);
            }
        }
        return book;
    }

    @VisibleForTesting
    void parseMultiResult(@NonNull final Context context,
                          @NonNull final Document document,
                          @NonNull final boolean[] fetchCovers,
                          @NonNull final Book book)
            throws SearchException, CredentialsException, StorageException {

        Element element = document.selectFirst("p.new");
        if (element != null) {
            element = element.selectFirst("a.new");
            if (element != null) {
                String url = element.attr("href");
                if (!url.isEmpty()) {
                    // url is relative, add the host
                    url = getHostUrl(context) + url;
                    final Document redirected = loadDocument(context, url, null);
                    // sanity check
                    if (!isMultiResult(redirected)) {
                        parse(context, redirected, fetchCovers, book);
                    }
                }
            }
        }
    }

    private boolean isMultiResult(@NonNull final Document document) {
        return document.title().startsWith(MULTI_RESULT_PAGE_TITLE);
    }

    /**
     * Parses the downloaded {@link Document}.
     * We only parse the <strong>first book</strong> found.
     *
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set to {@code true} if we want to get covers
     *                    The array is guaranteed to have at least one element.
     * @param book        Bundle to update
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

        final List<AuthorResolver> authorResolvers = AuthorResolverFactory
                .getResolvers(context, this);
        // id and title
        @Nullable
        final String sid = parseMetaTags(document, book);

        final Elements itemProps = document.select("[itemprop]");
        for (final Element itemProp : itemProps) {
            final String prop = itemProp.attr("itemprop");
            switch (prop) {
                case "author": {
                    parseAuthors(context, itemProp, Author.TYPE_WRITER, authorResolvers, book);
                    break;
                }
                case "description": {
                    parseDescription(itemProp, book);
                    break;
                }
                case "ratingValue": {
                    ratingParser.parse(itemProp.text())
                                .ifPresent(rating -> book.putFloat(DBKey.RATING, rating));
                    break;
                }
                case "genre": {
                    final List<Tag> tags = itemProp
                            .select("a.genre")
                            .stream().map(Element::text)
                            .map(Tag::new)
                            .collect(Collectors.toList());
                    if (!tags.isEmpty()) {
                        book.setTags(tags);
                    }
                    break;
                }
                case "datePublished": {
                    final String text = itemProp.text();
                    if (!text.isEmpty() && !"?".equals(text)) {
                        partialDateParser.parse(text).ifPresent(book::setPublicationDate);
                    }
                    break;
                }
                case "publisher": {
                    final String text = itemProp.text();
                    if (!text.isEmpty()) {
                        book.add(Publisher.from(text));
                    }
                    break;
                }
                default:
                    // prop=name => the title
                    // prop=image
                    // prop=aggregateRating
                    // rop=ratingCount
                    // prop=reviewCount
                    //Log.d(TAG, "prop=" + prop);
                    break;
            }
        }

        Element element;
        element = document.selectFirst("h3 > a[href^=/serie/]");
        if (element != null) {
            final String seriesName = SearchEngineUtils.cleanName(element.text());
            if (!seriesName.isEmpty()) {
                // some series will parse wrong.
                // Example "Lucky Luke (Crew)"
                // The "Crew" part is actually the publisher, but we parse it as the number.
                // Not much we can do about that, as we need to rely on "()" parsing
                // for many sites to BE the number.
                final Series series = Series.from(seriesName);
                element = document.selectFirst(
                        "span.nowrap > span.odright_pet, span.nowrap > span.odleft_pet ");
                if (element != null) {
                    String nr = element.text();
                    if (!nr.isEmpty()) {
                        // these usually/always end with ". díl" == "episode"; remove
                        if (nr.endsWith(". díl")) {
                            nr = nr.substring(0, nr.length() - 5);
                        }
                        series.setNumber(nr);
                    }
                }
                book.add(series);
            }
        }

        element = document.selectFirst("span:contains(Originální název:)");
        if (element != null) {
            element = element.nextElementSibling();
            if (element != null) {
                // <h4>The Case of the Left-Handed Lady<span class="gray">,</span> 2007</h4>
                if ("h4".equals(element.tag().getName())) {
                    if (element.childNodeSize() > 0) {
                        final String text = SearchEngineUtils.cleanName(
                                element.childNode(0).toString());
                        if (!text.isEmpty()) {
                            book.putString(DBKey.TRANSLATION_ORIGINAL_TITLE, text);
                        }
                    }
                    if (element.childNodeSize() > 2) {
                        final String text = SearchEngineUtils.cleanText(
                                element.childNode(2).toString());
                        partialDateParser.parse(text).ifPresent(
                                book::setFirstPublicationDate);
                    }
                }
            }
        }

        element = document.selectFirst("span:contains(Interpreti:)");
        if (element != null) {
            element = element.nextElementSibling();
            if (element != null) {
                final String url = element.attr("href");
                if (!url.isEmpty()) {
                    parseAuthor(context, element, element.text(), Author.TYPE_NARRATOR,
                                authorResolvers, book);
                }
            }
        }

        // in addition to the "genre" tags parsed above
        book.addTags(document.select("a.tag").stream()
                             .map(Element::text)
                             .map(Tag::new)
                             .collect(Collectors.toList()));

        // Sanity check
        if (sid != null && !sid.isEmpty()) {
            // fetch the "more details" and parse
            final String url = getHostUrl(context) + String.format(MORE_DETAILS_URL, sid);
            final Document d2 = loadDocument(context, url, null);
            parseAdditional(context, d2, authorResolvers, book);
        }

        // Check if there is TOC: there will be a link on the lower menu bar.
        final Element linksElement = document.selectFirst("ul#newIcons");
        if (linksElement != null) {
            final Element a = linksElement.selectFirst("a[href^=/povidky-z-knihy/]");
            if (a != null) {
                String url = a.attr("href");
                if (!url.isEmpty()) {
                    // url is relative, add the host
                    url = getHostUrl(context) + url;
                    final Document d2 = loadDocument(context, url, null);
                    parseToc(context, d2, book);
                }
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = book.getString(DBKey.ISBN);
            parseCover(context, document, isbn, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void parseDescription(@NonNull final Element itemProp,
                                  @NonNull final Book book) {
        final Element desc = itemProp.nextElementSibling();
        if (desc != null) {
            String text = desc.wholeText().strip();
            // Check/skip if it is "no description"
            if (!NO_DESCRIPTION_TEXT.equals(text)) {
                // text contains \n and lots of whitespace, cleanup
                text = Arrays.stream(text.split("\n"))
                             .map(String::strip)
                             // remove the "click to see more" if present
                             .filter(t -> !CELY_TEXT.equals(t))
                             .collect(Collectors.joining("\n"))
                             // empty lines at end
                             .stripTrailing();

                // depending on the formatting it might still have the "click" text
                if (text.endsWith(CELY_TEXT)) {
                    text = text.substring(0, text.length() - 13);
                }
                book.setDescription(text);
            }
        }
    }

    /**
     * Note that authors in the section of the page do NOT use the "(p)" suffix
     * to indicate pseudonyms.
     * Instead we find e.g. " Maurice De Bévère - Morris".
     * TODO/TEST: check if we should/could split on "-" for "author - pseudonym"
     *  but how reliable is this? Need more examples.
     *  https://www.databazeknih.cz/prehled-knihy/lucky-luke-crew-jak-se-daltonovi-polepsili-555445
     *
     * @param context         current Context
     * @param root            to parse
     * @param authorResolvers to use
     * @param book            to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    private void parseAdditional(@NonNull final Context context,
                                 @NonNull final Document root,
                                 @NonNull final List<AuthorResolver> authorResolvers,
                                 @NonNull final Book book)
            throws SearchException, CredentialsException {
        Element element;

        element = root.selectFirst("[itemprop='numberOfPages']");
        if (element != null) {
            final String text = element.text();
            if (!text.isEmpty()) {
                book.putString(DBKey.PAGES, text);
            }
        }

        element = root.selectFirst("[itemprop='language']");
        if (element != null) {
            final String text = element.text();
            if (!text.isEmpty()) {
                book.putString(DBKey.LANGUAGE, mapLanguage(text));
            }
        }

        // there can be more than one isbn. First one "wins"
        if (book.getIsbn().isEmpty()) {
            element = root.selectFirst("[itemprop='isbn']");
            if (element != null) {
                final String text = ISBN.cleanText(element.text());
                if (!text.isEmpty()) {
                    book.setIsbn(text);
                }
            }
        }

        element = root.selectFirst("[itemprop='ilustrator']");
        if (element != null) {
            parseAuthors(context, element, Author.TYPE_ARTIST, authorResolvers, book);
        }
        element = root.selectFirst("[itemprop='cover']");
        if (element != null) {
            parseAuthors(context, element, Author.TYPE_COVER_ARTIST, authorResolvers, book);
        }
        element = root.selectFirst("[itemprop='translator']");
        if (element != null) {
            parseAuthors(context, element, Author.TYPE_TRANSLATOR, authorResolvers, book);
        }

        // Audio books duration
        if (book.getString(DBKey.PAGES).isEmpty()) {
            element = root.selectFirst("span:contains(Délka:)");
            if (element != null) {
                final Node textNode = element.nextSibling();
                if (textNode != null) {
                    final String text = textNode.toString().trim();
                    if (!text.isEmpty()) {
                        book.putString(DBKey.PAGES, text);
                    }
                }
            }
        }

        // for translations, we keep that date if already set.
        if (!book.getFirstPublicationDate().isPresent()) {
            // 1. vydání originálu:
            element = root.selectFirst("span:contains(1. vydání originálu:)");
            if (element != null) {
                final Node textNode = element.nextSibling();
                if (textNode != null) {
                    final String text = textNode.toString().trim();
                    if (!text.isEmpty()) {
                        partialDateParser.parse(text).ifPresent(book::setFirstPublicationDate);
                    }
                }
            }
        }

        // Vazba knihy == binding
        element = root.selectFirst("span:contains(Vazba knihy:)");
        if (element != null) {
            final Node textNode = element.nextSibling();
            if (textNode != null) {
                final String text = textNode.toString().trim();
                if (!text.isEmpty()) {
                    book.putString(DBKey.FORMAT, text);
                }
            }
        }

        // Náklad == circulation
        element = root.selectFirst("span:contains(Náklad:)");
        if (element != null) {
            final Node textNode = element.nextSibling();
            if (textNode != null) {
                final String text = textNode.toString().trim();
                if (!text.isEmpty()) {
                    book.putString(DBKey.PRINT_RUN, text);
                }
            }
        }

        // Forma == form
        // This field contains one of:
        // "klasická kniha" (klassiek boek)
        // "ekniha" (eBook)
        // "audiokniha" (audio-book)
        // Not seen other entries, but not looked to exhaustion...
        element = root.selectFirst("span:contains(Forma:)");
        if (element != null) {
            final Node textNode = element.nextSibling();
            if (textNode != null) {
                final String text = textNode.toString().trim();
                if (!text.isEmpty()) {
                    if (EBOOK.equals(text)) {
                        book.putString(DBKey.FORMAT, EBOOK);
                    } else if (AUDIOBOOK.equals(text)) {
                        book.putString(DBKey.FORMAT, AUDIOBOOK);
                    } else if (!CLASSIC_BOOK.equals(text)) {
                        LoggerFactory.getLogger().w(TAG, "found Forma: " + text);
                    }
                }
            }
        }
    }

    @NonNull
    private String mapLanguage(@NonNull final String s) {
        return Objects.requireNonNullElse(LANG_MAPPING.get(s), s);
    }

    @VisibleForTesting
    void parseToc(@NonNull final Context context,
                  @NonNull final Document root,
                  @NonNull final Book book) {

        final Element element = root.selectFirst("table.new.odtop_big");
        if (element != null) {
            final Elements tds = element.select("td");

            Author primaryAuthor = book.getPrimaryAuthor();
            if (primaryAuthor == null) {
                primaryAuthor = Author.createUnknownAuthor(context);
            }

            final List<TocEntry> toc = new ArrayList<>();
            for (final Element td : tds) {
                final Element a = td.selectFirst("a");
                if (a != null) {
                    // ENHANCE: if we follow the link, we can get the actual author
                    //  and original lang. title... but we don't support the latter yet
                    // https://www.databazeknih.cz/povidky-z-knihy/fantasy-a-science-fiction-1994-04-87630
                    final String title = a.text();
                    if (!title.isEmpty()) {
                        final TocEntry tocEntry = new TocEntry(primaryAuthor, title);
                        final Element year = a.nextElementSibling();
                        if (year != null) {
                            partialDateParser.parse(year.text()).ifPresent(
                                    tocEntry::setFirstPublicationDate);
                        }
                        toc.add(tocEntry);
                    }
                }
            }
            if (!toc.isEmpty()) {
                book.setToc(toc);
            }
        }
    }

    /**
     * Parse all "a" links in the given element for Authors.
     *
     * @param context         current Context
     * @param element         to parse
     * @param type            of author
     * @param authorResolvers to use
     * @param book            to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    private void parseAuthors(@NonNull final Context context,
                              @NonNull final Element element,
                              @Author.Type final int type,
                              @NonNull final List<AuthorResolver> authorResolvers,
                              @NonNull final Book book)
            throws SearchException, CredentialsException {
        for (final Element a : element.select("a")) {
            final String text = a.text();
            if (!text.isEmpty()) {
                parseAuthor(context, a, text, type, authorResolvers, book);
            }
        }
    }

    /**
     * Parse the given link/text for an Author.
     *
     * @param context         current Context
     * @param a               to parse
     * @param text            author name
     * @param type            of author
     * @param authorResolvers to use
     * @param book            to update
     *
     * @throws CredentialsException on authentication/login failures
     * @throws SearchException      on generic exceptions (wrapped) during search
     */
    private void parseAuthor(@NonNull final Context context,
                             @NonNull final Element a,
                             @NonNull final String text,
                             @Author.Type final int type,
                             @NonNull final List<AuthorResolver> authorResolvers,
                             @NonNull final Book book)
            throws SearchException, CredentialsException {
        final Author author = Author.from(text);

        final String url = a.attr("href");
        final int index = url.lastIndexOf('-');
        if (index > 0 && (index + 1) < url.length()) {
            final String id = url.substring(index + 1);
            if (!id.isEmpty()) {
                author.setIdentifierValue(Identifier.SID_DATABAZE_KNIH, id);
            }
        }

        // an adjacent "(p)" indicates this is a Pseudonym.
        final Element maybePseudonym = a.nextElementSibling();
        if (maybePseudonym != null
            && "span".equals(maybePseudonym.tag().getName())
            && "(p)".equals(maybePseudonym.text())) {
            for (final AuthorResolver resolver : authorResolvers) {
                resolver.resolve(context, author);
            }
        }

        addAuthor(author, type, book);
    }

    @Nullable
    private String parseMetaTags(@NonNull final Document document,
                                 @NonNull final Book book) {
        String id = null;

        final Elements metaElements = document.head().select("meta");
        for (final Element meta : metaElements) {
            final String property = meta.attr("property");
            final String content = meta.attr("content");
            switch (property) {
                case "og:title": {
                    book.setTitle(content);
                    break;
                }
                case "og:url": {
                    // https://www.databazeknih.cz/prehled-knihy/pripad-levoruke-damy-546691
                    final int index = content.lastIndexOf('-');
                    if (index > 0 && (index + 1) < content.length()) {
                        id = content.substring(index + 1);
                        book.setIdentifierValue(Identifier.SID_DATABAZE_KNIH, id);
                    }
                    break;
                }
                // case "og:image": this is a small thumbnail only
            }
        }

        return id;
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
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                        @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        String url = null;
        final Element img = document.selectFirst("img.kniha_img, img.kniha_img_audiobook");
        if (img != null) {
            url = img.attr("src");
        }
        if (url == null || url.contains("empty_bmid.jpg")) {
            return Optional.empty();
        }

        return saveImage(context, url, null, bookId, cIdx, null);
    }
}
