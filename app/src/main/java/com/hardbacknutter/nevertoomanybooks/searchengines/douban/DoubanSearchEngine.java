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

package com.hardbacknutter.nevertoomanybooks.searchengines.douban;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RatingParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.ImageWebSize;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEdition;
import com.hardbacknutter.nevertoomanybooks.searchengines.AltEditionIsbn;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONObject;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

/**
 * Chinese language books & comics.
 * <p>
 * It seems that Chinese publishers reuse ISBNs for different editions of the same book.
 * This sort-of violates the intention of an ISBN:
 * - reuse for different print-runs is OK
 * - reuse for different edition is normally a big NO.
 * We try to work around this limitation.
 */
public class DoubanSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByText,
                   SearchEngine.CoverByEdition,
                   SearchEngine.AlternativeEditions<AltEditionDouban> {

    /** Main site, but NOT the search site. */
    private static final String SITE_URL = "https://book.douban.com";
    private static final String BOOK_URL = "https://book.douban.com/subject/%s";
    private static final String AUTHOR_URL = "https://www.douban.com/personage/%s";

    private static final Locale SITE_LOCALE = Locale.CHINA;

    private static final String PREFERENCE_KEY = "douban";

    /**
     * Preference key: whether to select the most-recent book {@code true}
     * or the first one found {@code false} from the multi-result list.
     * <p>
     * Type: {@code boolean}
     */
    public static final String PK_FETCH_MOST_RECENT =
            PREFERENCE_KEY + ".search.result.order.by.date";

    /**
     * param 1: the ISBN.
     */
    private static final String SEARCH_URL = "/book/subject_search?search_text=%1$s";

    /**
     * Support for foreign author names.
     * Format: [法] 保罗·霍尔特   ==>  [France] Paul Holt
     */
    private static final Pattern PATTERN_FOREIGN_AUTHOR = Pattern.compile("\\[(.+)] (.+)");

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
    public DoubanSearchEngine(@NonNull final Context appContext,
                              @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        ratingParser = new RatingParser(10);
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
                                    R.string.site_douban,
                                    List.of(R.string.site_description_chinese,
                                            R.string.site_description_catalog),
                                    "https://search.douban.com",
                                    SITE_LOCALE)
                .setIdentifierKey(Identifier.SID_DOUBAN)
                .setPreferenceFragmentClazz(DoubanPreferencesFragment.class);
    }

    @NonNull
    public static Collection<Identifier> createIdentifiers(@NonNull final Context context) {
        final String name = EngineId.Douban.getName(context);
        return Set.of(
                Identifier.createBook(
                        Identifier.SID_DOUBAN,
                        Identifier.Type.Number,
                        name,
                        SITE_URL,
                        BOOK_URL),
                Identifier.createAuthor(
                        Identifier.SID_DOUBAN,
                        Identifier.Type.Number,
                        name,
                        SITE_URL,
                        AUTHOR_URL,
                        "P6441")
        );
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {

        final Book book = new Book();

        final String url = getHostUrl() + String.format(SEARCH_URL, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            // it's ALWAYS multi-result, even if only one result is returned.
            parseMultiResult(context, document, fetchCovers, book);
        }
        return book;
    }

    /**
     * Criteria supported: ALL.
     * Code: supported.
     * <p>
     * {@inheritDoc}
     */
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

        final Book book = new Book();

        // Sanity check
        if (words.length() == 0) {
            return book;
        }

        final String url = getHostUrl() + String.format(SEARCH_URL, words);
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
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws SearchException, CredentialsException, StorageException {
        final Optional<String> oUrl = extractBookUrl(document);
        if (oUrl.isPresent()) {
            final Document redirected = loadDocument(context, oUrl.get(), null);
            if (!isCancelled()) {
                parse(context, redirected, fetchCovers, book);
            }
        } else {
            // Keep this as a fallback, but we're unlikely to ever get here.
            parse(context, document, fetchCovers, book);
        }
    }


    /**
     * Parse the given Document for the embedded JavaScript element containing
     * the list of books found and extract the best suited book (url).
     *
     * @param document to parse
     *
     * @return url for the book details page
     */
    @VisibleForTesting
    @NonNull
    public Optional<String> extractBookUrl(@NonNull final Document document) {
        final Optional<JSONArray> oItems = extractItemList(document);
        if (oItems.isPresent()) {
            final JSONArray items = oItems.get();

            final JSONObject reference;
            // Depending on user setting:
            if (useMostRecentResult()) {
                reference = findMostRecent(items);
            } else {
                // Use the first one found
                reference = items.getJSONObject(0);
            }

            final String url = reference.optString("url", null);
            if (url != null) {
                return Optional.of(url);
            }
        }
        return Optional.empty();
    }

    /**
     * Parse the given Document for the embedded JavaScript element containing
     * the list of books found.
     *
     * <pre>
     * {
     *   "count": 15,
     *   "error_info": "",
     *   "items": [
     *     {
     *        "abstract": "",
     *        "abstract_2": "",
     *        "cover_url": "https://img1.doubanio.com/cuphead/book-static/pics/book-default-lpic.gif",
     *        "extra_actions": [],
     *        "id": 25930607,
     *        "interest": null,
     *        "label_actions": [],
     *        "labels": [],
     *        "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',
     *                                       subject_id:'25930607',query:'9787532190294',
     *                                       i:'0',cat_id:'1001'})\"",
     *        "rating": {
     *            "count": 0,
     *            "rating_info": "目前无人评价",
     *            "star_count": 0,
     *            "value": 0
     *        },
     *        "title": "9787539190594",
     *        "topics": [],
     *        "tpl_name": "search_subject",
     *        "url": "https://book.douban.com/subject/25930607/"
     *     },
     *     {
     *        "abstract": "[英] 菲利普·高夫 / 傅星源 / 上海文艺出版社 / 2024-6 / 58",
     *        "abstract_2": "",
     *        "cover_url": "https://img1.doubanio.com/view/subject/m/public/s34875559.jpg",
     *        "extra_actions": [],
     *        "id": 36897178,
     *        "interest": null,
     *        "label_actions": [],
     *        "labels": [],
     *        "more_url": "onclick=\"moreurl(this,{from:'book_subject_search',
     *                                       subject_id:'36897178',query:'9787532190294',
     *                                       i:'1',cat_id:'1001'})\"",
     *        "rating": {
     *            "count": 0,
     *            "rating_info": "评价人数不足",
     *            "star_count": 0,
     *            "value": 0
     *        },
     *        "title": "伽利略的错误 : 为一种新的意识科学奠基",
     *        "topics": [],
     *        "tpl_name": "search_subject",
     *        "url": "https://book.douban.com/subject/36897178/"
     *     }
     *   ],
     *   "report": {
     *     "qtype": "195",
     *     "tags": "读书"
     *   },
     *   "start": 0,
     *   "text": "9787532190294",
     *   "total": 2
     * }
     * </pre>
     *
     * @param document to parse
     *
     * @return the item list; when found, the array is guaranteed to contain at least one item.
     */
    @NonNull
    private Optional<JSONArray> extractItemList(@NonNull final Document document) {
        final Elements elements = document.select("script[type=\"text/javascript\"]");
        for (final Element element : elements) {
            final String s = element.html().strip();
            if (s.startsWith("window.__DATA__ =")) {
                // Grab the part after the first equal sign and parse as a JSON string.
                final String[] sa = s.split("=", 2);
                if (sa.length > 1) {
                    JSONArray items = new JSONObject(sa[1]).optJSONArray("items");
                    if (items != null && !items.isEmpty()) {
                        // Remove any invalid entries
                        items = filter(items);
                        if (!items.isEmpty()) {
                            return Optional.of(items);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Find the most recent book in the given array by assuming
     * that the highest numerical id is the latest added to the site.
     *
     * @param items to parse
     *
     * @return item found
     */
    @NonNull
    private JSONObject findMostRecent(@NonNull final JSONArray items) {
        JSONObject result = null;
        int highestId = 0;
        for (int i = 0; i < items.length(); i++) {
            final JSONObject item = items.getJSONObject(i);
            final int id = item.optInt("id");
            if (id > highestId) {
                highestId = id;
                result = item;
            }
        }

        // Paranoia...
        if (result == null) {
            // Use the first one found
            result = items.getJSONObject(0);
        }
        return result;
    }

    /**
     * Filter/remove any 'empty' entries by copying the valid ones to a new array.
     *
     * @param items to filter
     *
     * @return the filtered array
     */
    @NonNull
    private JSONArray filter(@NonNull final JSONArray items) {
        final JSONArray result = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            final JSONObject item = items.getJSONObject(i);
            if (isProbablyValid(item)) {
                result.put(item);
            }
        }
        return result;
    }

    private boolean isProbablyValid(@Nullable final JSONObject item) {
        if (item == null) {
            return false;
        }
        final String title = item.optString("title", null);
        if (title == null) {
            return false;
        }
        // 'empty' entries seem to have a title containing just an isbn number
        for (int i = 0; i < title.length(); i++) {
            // As soon as we find a non-digit, assume it's a valid title
            if (!Character.isDigit(title.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does the user prefer to always use the most recent book from the site.
     * Or do they prefer to just grab the first one found?
     *
     * @return {@code true} if the most recent book is preferred,
     *         {@code false} to grab the first one found
     */
    private boolean useMostRecentResult() {
        return ServiceLocator.getInstance().getSharedPreferences()
                             .getBoolean(PK_FETCH_MOST_RECENT, true);
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
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        parseMetaTags(document, book);

        final Element infoTable = document.selectFirst("div#info");
        if (infoTable == null) {
            return;
        }

        final Elements labels = infoTable.select("span.pl");
        for (final Element label : labels) {
            // labels include the ':' except the author, where the ':' is a sibling text element
            switch (label.text().strip()) {
                case "作者": {
                    // Author
                    final Element a = label.nextElementSibling();
                    // make sure that sibling IS an "a"
                    if (a != null && "a".equals(a.tagName())) {
                        String text = a.text();
                        final Matcher matcher = PATTERN_FOREIGN_AUTHOR.matcher(text);
                        if (matcher.find()) {
                            // [法] 保罗·霍尔特   ==>  [France] Paul Holt
                            // Move the country prefix to the end to allow
                            // sorting on author names to work.
                            text = matcher.group(2) + " [" + matcher.group(1) + "]";
                        }
                        text = cleanName(text);
                        if (!text.isBlank()) {
                            final Author author = Author.from(text);

                            // ENHANCE: we parsed the author link as per below to get the SID
                            //  Example: https://book.douban.com/author/322717
                            //  when followed, this redirects to
                            //  https://www.douban.com/personage/30081700/
                            //  so the SID from the author link is WRONG...
                            //  We'll need to follow the link to the "personage" to
                            //  get the correct SID.
                            //  This would also allow us to get Birthdate etc

                            addAuthor(author, AuthorRole.UNKNOWN, book);
                        }
                    }
                    break;
                }
                case "出版社:": {
                    // Publisher
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        final String text = cleanName(a);
                        if (!text.isBlank()) {
                            book.add(Publisher.from(text));
                        }
                    }
                    break;
                }
                case "出品方:": {
                    // Producer (printer?). Ignored for now.
                    break;
                }
                case "副标题:": {
                    // Subtitle. Ignored for now.
                    break;
                }
                case "原作名:": {
                    // Original title
                    final Node n = label.nextSibling();
                    if (n != null) {
                        book.setTranslatedFromTitle(n.toString().strip());
                    }
                    break;
                }
                case "译者": {
                    // Translator
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        final String s = cleanName(a);
                        if (!s.isBlank()) {
                            addAuthor(Author.from(s), AuthorRole.TRANSLATOR, book);
                        }
                    }
                    break;
                }
                case "出版年:": {
                    // Year of publication
                    final Node n = label.nextSibling();
                    if (n != null) {
                        // Dates are listed as yyyy-MM;
                        // use a PartialDate parser ignoring the locale
                        final String dateStr = n.toString().strip();
                        partialDateParser.parse(dateStr).ifPresent(book::setPublicationDate);
                    }
                    break;
                }
                case "页数:": {
                    // Pages
                    final Node n = label.nextSibling();
                    if (n != null) {
                        book.setPages(n.toString().strip());
                    }
                    break;
                }
                case "定价:": {
                    // List price
                    final Node n = label.nextSibling();
                    if (n != null) {
                        final String priceStr = n.toString().strip();
                        parsePrice(context, priceStr, book);
                    }
                    break;
                }
                case "装帧:": {
                    // Format
                    final Node n = label.nextSibling();
                    if (n != null) {
                        book.setFormat(n.toString().strip());
                    }
                    break;
                }
                case "丛书:": {
                    // Series
                    final Element a = label.nextElementSibling();
                    if (a != null && "a".equals(a.tagName())) {
                        final String s = cleanName(a);
                        if (!s.isBlank()) {
                            book.add(Series.from(s));
                        }
                    }
                    break;
                }
            }
        }

        final Element ratingElement = document.selectFirst("div.rating_self > strong.rating_num ");
        if (ratingElement != null) {
            ratingParser.parse(ratingElement.text()).ifPresent(book::setRating);
        }

        parseDescription(document, book);

        // The content table - in the example we used, it's the chapter list.
        // TODO: check if there is a way of detecting chapter-list versus actual content-list
        // final Optional<String> sid = book.getIdentifierValue(Identifier.SID_DOUBAN);
        // if (sid.isPresent()) {
        //     final Element tocElement = document.selectFirst("div#dir_" + sid.get() + "_full");
        //     if (tocElement != null) {
        //         final String[] content = PATTERN_BR.split(tocElement.html());
        //         ... numbered lines with chapter-titles
        //     }
        // }

        // There is no language listed, we're assuming Simplified Chinese
        if (!book.contains(DBKey.LANGUAGE)) {
            book.setLanguage("zho");
        }

        if (fetchCovers[0]) {
            parseCover(context, document, book.getIsbn(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void parsePrice(@NonNull final Context context,
                            @NonNull final String priceStr,
                            @NonNull final Book book) {

        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(SITE_LOCALE, userLocales);
        final MoneyParser parser = new MoneyParser(SITE_LOCALE, allLocales);
        addPriceListed(context, parser, priceStr, MoneyParser.CNY, book);
    }

    /**
     * Some of the data is available in the page meta-data.
     *
     * <pre>{@code
     *     <meta property="og:title" content="三体" />
     *     <meta property="og:description" content="军方探寻外星文明的绝秘计划“..." />
     *     <meta property="og:site_name" content="豆瓣" />
     *     <meta property="og:url" content="https://book.douban.com/subject/36874304/" />
     *     <meta property="og:image"
     *           content="https://img1.doubanio.com/view/subject/l/public/s34850048.jpg" />
     *     <meta property="og:type" content="book" />
     *     <meta property="book:author" content="刘慈欣" />
     *     <meta property="book:isbn" content="9787536692930" />
     *     }
     * </pre>
     *
     * @param document to parse
     * @param book     Bundle to update
     */
    private void parseMetaTags(@NonNull final Document document,
                               @NonNull final Book book) {
        final Elements metaElements = document.head().select("meta");
        for (final Element meta : metaElements) {
            final String property = meta.attr("property");
            final String content = meta.attr("content");
            // There is also "og:image" with a cover url.
            // These can be VERY large and lead to java.net.SocketTimeoutException
            // We'll grab the thumbnail instead.
            switch (property) {
                case "og:title":
                    book.setTitle(content);
                    break;

                case "book:isbn":
                    book.setIsbn(content);
                    break;

                case "og:description":
                    // The description in the meta element is shortened.
                    // We copy it while we have it, but will overwrite when we
                    // can (should) get the full description later on.
                    book.setDescription(content);
                    break;

                case "og:url": {
                    // content="https://book.douban.com/subject/36874304/"
                    final String[] parts = content.split("/");
                    // Sanity check
                    if (parts.length >= 5) {
                        try {
                            // Paranoia: parse to make sure it's a number
                            final int sid = Integer.parseInt(parts[4]);
                            book.setIdentifierValue(Identifier.SID_DOUBAN, sid);
                        } catch (@NonNull final NumberFormatException ignore) {
                            // ignore
                        }
                    }
                    break;
                }
            }
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {
        // The meta element was shortened, overwrite if we find the full description
        final Element relInfo = document.selectFirst("div.related_info");
        if (relInfo != null) {
            // Then can be multiple "intro" blocks, as this is used for description, author, ...
            // We normally grab the first with the description only,
            // but check for an "a" element with JavaScript to "Expand".
            // If found, this means the text was very long, and was partially hidden.
            // In that case we grad the 2nd "intro" block which is the full description.
            final Elements introElements = relInfo.select("div.intro");
            if (!introElements.isEmpty()) {
                Element intro = introElements.get(0);
                if (intro.selectFirst("a.a_show_full") != null
                    && introElements.size() > 1) {
                    intro = introElements.get(1);
                }
                final String desc = cleanText(intro.html());
                if (!desc.isBlank()) {
                    book.setDescription(desc);
                }
            }
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
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Document document,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                            @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {
        // "div#mainpic > a" element will have as the href a large version of the image.
        // "div#mainpic > a > img" will have "src" point to a thumbnail
        // We found the large image to result in socket-timeouts
        // (without modifying our default timeout)
        // Choosing to get the thumbnail here:
        final Element img = document.selectFirst("div#mainpic > a > img");
        if (img == null) {
            return Optional.empty();
        }
        final String src = img.attr("src");
        if (src.isEmpty()) {
            return Optional.empty();
        }
        return saveImage(context, src, null, bookId, cIdx, null);
    }

    @NonNull
    @Override
    public List<AltEditionDouban> searchAlternativeEditions(@NonNull final Context context,
                                                            @NonNull final String validIsbn)
            throws SearchException, CredentialsException {

        final String url = getHostUrl() + String.format(SEARCH_URL, validIsbn);
        final Document document = loadDocument(context, url, null);
        if (!isCancelled()) {
            final Optional<JSONArray> oItems = extractItemList(document);
            if (oItems.isPresent()) {
                final JSONArray items = oItems.get();
                if (items.isEmpty()) {
                    return List.of();
                }
                final List<AltEditionDouban> editionList = new ArrayList<>();

                for (int i = 0; i < items.length(); i++) {
                    final JSONObject item = items.getJSONObject(i);

                    final long id = item.optLong("id");
                    final String bookUrl = item.optString("url");
                    final String coverUrl = item.optString("cover_url");

                    editionList.add(new AltEditionDouban(id, bookUrl, coverUrl));
                }

                return editionList;
            }
        }
        return List.of();
    }

    @NonNull
    public Optional<String> searchCoverByEdition(@NonNull final Context context,
                                                 @NonNull final AltEdition altEdition,
                                                 @IntRange(from = 0, to = 0) final int cIdx,
                                                 @Nullable final ImageWebSize size)
            throws SearchException, CredentialsException, StorageException {

        if (altEdition instanceof AltEditionDouban) {
            final AltEditionDouban edition = (AltEditionDouban) altEdition;

            final String bookUrl = edition.getBookUrl();
            if (bookUrl != null && !bookUrl.isEmpty()) {
                final Document document = loadDocument(context, bookUrl, null);
                if (!isCancelled()) {
                    return parseCover(context, document, String.valueOf(edition.getId()), cIdx)
                            // let the system resolve any path variations
                            .map(fileSpec -> new File(fileSpec).getAbsolutePath());
                }
            }
        } else if (altEdition instanceof AltEditionIsbn) {
            final AltEditionIsbn edition = (AltEditionIsbn) altEdition;

            final String isbn = edition.getIsbn();
            final String url = getHostUrl() + String.format(SEARCH_URL, isbn);
            final Document document = loadDocument(context, url, null);
            if (!isCancelled()) {
                return parseCover(context, document, isbn, cIdx)
                        // let the system resolve any path variations
                        .map(fileSpec -> new File(fileSpec).getAbsolutePath());
            }
        }

        return Optional.empty();
    }
}
