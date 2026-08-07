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

package com.hardbacknutter.nevertoomanybooks.searchengines.biblionetgr;

import android.content.Context;
import android.os.LocaleList;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
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
 * As it turns out, the site has an API: <a href="https://www.biblionet.gr/webservice/">API</a>
 * but this engine was written before I found the API :/
 * Sticking with the current jsoup approach until it breaks, then moving to the API.
 * <p>
 * Identifiers: wikidata lists identifiers and url templates for both books and authors,
 * but <strong>these are invalid since the biblionet 2024 rebuild</strong>.
 * Currently (2025-12) no identifiers are supported.
 */
public class BiblionetGrSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn {

    private static final String SITE_URL = "https://biblionet.gr";

    private static final String TAG = "BiblionetGrSearchEngine";

    private static final Locale SITE_LOCALE = new Locale("el", "GR");

    private static final String PREFERENCE_KEY = "biblionetgr";

    /**
     * Subject tags on the site have a code prefixed. By default, we drop this
     * but the user can enable this in the settings.
     */
    static final String PK_TAG_PREFIX_NUMBER = PREFERENCE_KEY + ".search.tag.prefix_number";

    /**
     * Encoded text is:  σύνθετη αναζήτηση = “advanced search”.
     * Append the ISBN code as-is to search.
     */
    private static final String SEARCH = "/%CF%83%CF%85%CE%BD%CE%B8%CE%B5%CF%84"
                                         + "%CE%B7-%CE%B1%CE%BD%CE%B1%CE%B6%CE%B7"
                                         + "%CF%84%CE%B7%CF%83%CE%B7?q=";
    private static final Pattern SUBJECT_BADGE_PATTERN =
            Pattern.compile("\\[.*]\\s*(.*)");

    /** The language text as usd on the site. NOT the same as the Java 'display-name'. */
    private static final String ANCIENT_GREEK_TEXT = "Greek (Ancient script)";
    /** The Locale iso3 code for Ancient Greek. */
    private static final String ANCIENT_GREEK_ISO3 = "grc";
    /** The Locale iso3 code for Modern Greek. */
    private static final String MODERN_GREEK_ISO3 = "ell";

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
                .setAuthorResolverSupplier(BiblionetGrAuthorResolver::create)
                .setConfig(cb -> cb
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    private static String stripLabel(@NonNull final Element li) {
        final String label = li.ownText();
        if (label.endsWith(":")) {
            return label.substring(0, label.length() - 1).strip();
        }
        return label;
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final BookSearchCriteria criteria)
            throws StorageException, SearchException, CredentialsException {

        final ProductCode productCode = criteria.requireProductCode();
        final String codeStr = productCode.getFormatted(getEngineId());

        final String url = getHostUrl() + SEARCH + codeStr;
        final Document document = loadDocument(context, url, null);

        final Book book = new Book();
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
     * @param context     Current context
     * @param document    to parse
     * @param fetchCovers Set array indexes to {@code true} to fetch a cover for that index.
     *                    Array length is {@link DBKey#NR_OF_BOOK_COVERS}.
     * @param book        to update
     *
     * @throws CredentialsException  on authentication/login failures
     * @throws SearchException       on generic exceptions (wrapped) during search
     * @throws  StorageException     on storage related failures
     */
    @WorkerThread
    private void multiResult(@NonNull final Context context,
                             @NonNull final Document document,
                             @NonNull final boolean[] fetchCovers,
                             @NonNull final Book book)
            throws SearchException, CredentialsException, StorageException {

        final String url = parseMultiResult(document);
        if (url == null) {
            return;
        }
        final Document redirected = loadDocument(context, url, null);
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

        final Element urlElement = document.selectFirst("div#result_books a.book-title");
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
     * @throws StorageException      on storage related failures
     * @throws SearchException       on generic exceptions (wrapped) during search
     * @throws CredentialsException  on authentication/login failures
     *                               This should only occur if the engine calls/relies on
     *                               secondary sites.
     */
    @VisibleForTesting
    @WorkerThread
    public void parse(@NonNull final Context context,
                      @NonNull final Document document,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws SearchException, CredentialsException, StorageException {

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
                final String text = SearchEngineUtils.cleanText(desc);
                if (!text.isBlank()) {
                    final String description = book.getDescription();
                    if (description.isBlank()) {
                        book.setDescription(text);
                    } else {
                        // The "Notes" field when present is kept at the start.
                        // Example of notes:
                        //    Freely available under a Creative Commons licence via
                        //    the website www.saitapublications.gr
                        book.setDescription(description + "\n\n" + text);
                    }
                }
            }
        }

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            parseCovers(context, document, book.getRawProductCode(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private boolean processTitleAndAuthors(@NonNull final Element titleSection,
                                           @NonNull final Book book) {
        final Element titleHeader = titleSection.selectFirst("h1");
        if (titleHeader == null) {
            return false;
        }

        String title = SearchEngineUtils.cleanText(titleHeader);
        // optional subtitle
        final Element p = titleSection.selectFirst("p");
        if (p != null) {
            final String sub = SearchEngineUtils.cleanText(p);
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
     * Parse an author. The first text/label (e.g. Συγγραφέας) is the author type.
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
     *
     * @param list  to parse
     * @param book to update
     */
    private void processAuthors(@NonNull final Collection<Element> list,
                                @NonNull final Book book) {
        for (final Element li : list) {
            final String label = stripLabel(li);
            switch (label) {
                case "Συγγραφέας":
                case "Author": {
                    processAuthor(AuthorRole.WRITER, li, book);
                    break;
                }
                case "Μετάφραση":
                case "Translation": {
                    processAuthor(AuthorRole.TRANSLATOR, li, book);
                    break;
                }
                case "Εικονογράφηση":
                case "Illustrator":
                case "Φωτογράφος":
                case "Photographer":
                case "Ζωγράφος":
                case "Painter":
                case "Γλύπτης":
                case "Sculptor":
                case "Καλλιτέχνης":
                case "Artist": {
                    processAuthor(AuthorRole.ARTIST, li, book);
                    break;
                }
                case "Επιμέλεια":
                case "Editor":
                case "Ευθύνη Σειράς":
                case "Series editor":
                case "Ανθολόγος":
                case "Anthologist":
                case "Ευθύνη Υποσειράς":
                case "Sub-series editor": {
                    processAuthor(AuthorRole.EDITOR, li, book);
                    break;
                }
                case "Αφήγηση":
                case "Narrated by": {
                    processAuthor(AuthorRole.NARRATOR, li, book);
                    break;
                }
                case "Εισήγηση":
                case "Introduction": {
                    processAuthor(AuthorRole.INTRODUCTION, li, book);
                    break;
                }
                case "Επίμετρο": {
                    /* no English label. */
                    processAuthor(AuthorRole.AFTERWORD, li, book);
                    break;
                }
                case "Επιμέλεια Κειμένων": {
                    /* no English label. */
                    processAuthor(AuthorRole.EDITOR, li, book);
                    break;
                }
                case "Φορέας":
                case "Body": {
                    li.select("a").forEach(a -> {
                        // Φιλολογικός Όμιλος Αγρινίου "Κώστας Χατζόπουλος"
                        // -> Literary Society of Agrinio "Kostas Chatzopoulos"
                        final String s = SearchEngineUtils.cleanName(a);
                        if (!s.isBlank()) {
                            // Add directly, don't use the bookParserHelper addAuthor method
                            book.add(Author.asOrganisation(s));
                        }
                    });
                    break;
                }
                case "Μεταγραφή": {
                    /* no English label; Transcription? */
                    processAuthor(AuthorRole.UNKNOWN, li, book);
                    break;
                }
                case "Απόδοση": {
                    /* no English label; Performance? */
                    processAuthor(AuthorRole.UNKNOWN, li, book);
                    break;
                }
                case "Ερμηνεία":
                case "Performed by":
                case "Σύνθεση":
                case "Composer":
                case "Στιχουργός":
                case "Lyrist":
                case "Διασκευή":
                case "Adaptation": {
                    processAuthor(AuthorRole.UNKNOWN, li, book);
                    break;
                }
                default: {
                    LoggerFactory.getLogger().w(TAG, "processContributors",
                                                "Label: " + label);
                }
            }
        }
    }

    private void processAuthor(@AuthorRole.Role final int type,
                               @NonNull final Element li,
                               @NonNull final Book book) {
        // <a role="link" href="/rené-goscinny-c787">René Goscinny</a>
        li.select("a")
          .stream()
          .map(SearchEngineUtils::cleanName)
          .filter(name -> !name.isBlank())
          .map(Author::from)
          .forEach(a -> bookParserHelper.addAuthor(a, type, book, false));
    }

    private void processDetails(@NonNull final Context context,
                                @NonNull final Element ul,
                                @NonNull final Book book) {
        @Nullable
        String seriesNum = null;

        for (final Element li : ul.select("li")) {
            final String label = stripLabel(li);
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

            // The English label SHOW as uppercase on the site, but ARE LOWERCASE in the HTML.
            switch (label) {
                case "Εκδοτης":
                case "Publisher": {
                    final String s = SearchEngineUtils.cleanName(text);
                    if (!s.isBlank()) {
                        book.add(Publisher.from(s));
                    }
                    break;
                }
                case "Διαθεσιμοτητα":
                case "Availability": {
                    // Example: Κυκλοφορεί, In Print
                    break;
                }
                case "Ημ. Εκδοσης":
                case "Publish date": {
                    dateParser.parse(text).ifPresent(book::setPublicationDate);
                    break;
                }
                case "Ημ. 1ης εκδοσης":
                case "First publish date": {
                    dateParser.parse(text).ifPresent(book::setFirstPublicationDate);
                    break;
                }
                case "Αρ. εκδοσης":
                case "Edition num.": {
                    // Example: 1
                    break;
                }
                case "Περιοχη":
                case "Area": {
                    // Example: Athens
                    break;
                }
                case "ISBN": {
                    book.setRawProductCode(ISBN.cleanText(text));
                    break;
                }
                case "Τιμη":
                case "Price": {
                    parsePrice(context, text, book);
                    break;
                }
                case "Γλωσσα":
                case "Language": {
                    if (ANCIENT_GREEK_TEXT.equals(text)) {
                        book.setLanguage(ANCIENT_GREEK_ISO3);
                    } else {
                        book.setLanguage(text);
                    }
                    break;
                }
                case "Γλωσσα Πρωτοτυπου":
                case "Original language": {
                    if (ANCIENT_GREEK_TEXT.equals(text)) {
                        book.setTranslatedFromLanguage(ANCIENT_GREEK_ISO3);
                    } else {
                        // a Greek book can also have the original language set to Greek.
                        // Only store it if different
                        if (!text.equals(book.getLanguage())) {
                            book.setTranslatedFromLanguage(text);
                        }
                    }
                    break;
                }
                case "Μεταφραση απο":
                case "Translated from": {
                    break;
                }
                case "Αρ. Συλλογης":
                case "Volume num.": {
                    // The volume number appears 'before' the series, cache it locally
                    seriesNum = text;
                    break;
                }
                case "Συλλογη":
                case "Collection": {
                    break;
                }
                case "ISBN Σετ":
                case "Collection ISBN": {
                    break;
                }
                case "Σειρα":
                case "Series title": {
                    final String s = SearchEngineUtils.cleanName(text);
                    if (!s.isBlank()) {
                        final Series currentSeries = Series.from(s);
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
                    }
                    break;
                }
                case "Υποσειρα":
                case "Subseries": {
                    break;
                }
                case "Τυπος":
                case "Type": {
                    // Ignore: Βιβλίο; Book
                    // otherwise set the format
                    // See also "Cover" below.
                    if (!"Βιβλίο".equals(text)) {
                        book.setFormat(context.getString(R.string.book_format_ebook));
                    }
                    break;
                }
                case "Ηλικια":
                case "Age": {
                    // Example: από 7 έως 11 έτη
                    break;
                }
                case "Δεσιμο":
                case "Cover": {
                    // The site only lists hard/soft covers here.
                    // see "Type" above for more.
                    // We take "Type" by preference.
                    if (book.getFormat().isBlank()) {
                        book.setFormat(text);
                    }
                    break;
                }
                case "Σελιδες":
                case "Pages": {
                    book.setPages(text);
                    break;
                }
                case "Διαστασεις (cm)":
                case "Dimensions (cm)": {
                    // Example: 27x21
                    break;
                }
                case "Βαρος (gr)":
                case "Weight (gr)": {
                    // xampl: 428
                    break;
                }
                case "Εχει Εικονογραφηση":
                case "has Illustration": {
                    // Example: Ναι
                    break;
                }
                case "Ειναι Μεταφρασμενο":
                case "is Translated": {
                    // Example: Ναι
                    break;
                }
                case "Εχει Βιβλιογραφια":
                case "has Bibliography": {
                    // Example: Ναι
                    break;
                }
                case "Λογοτεχνικα Βραβεια":
                case "Awards": {
                    // these are "badge" style entries; see "Subject" below
                    // on how to parse those.
                    break;
                }
                case "Σημειωσεις":
                case "Notes": {
                    // Always set here, we'll append the full description as needed.
                    book.setDescription(text);
                    break;
                }

                //  Below are the second ul list labels

                case "Θεμα":
                case "Subject": {
                    // Subject -> use for tags
                    // Note: the html shows there might be another "tags" section
                    processSubjectTags(data, book);
                    break;
                }
            }
        }

        // Fallback to Greek if not set
        if (book.getLanguage().isBlank()) {
            book.setLanguage(MODERN_GREEK_ISO3);
        }
    }

    private void parsePrice(@NonNull final Context context,
                            @NonNull final String text,
                            @NonNull final Book book) {
        // ouch... the Greek Locale uses the "," as the decimal separator,
        // but the site uses "." instead.
        // While we would normally parse here with the site Locale,
        // we parse the mony value with the UK one instead to force a "."
        final LocaleList userLocales = context.getResources().getConfiguration().getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(Locale.UK, userLocales);
        final MoneyParser parser = new MoneyParser(SITE_LOCALE, allLocales);
        bookParserHelper.addPriceListed(parser, text, MoneyParser.EUR, book);
    }

    private void processSubjectTags(@NonNull final Element data,
                                    @NonNull final Book book) {
        final boolean keepPrefix = ServiceLocator.getInstance().getSharedPreferences()
                                                 .getBoolean(PK_TAG_PREFIX_NUMBER, false);

        final List<String> tagNames = new ArrayList<>();
        // [741.5] Κόμικς
        // [889.3] Greek prose literature, Modern - Short story
        for (final Element badge : data.select("span.badge")) {
            final String tagText = badge.text();
            if (keepPrefix) {
                tagNames.add(tagText);
            } else {
                final Matcher matcher = SUBJECT_BADGE_PATTERN.matcher(tagText);
                if (matcher.find()) {
                    final String subject = matcher.group(1);
                    if (subject != null && !subject.isBlank()) {
                        tagNames.add(subject);
                    }
                }
            }
        }
        bookParserHelper.setTags(tagNames, book);
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
