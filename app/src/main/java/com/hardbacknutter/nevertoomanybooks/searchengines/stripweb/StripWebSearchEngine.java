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

package com.hardbacknutter.nevertoomanybooks.searchengines.stripweb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.LocaleList;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.storage.StorageException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
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
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * <a href="https://www.stripweb.be">StripWeb</a>
 * <p>
 * Dutch language (and to some extent other languages) comics.
 * <p>
 * The site can be accessed in Dutch,French,English. We use the Dutch site for access.
 * The main reason for this one is having access to current list-prices;
 * otherwise the recommendation is to use
 * {@link com.hardbacknutter.nevertoomanybooks.searchengines.EngineId#StripInfoBe}
 * and {@link com.hardbacknutter.nevertoomanybooks.searchengines.EngineId#LastDodoNl}.
 * <p>
 * {@link SearchEngine.ByBarcode}: for barcodes and invalid ISBNs
 * The site also sells comic related merchandise, which has a site-specific code
 * and can be searched as a generic code.
 * The site treats this as a plain (but invalid) ISBN code.
 */
public class StripWebSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByText,
                   SearchEngine.ByBarcode,
                   SearchEngine.SearchOnSite {

    public static final String SITE_URL = "https://www.stripweb.be";
    // A permalink to the product nr is not possible
    public static final String BOOK_URL = null;
    public static final String AUTHOR_URL = null;

    private static final String PREFERENCE_KEY = "stripweb";

    /** Website character encoding. */
    private static final String CHARSET = "UTF-8";

    /**
     * Param 1: search terms.
     */
    private static final String SEARCH_URL = "/nl-nl/zoeken?type=&text=%1$s";

    /**
     * Some titles have suffixes which we need to strip.
     * A big mess.... there is no structure on the website for these... seems
     * to depend on the mood of the person entering the title...
     * However, these books are generally NOT listed under their ISBN,
     * so unless the user enters the private site code they won't show up anyhow.
     * <p>
     * Entries MUST all be lowercase.
     */
    private static final Set<String> TITLE_SUFFIXES = Set.of(" - met ex libris - sc",
                                                             " - met ex libris hc",
                                                             " - met ex libris",
                                                             " met ex libris sc",
                                                             " + ex libris",
                                                             " -vip club met ex libris",
                                                             " + ex libris gesigneerd hc",
                                                             // typo is from website!
                                                             " + ex lbiris sc",
                                                             " sc");
    private static final String LANG_NLD = "nld";
    private static final String LANG_FRA = "fra";
    private static final String LANG_ENG = "eng";

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
    public StripWebSearchEngine(@NonNull final Context appContext,
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
                                    R.string.site_stripweb_be,
                                    List.of(R.string.site_description_dutch_and_more,
                                            R.string.site_description_shop,
                                            R.string.site_description_eu_comics),
                                    "https://www.stripweb.be",
                                    new Locale("nl", "BE"))
                .setPreferenceFragmentClazz(StripWebPreferencesFragment.class);
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
    @WorkerThread
    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws StorageException, SearchException, CredentialsException {

        // Grab the first search result, and redirect to that page
        final Element section = document.selectFirst("div.overview-item");
        // it will be null if there were no results.
        if (section != null) {
            final Element urlElement = section.selectFirst("a");
            if (urlElement != null) {
                String url = urlElement.attr("href");
                // sanity check
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

        final Locale siteLocale = getLocale(context);

        final Element main = document.selectFirst("main.content");
        if (main == null) {
            return;
        }
        final Element row = main.selectFirst("div.row");
        if (row == null) {
            return;
        }

        // dic class="col-lg-8 col-sm-8 pr-xl-9"
        final Element details = main.selectFirst("div.col-lg-8");
        if (details == null) {
            return;
        }

        final Element titleElement = details.selectFirst("h1");
        if (titleElement == null) {
            return;
        }
        parseTitle(context, titleElement, book);

        final Element techInfoSection = details.selectFirst("div.techinfo");
        if (techInfoSection == null) {
            return;
        }

        String tmpSeriesNr = null;
        // We're not using the helper 'setTags(tagNames, book) because
        // this site can have tags in two different sections.
        //noinspection DataFlowIssue
        final Set<String> tagsToIgnore = getEngineId().getConfig().getTagsToIgnore();

        for (final Element divRows : techInfoSection.select("div")) {
            final Element th = divRows.selectFirst("strong");
            final Element td = divRows.selectFirst("span");
            if (th != null && td != null) {
                switch (th.text()) {
                    case "ISBN nummer": {
                        book.setIsbn(ISBN.cleanText(td.text()));
                        break;
                    }
                    case "Pagina's":
                        book.setPages(cleanText(td));
                        break;
                    case "Reeks":
                        parseSeries(td, book);
                        break;
                    case "Nummer":
                        tmpSeriesNr = td.text();
                        break;
                    case "Taal":
                        parseLanguage(book, td);
                        break;
                    case "Cover":
                        book.setFormat(cleanText(td));
                        break;
                    case "Verschijningsdatum": {
                        final String text = cleanText(td);
                        if (!text.isEmpty()) {
                            addPublicationDate(context, siteLocale, text, book);
                        }
                        break;
                    }
                    case "Tekenaars":
                        parseAuthor(td, AuthorRole.ARTIST, book);
                        break;
                    case "Scenarist":
                        parseAuthor(td, AuthorRole.WRITER, book);
                        break;
                    case "Inkleuring":
                        parseAuthor(td, AuthorRole.COLORIST, book);
                        break;
                    case "Cover artiest":
                        parseAuthor(td, AuthorRole.COVER_ARTIST, book);
                        break;
                    case "Uitgeverij":
                        parsePublisher(td, book);
                        break;

                    case "Afmetingen": {
                        final String text = cleanText(td);
                        if (!text.isBlank()) {
                            book.putString(SiteField.SIZE, text);
                        }
                        break;
                    }
                    case "Genre": {
                        final String text = cleanText(td);
                        if (!text.isBlank() && !tagsToIgnore.contains(text)) {
                            // Use 'add', as tags can also be populated by "Trefwoorden"
                            book.addTags(List.of(new Tag(text)));
                        }
                        break;
                    }
                    case "Trefwoorden": {
                        // comma separated words but with extra whitespace we must remove
                        final String[] split = cleanText(td).split(",");

                        // Use 'add', as tags can also be populated by "Genre"
                        book.addTags(Arrays.stream(split)
                                           .map(String::strip)
                                           .filter(t -> !t.isBlank())
                                           .filter(t -> !tagsToIgnore.contains(t))
                                           .map(Tag::new)
                                           .collect(Collectors.toList()));
                        break;
                    }
                    default:
                        // Other labels which are ignored:
                        // "Collectie" seen for a "Schilderdoeken"
                        // "Gewicht" weight in grams
                        //  size
                        // "Formaat" size
                        // "Levertermijn"
                        // "FocVendorDate": Final Order Cutoff Date
                        // "Brand Code"
                        // "Diamond Code"
                        // "Upc"
                        break;
                }
            }
        }

        parsePrice(context, document, book);
        parseDescription(document, book);
        parseRating(details, book);

        // post-process all found data.

        if (tmpSeriesNr != null && !tmpSeriesNr.isEmpty()) {
            final List<Series> seriesList = book.getSeries();
            if (seriesList.size() == 1) {
                final Series series = seriesList.get(0);
                series.setNumber(tmpSeriesNr);
            }
            //else if (seriesList.isEmpty()) {
            // A book can have a series number without a series name.
            // This was noted on certain Manga/Comics publications.
            // The title always (?) contains the number,
            // so we're ignoring this.
            //}
        }

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final String isbn = book.getIsbn();
            // start from 'main' !
            parseCover(context, main, isbn, 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    private void parseTitle(@NonNull final Context context,
                            @NonNull final Element titleElement,
                            @NonNull final Book book) {
        final String text = cleanText(titleElement);
        if (!text.isBlank()) {
            // TITLE_SUFFIXES are as entered by site-employees, hence use site-locale
            final String lcText = text.toLowerCase(getLocale(context));
            final String title = TITLE_SUFFIXES
                    .stream()
                    .filter(lcText::endsWith)
                    .map(suffix -> text.substring(0, text.length() - suffix.length()))
                    .findAny()
                    .orElse(text);
            book.setTitle(title);
        }
    }

    private void parseRating(@NonNull final Element details,
                             @NonNull final Book book) {
        final Element stars = details.selectFirst("div.stars");
        if (stars != null) {
            final Elements nr = stars.select("i.text-yellow");
            // Rating is simply the amount of stars, i.e. 0..5
            // Only add if at least 1 star (and max 5 as sanity check)
            final int rating = nr.size();
            if (rating > 0 && rating < 6) {
                book.setRating(rating);
            }
        }
    }

    private void parseLanguage(@NonNull final Book book,
                               @NonNull final Element td) {
        final String langCode = cleanText(td);
        if (!langCode.isBlank()) {
            // Another mess... the site uses an abbreviation for the language,
            // but NOT a standard one.
            // Seen in use: NL,FR,Fr,EN
            switch (langCode.toLowerCase(Locale.ROOT)) {
                case "nl":
                    book.setLanguage(LANG_NLD);
                    break;
                case "fr":
                    book.setLanguage(LANG_FRA);
                    break;
                case "en":
                    book.setLanguage(LANG_ENG);
                    break;
                default:
                    book.setLanguage(langCode);
                    break;
            }
        }
    }

    private void parseDescription(@NonNull final Document document,
                                  @NonNull final Book book) {
        final Element desc = document.selectFirst("div.detail-description");
        if (desc != null) {
            String html = desc.html();
            // Potentially contains an iframe (e.g. to YouTube content); remove it.
            final int iStart = html.indexOf("<iframe");
            if (iStart > 0) {
                final int iEnd = html.indexOf("</iframe>");
                // Sanity check
                if (iEnd > iStart) {
                    html = html.substring(0, iStart) + " " + html.substring(iEnd + 9);
                }
            }

            final String s = cleanText(html);
            if (!s.isBlank()) {
                book.setDescription(s);
            }
        }
    }

    private void parsePrice(@NonNull final Context context,
                            @NonNull final Document document,
                            @NonNull final Book book) {
        final Element cartForm = document.selectFirst("form[id='frmAddToCart']");
        if (cartForm != null) {
            // In EURO; contains a comma as decimal separate.
            final Element price = cartForm.selectFirst("span[itemprop='price']");
            if (price != null) {
                final Locale siteLocale = getLocale(context, document.location().split("/")[2]);

                final String priceStr = price.text().strip();
                final LocaleList userLocales = context.getResources().getConfiguration()
                                                      .getLocales();
                final List<Locale> allLocales = LocaleListUtils.asList(siteLocale, userLocales);
                final MoneyParser parser = new MoneyParser(siteLocale, allLocales);
                addPriceListed(context, parser, priceStr, MoneyParser.EUR, book);
            }

            final Element sidElement = cartForm.selectFirst("input[id='hdnArticleNo']");
            if (sidElement != null) {
                final String sid = sidElement.attr("value");
                book.setIdentifierValue(Identifier.SID_STRIPWEB, sid);
            }
        }
    }

    /**
     * Found an Author.
     *
     * @param td   data td
     * @param type of this entry
     * @param book Bundle to update
     */
    private void parseAuthor(@NonNull final Element td,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {

        // Most books list the authors as "a" elements
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            // but some are plain text separated by commas
            final String[] names = td.text().split(",");
            Arrays.stream(names)
                  .map(this::cleanName)
                  .filter(name -> !name.isBlank())
                  .forEach(name -> parseAuthor(name, type, book));
        } else {
            aas.stream()
               .map(this::cleanName)
               .filter(name -> !name.isBlank())
               .forEach(name -> parseAuthor(name, type, book));
        }
    }

    private void parseAuthor(@NonNull final String name,
                             @AuthorRole.Role final int type,
                             @NonNull final Book book) {
        // The site actually uses "lastname firstname" or just "lastname".
        // This creates additional issues with names like "Van Hamme" which is a "lastname"
        // with a space in... nice mess...
        // So far this is the only site doing so consistently (other sites 'sometimes' do it).
        // We decode as usual,
        final Author author = Author.from(name);
        // and then swap the names... sigh... this is easier than adapting the parser.
        final String family = author.getGivenNames();
        // but only swap when there ARE two names....
        if (!family.isEmpty()) {
            // and apply a HACK.... for some common Flemish names
            // which is NOT exhaustive but better than nothing...
            if ("van".equalsIgnoreCase(family) || "de".equalsIgnoreCase(family)) {
                author.setName(family + " " + author.getFamilyName(), "");
            } else {
                author.setName(family, author.getFamilyName());
            }
        }
        // Add/merge or skip if already present
        addAuthor(author, type, book);
    }

    /**
     * Found a Series.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void parseSeries(@NonNull final Element td,
                             @NonNull final Book book) {
        // Most books list the series as "a" elements
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            // but some are plain text separated by commas
            final String[] names = td.text().split(",");
            Arrays.stream(names)
                  .map(this::cleanText)
                  .filter(name -> !name.isBlank())
                  .map(Series::from)
                  .forEach(series -> {
                      // Add if not already present.
                      if (book.getSeries().stream().noneMatch(series1 -> series1.equals(series))) {
                          book.add(series);
                      }
                  });
        } else {
            aas.stream()
               .map(this::cleanText)
               .filter(name -> !name.isBlank())
               .map(Series::from)
               .forEach(series -> {
                   // Add if not already present.
                   if (book.getSeries().stream().noneMatch(series1 -> series1.equals(series))) {
                       book.add(series);
                   }
               });
        }
    }

    /**
     * Found a Publisher.
     *
     * @param td   data td
     * @param book Bundle to update
     */
    private void parsePublisher(@NonNull final Element td,
                                @NonNull final Book book) {
        // Most books list the publishers as "a" elements
        final Elements aas = td.select("a");
        if (aas.isEmpty()) {
            // but some are plain text separated by commas
            final String[] names = td.text().split(",");
            Arrays.stream(names)
                  .map(this::cleanName)
                  .filter(name -> !name.isBlank())
                  .map(Publisher::from)
                  .forEach(book::add);
        } else {
            aas.stream()
               .map(this::cleanName)
               .filter(name -> !name.isBlank())
               .map(Publisher::from)
               .forEach(book::add);
        }
    }

    /**
     * Parses the given {@link Element} for the cover and fetches it when present.
     *
     * @param context Current context
     * @param main    the "main.content" element to parse
     * @param bookId  (optional) isbn or native id of the book,
     *                will only be used for the temporary cover filename
     * @param cIdx    0..n image index
     *
     * @return fileSpec
     *
     * @throws StorageException on storage related failures
     */
    @WorkerThread
    @NonNull
    private Optional<String> parseCover(@NonNull final Context context,
                                        @NonNull final Element main,
                                        @Nullable final String bookId,
                                        @SuppressWarnings("SameParameterValue")
                                            @IntRange(from = 0, to = 0) final int cIdx)
            throws StorageException {

        final Element cover = main.selectFirst("a.d-block");
        if (cover == null) {
            return Optional.empty();
        }
        String url = cover.attr("href");
        // Sanity check; the url is supposed to be relative
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        return saveImage(context, url, null, bookId, cIdx, null);
    }

    @Override
    public boolean isShowSearchOnSiteMenu(@NonNull final Context context) {
        final String key = PREFERENCE_KEY + '.' + SearchEngineConfig.PK_SEARCH_WEBSITE_MENU;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false);
        } else {
            final Languages languages = ServiceLocator.getInstance().getLanguages();
            return languages.isUserLanguage(context, LANG_NLD)
                   || languages.isUserLanguage(context, LANG_FRA);
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

        return getHostUrl() + String.format(SEARCH_URL, fields);
    }

    public static final class SiteField {

        static final String SIZE = "__size";

        private SiteField() {
        }
    }
}
