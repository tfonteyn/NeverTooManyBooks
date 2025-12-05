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

package com.hardbacknutter.nevertoomanybooks.searchengines.biblionetgr;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
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

@SuppressWarnings("ALL")
public class BiblionetGrSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    public static final String SITE_URL = "https://biblionet.gr";
    public static final String BOOK_URL = null;
    public static final String AUTHOR_URL = null;

    private static final String TAG = "BiblionetGrSearchEngine";

    private static final Locale SITE_LOCALE = new Locale("el", "GR");

    private static final String PREFERENCE_KEY = "biblionetgr";

    /**
     * Subject tags on the site have a code prefixed. By default, we drop this
     * but the user can enable this in the settings.
     */
    private static final String PK_TAG_PREFIX_NUMBER =
            PREFERENCE_KEY + ".search.tag.prefix_number";

    /**
     * Encoded text is:  σύνθετη αναζήτηση = “advanced search”.
     * Append the ISBN code as-is to search.
     */
    private static final String SEARCH = "/%CF%83%CF%85%CE%BD%CE%B8%CE%B5%CF%84"
                                         + "%CE%B7-%CE%B1%CE%BD%CE%B1%CE%B6%CE%B7"
                                         + "%CF%84%CE%B7%CF%83%CE%B7?q=";
    private static final Pattern SUBJECT_BADGE_PATTERN =
            Pattern.compile("\\[.*]\\s*(.*)");
    private final DateParser<PartialDate> dateParser = new PartialDateParser();
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
    public BiblionetGrSearchEngine(@NonNull final Context appContext,
                                   @NonNull final SearchEngineConfig config) {
        super(appContext, config);

        authorResolverHelper = new AuthorResolverHelper();
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
                                    R.string.site_biblionet_gr,
                                    List.of(R.string.site_description_greek,
                                            R.string.site_description_catalog),
                                    SITE_URL,
                                    SITE_LOCALE)
                .setPreferenceFragmentClazz(BiblionetGrPreferencesFragment.class)
                .setConfig(cb -> cb
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException, CredentialsException {
        final String url = getHostUrl() + SEARCH + validIsbn;
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
        Element dataElement = document.selectFirst("div#result_books");
        if (dataElement != null) {
            dataElement = dataElement.selectFirst("a.book-title");
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

        final Elements summaryDivs = document.select("div.summary");

        // Sanity check, there should always be 2
        if (summaryDivs.size() < 2) {
            return;
        }

        if (!processTitleAndAuthors(summaryDivs.get(0), book)) {
            return;
        }

        final Elements lists = summaryDivs.get(1).select("ul");

        if (lists.isEmpty()) {
            return;
        }
        processDetails(context, lists.get(0), book);

        if (lists.size() > 1) {
            processDetails(context, lists.get(1), book);
        }

        final Element bookTabs = document.selectFirst("section#book_tabs");
        if (bookTabs != null) {
            final Element desc = bookTabs.selectFirst("div#bookDescription");
            if (desc != null) {
                // the text is a couple of elements deeper, but there is only one text element
                final String text = desc.text();
                final String description = book.getDescription();
                if (description.isBlank()) {
                    book.setDescription(text);
                } else {
                    // The "Notes" field when present is kept at the start.
                    // Example of notes:
                    //    Freely available under a Creative Commons license via
                    //    the website www.saitapublications.gr
                    book.setDescription(description + "\n\n" + text);
                }
            }
        }

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCovers(context, document, book.getIsbn(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private boolean processTitleAndAuthors(@NonNull final Element titleSection,
                                           @NonNull final Book book) {
        final Element titleHeader = titleSection.selectFirst("h1");
        if (titleHeader == null) {
            return false;
        }

        String title = titleHeader.text();
        // optional subtitle
        final Element p = titleSection.selectFirst("p");
        if (p != null) {
            final String sub = p.text();
            if (!sub.isBlank()) {
                title += " - " + sub;
            }
        }
        book.setTitle(title);


        final Element origTitleHeader = titleSection.selectFirst("h3");
        if (origTitleHeader != null) {
            book.setTranslatedFromTitle(origTitleHeader.text());
        }

        final Element authors = titleSection.selectFirst("div.contributors-list");
        if (authors != null) {
            processAuthors(authors.select("li"), book);
        }
        return true;
    }

    /**
     * Συγγραφέας == Author
     * <pre>
     *    {@code
     *      <li class="text-4 mb-3">Συγγραφέας:
     *      <strong>
     *          <a role="link" href="/rené-goscinny-c787">René Goscinny</a>
     *          <a role="link" href="/albert-uderzo-c801">Albert Uderzo</a>
     *      </strong>
     *      </li>
     *    }
     * </pre>
     * Εικονογράφηση == Illustration
     * <pre>
     *     {@code
     *        <li class="text-4 mb-3">Εικονογράφηση:
     *          <strong>
     *          <a role="link" href="/albert-uderzo-c801">Albert Uderzo</a>
     *          </strong>
     *       </li>
     *     }
     * </pre>
     */
    private void processAuthors(@NonNull final Elements lis,
                                @NonNull final Book book) {
        lis.forEach(li -> {
            final String label = li.ownText();
            switch (label) {
                case "Συγγραφέας:":
                case "Author:": {
                    processAuthor(Author.TYPE_WRITER, li, book);
                    break;
                }
                case "Μετάφραση:":
                case "Translation:": {
                    processAuthor(Author.TYPE_TRANSLATOR, li, book);
                }
                case "Εικονογράφηση:":
                case "Illustrator:":
                case "Φωτογράφος:":
                case "Photographer:": {
                    processAuthor(Author.TYPE_ARTIST, li, book);
                    break;
                }
                case "Επιμέλεια:":
                case "Editor:":
                case "Ευθύνη Σειράς:":
                case "Series editor:": {
                    processAuthor(Author.TYPE_EDITOR, li, book);
                    break;
                }
                default: {
                    LoggerFactory.getLogger().w(TAG, "processContributors",
                                                "Label: " + label);
                }
            }
        });
    }

    private void processAuthor(@Author.Type final int type,
                               @NonNull final Element li,
                               @NonNull final Book book) {
        // <a role="link" href="/rené-goscinny-c787">René Goscinny</a>
        li.select("a").forEach(a -> {
            final Author author = Author.from(a.text());
            // TODO: author identifier?
            addAuthor(author, type, book);
        });
    }

    private void processDetails(@NonNull final Context context,
                                @NonNull final Element ul,
                                @NonNull final Book book) {
        @Nullable
        String seriesNum = null;

        for (final Element li : ul.select("li")) {
            final String label = li.ownText();
            final Element data = li.selectFirst("strong");
            // paranoia
            if (label.isEmpty() || data == null) {
                continue;
            }
            final String text = data.text();
            // paranoia
            if (text.isEmpty()) {
                continue;
            }

            // The english label SHOW as uppercas on the site, but ARE LOWERCASE in the html.
            switch (label) {
                case "Εκδοτης:":
                case "Publisher:": {
                    book.add(Publisher.from(text));
                    break;
                }
                case "Διαθεσιμοτητα:":
                case "Availability:": {
                    // Example: Κυκλοφορεί, In Print
                    break;
                }
                case "Ημ. Εκδοσης:":
                case "Publish date:": {
                    dateParser.parse(text).ifPresent(book::setPublicationDate);
                    break;
                }
                case "Ημ. 1ης εκδοσης:":
                case "First publish date:": {
                    dateParser.parse(text).ifPresent(book::setFirstPublicationDate);
                    break;
                }
                case "Αρ. εκδοσης:":
                case "Edition num.:": {
                    // Example: 1
                    break;
                }
                case "Περιοχη:":
                case "Area:": {
                    // Example: Athens
                    break;
                }
                case "ISBN:": {
                    book.setIsbn(ISBN.cleanText(text));
                    break;
                }
                case "Τιμη":
                case "Price": {
                    // no colon in label!
                    // ouch... the Greek Locale uses the "," as the decimal separator,
                    // but the site uses "." instead.
                    // While we would normally parse here with the site Locale,
                    // we parse with the UK one instead to use the "."
                    addPriceListed(context, Locale.UK, text, MoneyParser.EUR, book);
                    break;
                }
                case "Γλωσσα:":
                case "Language:": {
                    book.setLanguage(text);
                    break;
                }
                case "Γλωσσα Πρωτοτυπου:":
                case "Original language:": {
                    // a Greek book can also have the original lang set to Greek.
                    // Only store it if different
                    if (!text.equals(book.getLanguage())) {
                        book.setTranslatedFromLanguage(text);
                    }
                    break;
                }
                case "Μεταφραση απο:":
                case "Translated from:": {
                    break;
                }
                case "Αρ. Συλλογης:":
                case "Volume num.:": {
                    // The volume number appears 'before' the series, cache it locally
                    seriesNum = text;
                    break;
                }
                case "Σειρα:":
                case "Series title:": {
                    final Series currentSeries = Series.from(text);
                    // Add if not already present.
                    if (book.getSeries().stream()
                            .noneMatch(series -> series.equals(currentSeries))) {
                        // previously parsed number?
                        if (seriesNum != null) {
                            currentSeries.setNumber(seriesNum);
                            seriesNum = null;
                        }
                        book.add(currentSeries);
                    }
                    break;
                }
                case "Υποσειρα:":
                case "Subseries:": {
                    break;
                }
                case "Τυπος:":
                case "Type:": {
                    // Example: Βιβλίο; Book
                    // e-book
                    if ("e-book".equals(text)) {
                        book.setFormat(context.getString(R.string.book_format_ebook));
                    }
                    break;
                }
                case "Ηλικια:":
                case "Age:": {
                    // Example: από 7 έως 11 έτη
                    break;
                }
                case "Δεσιμο:":
                case "Cover:": {
                    // Example: Μαλακό εξώφυλλο; paperback
                    book.setFormat(text);
                    break;
                }
                case "Σελιδες:":
                case "Pages:": {
                    book.setPages(text);
                    break;
                }
                case "Διαστασεις (cm):":
                case "Dimensions (cm):": {
                    // Example: 27x21
                    break;
                }
                case "Βαρος (gr):":
                case "Weight (gr):": {
                    // xampl: 428
                    break;
                }
                case "Εχει Εικονογραφηση:":
                case "has Illustration:": {
                    // Example: Ναι
                    break;
                }
                case "Ειναι Μεταφρασμενο:":
                case "is Translated:": {
                    // Example: Ναι
                    break;
                }
                case "Εχει Βιβλιογραφια:":
                case "has Bibliography:": {
                    // Example: Ναι
                    break;
                }
                case "Σημειωσεις:":
                case "Notes:": {
                    book.setDescription(text);
                }

                //  Below are the second ul list labels

                case "Θεμα:":
                case "Subject:": {
                    // Subject -> use for tags
                    // Note: the html shows there might be another "tags" section
                    processSubjectTags(context, data, book);
                    break;
                }
            }
        }

        if (book.getLanguage().isBlank()) {
            book.setLanguage("ell");
        }
    }

    private void processSubjectTags(@NonNull final Context context,
                                    @NonNull final Element data,
                                    @NonNull final Book book) {
        final boolean keepPrefix = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(PK_TAG_PREFIX_NUMBER, false);

        final List<String> tagStrings = new ArrayList<>();
        // [741.5] Κόμικς
        // [889.3] Greek prose literature, Modern - Short story
        for (final Element badge : data.select("span.badge")) {
            final String tagText = badge.text();
            if (keepPrefix) {
                tagStrings.add(tagText);
            } else {
                final Matcher matcher = SUBJECT_BADGE_PATTERN.matcher(tagText);
                if (matcher.find()) {
                    final String subject = matcher.group(1);
                    if (!subject.isBlank()) {
                        tagStrings.add(subject);
                    }
                }
            }
        }
        final Set<String> tagsToIgnore = getEngineId().getConfig().getTagsToIgnore();
        final List<Tag> tags = tagStrings
                .stream()
                .filter(t -> !tagsToIgnore.contains(t))
                .map(Tag::new)
                .collect(Collectors.toList());
        book.setTags(tags);
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
            throws CoverStorageException {

        final Element cover = document.selectFirst("div.product-thumb-info-image > img");
        if (cover == null) {
            return Optional.empty();
        }
        String url = cover.attr("src");
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        return saveImage(context, url, null, bookId, cIdx, null);
    }
}
