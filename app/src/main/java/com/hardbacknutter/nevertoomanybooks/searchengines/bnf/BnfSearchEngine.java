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

package com.hardbacknutter.nevertoomanybooks.searchengines.bnf;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.core.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISNI;
import com.hardbacknutter.nevertoomanybooks.covers.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searchengines.AuthorResolverHelper;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.CoverFileSpecArray;
import com.hardbacknutter.nevertoomanybooks.searchengines.EngineId;
import com.hardbacknutter.nevertoomanybooks.searchengines.JsoupSearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class BnfSearchEngine
        extends JsoupSearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ByText {

    public static final String SITE_URL = "https://www.bnf.fr";
    public static final String BOOK_URL = "https://catalogue.bnf.fr/ark:/12148/%s";
    public static final String AUTHOR_URL = "https://catalogue.bnf.fr/ark:/12148/%s";

    // also used as the identifier value
    private static final String PREFERENCE_KEY = "bnf";
    private static final Locale SITE_LOCALE = Locale.FRANCE;

    private static final Pattern PAGES_PATTERN = Pattern.compile(".*\\((\\d*).*?\\).*");
    private static final Pattern YEAR_PATTERN = Pattern.compile(".*?(\\d\\d\\d\\d).*?");

    private static final String URL_SUFFIX_UNIMARC = ".unimarc";

    private static final String SEARCH = "/rechercher.do?motRecherche=%1$s"
                                         + "&critereRecherche=0"
                                         + "&depart=0"
                                         + "&facetteModifiee=ok";

    private static final Map<String, Integer> AUTHOR_CODES = Map.ofEntries(
            Map.entry("000", AuthorRole.UNKNOWN),
            Map.entry("040", AuthorRole.ARTIST),
            Map.entry("070", AuthorRole.WRITER),
            Map.entry("690", AuthorRole.WRITER),
            Map.entry("075", AuthorRole.AFTERWORD),
            Map.entry("080", AuthorRole.INTRODUCTION),
            Map.entry("205", AuthorRole.CONTRIBUTOR),
            Map.entry("570", AuthorRole.CONTRIBUTOR),
            Map.entry("340", AuthorRole.EDITOR),
            Map.entry("410", AuthorRole.COLORIST),
            // overlap with "040"
            Map.entry("440", AuthorRole.ARTIST),
            Map.entry("550", AuthorRole.NARRATOR),
            Map.entry("730", AuthorRole.TRANSLATOR),
            Map.entry("750", AuthorRole.LETTERING));

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
    public BnfSearchEngine(@NonNull final Context appContext,
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
                                    R.string.site_bnf_fr,
                                    List.of(R.string.site_description_french,
                                            R.string.site_description_catalog),
                                    "https://catalogue.bnf.fr",
                                    SITE_LOCALE)
                .setIdentifierKey(Identifier.SID_BNF)
                .setPreferenceFragmentClazz(BnfPreferencesFragment.class)
                .setConfig(cb -> cb
                        .build(SearchEngineConfig::new));
    }

    @NonNull
    @Override
    public Book searchByExternalId(@NonNull final Context context,
                                   @NonNull final String externalId,
                                   @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, CoverStorageException {

        final String url = getHostUrl() + '/' + externalId;
        return search(context, url, fetchCovers);
    }

    @NonNull
    @Override
    public Book searchByIsbn(@NonNull final Context context,
                             @NonNull final String validIsbn,
                             @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, CoverStorageException {

        final String url = getHostUrl() + String.format(SEARCH, validIsbn);
        return search(context, url, fetchCovers);
    }

    @NonNull
    @Override
    public Book search(@NonNull final Context context,
                       @NonNull final BookSearchCriteria criteria,
                       @Nullable final String code,
                       @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, CoverStorageException {
        // Searches are just a string of 'words', we can simply concatenate all available options.
        final StringJoiner words = criteria.concatTextCriteria(" ");
        if (code != null && !code.isEmpty()) {
            words.add(code);
        }

        // Sanity check
        if (words.length() == 0) {
            return new Book();
        }

        final String url = getHostUrl() + String.format(SEARCH, words);
        return search(context, url, fetchCovers);
    }

    @NonNull
    private Book search(@NonNull final Context context,
                        @NonNull final String url,
                        @NonNull final boolean[] fetchCovers)
            throws SearchException, CredentialsException, CoverStorageException {
        final Book book = new Book();
        // Load the 'public' page which will tell us if we found the book
        // and (hopefully) contains the cover image.
        final Document pubDocument = loadDocument(context, url, null);
        final Element nbNotice = pubDocument.selectFirst("div#result > span");
        if (nbNotice != null) {
            // zero or multiple results
            if (!"0".equals(nbNotice.text())) {
                parseMultiResult(context, pubDocument, fetchCovers, book);
            }
        } else {
            // it was a single result
            search(context, pubDocument, fetchCovers, book);
        }
        return book;
    }

    private void parseMultiResult(@NonNull final Context context,
                                  @NonNull final Document document,
                                  @NonNull final boolean[] fetchCovers,
                                  @NonNull final Book book)
            throws SearchException, CredentialsException, CoverStorageException {

        final Element ul = document.selectFirst("ul#ancrePremiereNotice");
        if (ul == null) {
            return;
        }
        final Element a = ul.selectFirst("a");
        if (a == null) {
            return;
        }

        final String href = a.attr("href");
        if (href.isEmpty()) {
            return;
        }
        // /ark:/12148/cb31667415m
        final Document p = loadDocument(context, getHostUrl() + href, null);
        search(context, p, fetchCovers, book);
    }

    private void search(@NonNull final Context context,
                        @NonNull final Document pubDocument,
                        @NonNull final boolean[] fetchCovers,
                        @NonNull final Book book)
            throws SearchException, CredentialsException, CoverStorageException {
        // First get the unimarc page to easily parse the book data
        // https://catalogue.bnf.fr/ark:/12148/cb476077541;jsessionid=9905435942FD1782C64BE0E55D4A173A
        final String[] location = pubDocument.location().split(";");
        final String url = location[0] + URL_SUFFIX_UNIMARC
                           + (location.length > 1 && location[1] != null
                              ? ";" + location[1]
                              : "");
        final Document unimarcDocument = loadDocument(context, url, null);
        parse(context, unimarcDocument, book);

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        // We should now have the isbn, go parse the cover
        if (fetchCovers[0]) {
            parseCovers(context, pubDocument, book);
        }
    }

    @VisibleForTesting
    public void parseCovers(@NonNull final Context context,
                            @NonNull final Document document,
                            @NonNull final Book book)
            throws CoverStorageException {
        parseCovers(context, document, book.getIsbn(), 0).ifPresent(
                fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
    }

    private void parse(@NonNull final Context context,
                       @NonNull final Document document,
                       @NonNull final Book book)
            throws SearchException {

        final Element element = document.selectFirst("div#ancreNotice");
        if (element == null) {
            return;
        }

        final Elements select = element.select("div.zone");
        final List<String> rows = select
                .stream()
                .map(Element::text)
                .collect(Collectors.toList());

        parse(context, rows, book);
    }

    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final List<String> rows,
                      @NonNull final Book book)
            throws SearchException {
        try {
            for (final String row : rows) {
                final String tag = row.substring(0, 3);
                String s;
                switch (tag) {
                    case "003": {
                        // Persistent Record Identifier
                        // 003 http://catalogue.bnf.fr/ark:/12148/cb424392165
                        final int lastIndex = row.lastIndexOf('/');
                        if (lastIndex > 0) {
                            s = row.substring(lastIndex + 1);
                            if (!s.isBlank()) {
                                book.setIdentifierValue(Identifier.SID_BNF, s);
                            }
                        }
                        break;
                    }
                    case "010": {
                        processIsbnFormatAndPrice(context, row, book);
                        break;
                    }
                    case "011": {
                        // ISSN
                        // Only grab the first should there be multiple
                        if (!book.hasIsbn()) {
                            final Map<Character, String> fields = parseUnimarcField(row);
                            s = ISBN.cleanText(fields.get('a'));
                            if (!s.isEmpty()) {
                                book.setIsbn(s);
                            }
                        }
                        break;
                    }
                    case "101": {
                        // Language
                        processLanguage(row, book);
                        break;
                    }
                    case "200": {
                        // Title
                        // 200 1. $a Le Dieu du carnage $b Texte imprimé $f Yasmina Reza
                        // $g présentation, notes, questions et après-texte établis...
                        final Map<Character, String> fields = parseUnimarcField(row);
                        s = fields.get('a');
                        if (s != null && !s.isEmpty()) {
                            book.setTitle(s);
                        }
                        break;
                    }
                    case "205": {
                        // Edition
                        // TODO: parse the text? it is wildly varied.
                        break;
                    }
                    case "210":
                    case "214": {
                        processPublication(context, row, book);
                        break;
                    }
                    case "215": {
                        processPhysicalDescription(context, row, book);
                        break;
                    }
                    case "225": {
                        processSeries(row, book);
                        break;
                    }
                    case "330": {
                        // Summary
                        final Map<Character, String> fields = parseUnimarcField(row);
                        s = fields.get('a');
                        if (s != null && !s.isEmpty()) {
                            book.setDescription(s);
                        }
                        break;
                    }
                    case "454": {
                        // "454 .1 $t The etymologies\n"
                        final Map<Character, String> fields = parseUnimarcField(row);
                        s = fields.get('t');
                        if (s != null && !s.isEmpty()) {
                            book.setTranslatedFromTitle(s);
                        }
                        break;
                    }
                    case "700":
                    case "701":
                    case "702": {
                        processAuthor(row, book);
                        break;
                    }
                    default:
                        break;
                }
            }
        } catch (@NonNull final RuntimeException e) {
            // parsing issues; we don't check string length etc...
            throw new SearchException(getEngineId(), e);
        }
    }

    private void processIsbnFormatAndPrice(@NonNull final Context context,
                                           @NonNull final String row,
                                           @NonNull final Book book) {
        String s;
        // 010 .. $a 978-2-210-75564-2 $b br. $d 5 EUR
        final Map<Character, String> fields = parseUnimarcField(row);
        // Only grab the first should there be multiple
        if (!book.hasIsbn()) {
            s = ISBN.cleanText(fields.get('a'));
            if (!s.isEmpty()) {
                book.setIsbn(s);
            }
        }
        s = fields.get('b');
        if (s != null && !s.isEmpty()) {
            // The values are not well-defined?
            // Limit what we accept:
            final String lc = s.toLowerCase(SITE_LOCALE);
            if ("br.".equals(lc)
                || "rel.".equals(lc)) {
                // The FormatMapper will transform as needed/permitted
                book.setFormat(s);
            }
        }
        s = fields.get('d');
        if (s != null && !s.isEmpty()) {
            addPriceListed(context, SITE_LOCALE, s, null, book);
        }
    }

    private void processSeries(@NonNull final String row,
                               @NonNull final Book book) {
        // 225 |. $a Classiques & contemporains $e collège $v 128
        final Map<Character, String> fields = parseUnimarcField(row);
        final String title = fields.get('a');
        if (title != null && !title.isEmpty()) {
            final String nr = fields.get('v');
            book.add(Series.from(title, nr));
        }
    }

    private void processLanguage(@NonNull final String row,
                                 @NonNull final Book book) {
        String s;
        // 101 0. $a fre
        final Map<Character, String> fields = parseUnimarcField(row);
        s = fields.get('a');
        if (s != null && !s.isEmpty()) {
            book.setLanguage(s);
        }
        s = fields.get('c');
        if (s != null && !s.isEmpty()) {
            book.setTranslatedFromLanguage(s);
        }
    }

    private void processPublication(@NonNull final Context context,
                                    @NonNull final String row,
                                    @NonNull final Book book) {
        String s;
        // 210 .. $a [Paris] $c Magnard $d impr. 2011 $e impr. en Italie
        final Map<Character, String> fields = parseUnimarcField(row);
        s = fields.get('c');
        if (s != null && !s.isEmpty()) {
            book.add(Publisher.from(s));
        }
        // Date of Publication - free text ... sigh
        // DL 2019
        s = fields.get('d');
        if (s != null && !s.isEmpty()) {
            final Matcher matcher = YEAR_PATTERN.matcher(s);
            if (matcher.find()) {
                s = matcher.group(1);
                if (s != null && !s.isEmpty()) {
                    addPublicationDate(context, SITE_LOCALE, s, book);
                }
            }
        }
    }

    private void processPhysicalDescription(@NonNull final Context context,
                                            @NonNull final String row,
                                            @NonNull final Book book) {
        String s;
        // 215 .. $a 1 vol. (107 p.) $c couv. ill. en coul. $d 18 cm
        final Map<Character, String> fields = parseUnimarcField(row);
        s = fields.get('a');
        if (s != null && !s.isEmpty()) {
            final Matcher matcher = PAGES_PATTERN.matcher(s);
            if (matcher.find()) {
                s = matcher.group(1);
                if (s != null && !s.isEmpty()) {
                    book.setPages(s);
                }
            }
        }
        s = fields.get('c');
        if (s != null && !s.isEmpty()) {
            if ("ill. en coul.".equals(s)) {
                book.setColor(context.getString(R.string.book_color_full_color));
            }
        }
    }

    private void processAuthor(@NonNull final String row,
                               @NonNull final Book book) {
        // 700 .| $3 12066277 $o ISNI0000000120373451 $a Reza $b Yasmina $f 1959-.... $4 070
        // We're not parsing the dates as author resolving from wikidata will likely get more.
        final Map<Character, String> fields = parseUnimarcField(row);
        final String familyName = fields.get('a');
        if (familyName != null) {
            final String givenNames = fields.get('b');
            final Author author = new Author(familyName, givenNames);
            final String isniStr = fields.get('o');
            if (isniStr != null && isniStr.startsWith("ISNI")) {
                final ISNI isni = new ISNI(isniStr.substring(4));
                if (isni.isValid()) {
                    author.setIdentifierValue(Identifier.SID_ISNI, isni.getIsni());
                }
            }
            final String code = fields.get('4');
            if (code != null && !code.isEmpty()) {
                final Integer role = AUTHOR_CODES.get(code);
                if (role != null) {
                    author.setRole(role);
                }
            }
            book.add(author);
        }
    }

    @NonNull
    private Map<Character, String> parseUnimarcField(@NonNull final String row) {
        // 0..2: 3 digit tag
        // 4: space
        // 5: indicator
        // 6: indicator
        // 7: space
        // 8: first $

        final Map<Character, String> fields = new HashMap<>();
        final String[] subfields = row.substring(7).split("\\$");
        for (final String sub : subfields) {
            // one code, space, data
            if (sub.length() > 2) {
                fields.put(sub.charAt(0), sub.substring(1).strip());
            }
        }
        return fields;
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
     * @throws CoverStorageException on storage related failures
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

        final Element coversDiv = document.selectFirst("div.notice-detail > div.visuels");
        if (coversDiv == null) {
            return Optional.empty();
        }

        final Element img = coversDiv.selectFirst("img");
        if (img == null) {
            return Optional.empty();
        }

        String url = img.attr("src");
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        return saveImage(context, url, null, bookId, cIdx, null);
    }
}
