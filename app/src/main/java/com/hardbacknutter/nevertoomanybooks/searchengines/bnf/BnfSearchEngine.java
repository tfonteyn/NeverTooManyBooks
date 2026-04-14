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
import android.os.LocaleList;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.util.Arrays;
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
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Code;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISNI;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
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

/**
 * Bibliothèque nationale de France.
 * <p>
 * The unimarc format:
 * <a href="https://www.ifla.org/unimarc-updates/unimarc-bibliographic-format-manual-online-ed/">
 * English PDF manuel</a>
 * <a href="https://www.transition-bibliographique.fr/unimarc/manuel-unimarc-format-bibliographique/">
 * French tags online</a>
 */
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

    /** The "extend" typically contains the number of pages in {@code () / []} brackets. */
    private static final Pattern PAGES_PATTERN = Pattern.compile(".*[(\\[](\\d*).*?[)\\]].*");
    /** A 4-digit year. */
    private static final Pattern YEAR_PATTERN = Pattern.compile(".*?(\\d\\d\\d\\d).*?");

    private static final String URL_SUFFIX_UNIMARC = ".unimarc";

    private static final String ARK_12148 = "/ark:/12148/";

    private static final String SEARCH = "/rechercher.do?motRecherche=%1$s"
                                         + "&critereRecherche=0"
                                         + "&depart=0"
                                         + "&facetteModifiee=ok";

    private static final Map<String, Integer> AUTHOR_CODES = Map.ofEntries(
            Map.entry("000", AuthorRole.UNKNOWN),
            // Artist; overlaps with "440"
            Map.entry("040", AuthorRole.ARTIST),
            // Author
            Map.entry("070", AuthorRole.WRITER),
            // Author of afterword, postface, colophon, etc.
            Map.entry("075", AuthorRole.AFTERWORD),
            // Author of introduction, etc.
            Map.entry("080", AuthorRole.INTRODUCTION),
            // Collaborator
            Map.entry("205", AuthorRole.CONTRIBUTOR),
            // Editor
            Map.entry("340", AuthorRole.EDITOR),
            // Graphic technician
            Map.entry("410", AuthorRole.COLORIST),
            // Illustrator; overlaps with "040"
            Map.entry("440", AuthorRole.ARTIST),
            // Narrator
            Map.entry("550", AuthorRole.NARRATOR),
            // Other
            Map.entry("570", AuthorRole.CONTRIBUTOR),
            // Publishing director (Series editor)
            Map.entry("651", AuthorRole.EDITOR),
            // Scenarist (Author of a screenplay.)
            Map.entry("690", AuthorRole.WRITER),
            // Translator
            Map.entry("730", AuthorRole.TRANSLATOR),
            // Typographer
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

        final String url = getHostUrl() + ARK_12148 + externalId;
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

        String url = href;
        // sanity check - it normally does NOT have the protocol/site part
        if (url.startsWith("/")) {
            url = getHostUrl() + url;
        }
        final Document p = loadDocument(context, url, null);
        search(context, p, fetchCovers, book);
    }

    private void search(@NonNull final Context context,
                        @NonNull final Document pubDocument,
                        @NonNull final boolean[] fetchCovers,
                        @NonNull final Book book)
            throws SearchException, CredentialsException, CoverStorageException {
        // First get the unimarc page to easily parse the book data.
        // We have seen a suffix with the jsessionid; reconstruct as needed
        // https://catalogue.bnf.fr/ark:/12148/cb476077541;jsessionid=99...
        final String[] location = pubDocument.location().split(";");
        final String url = location[0] + URL_SUFFIX_UNIMARC
                           + (location.length > 1 && location[1] != null
                              ? ";" + location[1]
                              : "");
        final Document unimarcDocument = loadDocument(context, url, null);
        parse(context, pubDocument, unimarcDocument, fetchCovers, book);
    }

    @VisibleForTesting
    public void parse(@NonNull final Context context,
                      @NonNull final Document pubDocument,
                      @NonNull final Document unimarcDocument,
                      @NonNull final boolean[] fetchCovers,
                      @NonNull final Book book)
            throws SearchException, CredentialsException, CoverStorageException {

        parseUnimarc(context, unimarcDocument, book);

        authorResolverHelper.resolve(context, this, book);

        if (isCancelled()) {
            return;
        }

        // We should now have the isbn, go parse the cover
        if (fetchCovers[0]) {
            parseCovers(context, pubDocument, book.getIsbn(), 0).ifPresent(
                    fileSpec -> CoverFileSpecArray.setFileSpec(book, 0, fileSpec));
        }
    }

    /**
     * Parse the unimarc page document.
     * <p>
     * We're skipping these fields which we've encountered.
     * <pre>
     * 000
     * 001 RECORD IDENTIFIER
     * 020 NATIONAL BIBLIOGRAPHY NUMBER
     * 039 (bnf) Numéro de notice récupérée d'un ancien système BnF
     * 073 INTERNATIONAL ARTICLE NUMBER (EAN)
     * 100 GENERAL PROCESSING DATA
     * 102 COUNTRY OF PUBLICATION OR PRODUCTION
     * 105 CODED DATA FIELD: TEXTUAL LANGUAGE MATERIAL, MONOGRAPHIC
     * 106 CODED DATA FIELD: TEXTUAL RESOURCE – FORM
     * 181 CODED DATA FIELD: CONTENT FORM
     * 182 CODED DATA FIELD: MEDIA TYPE
     *
     * 205 EDITION STATEMENT
     *     the field values are unstructured
     *
     * 300 GENERAL NOTES
     *     unstructured
     * 304 NOTES PERTAINING TO TITLE AND STATEMENT OF RESPONSIBILITY
     *
     * 312 NOTES PERTAINING TO RELATED TITLES
     * 312 .. $a Autre forme de titre : De la colonisation aux indépendances
     *
     * 316 NOTE RELATING TO THE ITEM
     *
     * 321 EXTERNAL INDEXES/ABSTRACTS/REFERENCES NOTE
     * 321 .. $a Escales en littérature de jeunesse $c 2013
     *
     * 329 (bnf) is used to store critical reviews or analytical commentaries
     *     provided by La Joie par les livres
     *     (the BnF’s National Center for Children's Literature).
     *
     * 333 USERS/INTENDED AUDIENCE NOTE
     * 333 .. $a À partir de 3 ans $2 CNLJ $k Avis critique donné par le Centre national de la littérature pour la jeunesse
     *
     * 423 (links) ISSUED WITH
     * 461 (links) SET
     *
     * 510 PARALLEL TITLE PROPER
     *
     * 600 PERSONAL NAME USED AS SUBJECT
     * 600 .| $3 11908875 $a Jodorowsky $b Alexandro $f 1929-.... $2 rameau
     * -> on a book ABOUT Jodorowsky
     *
     * 606 TOPICAL NAME USED AS SUBJECT
     * 606 .. $3 11932417 $a Mariage $3 11940497 $x Rites et cérémonies $2 rameau
     * -> on a book ABOUT Mariage
     *
     * 608 FORM, GENRE OR PHYSICAL CHARACTERISTICS ACCESS POINT
     * 608 .. $a Bandes dessinées $2 CNLJ $k Avis critique donné par le Centre national de la littérature pour la jeunesse
     * 608 .. $a Albums $2 CNLJ $k Avis critique donné par le Centre national de la littérature pour la jeunesse
     *
     * 676 DEWEY DECIMAL CLASSIFICATION
     *
     * 686 OTHER CLASS NUMBERS
     * 686 .. $a 804 $2 Cadre de classement de la Bibliographie nationale française
     *
     * 801 ORIGINATING SOURCE
     * 801 .0 $a FR $b FR-751131015 $c 20100412 $g AFNOR $h FRBNF42177275000000X $2 intermrc
     *
     * 856 ELECTRONIC LOCATION AND ACCESS
     * 856 .2 $u 164608 $b Première de couverture
     *
     * 900+ not defined in the unimarc manual
     * </pre>
     *
     * @param context  Current context
     * @param document to parse
     * @param book     to update
     *
     * @throws SearchException on generic exceptions (wrapped) during search
     */
    private void parseUnimarc(@NonNull final Context context,
                              @NonNull final Document document,
                              @NonNull final Book book)
            throws SearchException {

        final Element element = document.selectFirst("div#ancreNotice");
        if (element == null) {
            return;
        }

        final Elements zones = element.select("div.zone");
        if (zones.isEmpty()) {
            return;
        }

        try {
            for (final Element zone : zones) {
                // For pure text, this is faster;
                // for special situations, we fall back to the zone element itself.
                final String text = zone.text();

                final String tag = text.substring(0, 3);

                switch (tag) {
                    case "003": {
                        // 003 PERSISTENT RECORD IDENTIFIER
                        // 003 http://catalogue.bnf.fr/ark:/12148/cb424392165
                        final String sid = parseSid(text);
                        if (sid != null) {
                            book.setIdentifierValue(Identifier.SID_BNF, sid);
                        }
                        break;
                    }
                    case "010":
                    case "011": {
                        processIsbnFormatAndPrice(context, text, book);
                        break;
                    }
                    case "101": {
                        processLanguage(text, book);
                        break;
                    }
                    case "200": {
                        processTitle(text, book);
                        break;
                    }
                    case "210":
                    case "214": {
                        processPublication(text, book);
                        break;
                    }
                    case "215": {
                        processPhysicalDescription(context, text, book);
                        break;
                    }
                    case "225":
                    case "410":
                    case "461": {
                        processSeries(text, book);
                        break;
                    }
                    case "330": {
                        processDescription(text, book);
                        break;
                    }
                    case "454": {
                        processTranslation(text, book);
                        break;
                    }
                    case "700":
                    case "701":
                    case "702":
                    case "710":
                    case "711": {
                        processAuthor(zone, text, book);
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

    /**
     * {@code "010"} INTERNATIONAL STANDARD BOOK NUMBER (ISBN).
     * {@code "011"} ISSN.
     * <ul>
     * <li>{@code $a} Number (ISBN or ISSN)</li>
     * <li>{@code $b} Qualification</li>
     * <li>{@code $d} Terms of Availability and/or Price</li>
     * </ul>
     *
     * @param context Current context
     * @param text    to parse
     * @param book    to update
     */
    private void processIsbnFormatAndPrice(@NonNull final Context context,
                                           @NonNull final String text,
                                           @NonNull final Book book) {
        String s;
        final Map<Character, String> fields = parseUnimarcField(text);
        // Only grab the first should there be multiple
        if (!book.hasIsbn()) {
            s = ISBN.cleanText(fields.get('a'));
            if (!s.isEmpty()) {
                book.setIsbn(s);
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
                final LocaleList userLocales = context.getResources().getConfiguration()
                                                      .getLocales();
                final List<Locale> allLocales = LocaleListUtils.asList(SITE_LOCALE, userLocales);
                final MoneyParser parser = new MoneyParser(SITE_LOCALE, allLocales);
                addPriceListed(context, parser, s, null, book);
            }
        }
    }

    /**
     * {@code "101"} LANGUAGE OF THE RESOURCE.
     * <ul>
     * <li>{@code $a} Language of Text</li>
     * <li>{@code $c} Language of Original Work</li>
     * </ul>
     *
     * @param text to parse
     * @param book to update
     */
    private void processLanguage(@NonNull final String text,
                                 @NonNull final Book book) {
        String s;
        final Map<Character, String> fields = parseUnimarcField(text);
        s = fields.get('a');
        if (s != null && !s.isEmpty()) {
            book.setLanguage(s);
        }
        s = fields.get('c');
        if (s != null && !s.isEmpty()) {
            book.setTranslatedFromLanguage(s);
        }
    }

    /**
     * {@code "200"} TITLE AND STATEMENT OF RESPONSIBILITY.
     * <p>
     * Typically, we only need {@code $a} for the title.
     * <ul>
     * <li>{@code $a} Title Proper</li>
     * <li>{@code $b} General Material Designation</li>
     * <li>{@code $e} Other Title Information</li>
     * <li>{@code $f} First Statement of Responsibility</li>
     * <li>{@code $g} Subsequent Statement of Responsibility</li>
     * <li>{@code $h} Number of a Part</li>
     * <li>{@code $i} Name of a Part</li>
     * </ul>
     * Some more complicated/extended examples.
     * <pre>
     *   $a Nouvelles
     *   $b Texte imprimé
     *   $h Tome 1
     *   $i 1947-1953
     *   $f Philip K. Dick
     *   $g trad. rev. et harmonisées par Hélène Collon
     *   $g avant-propos d'Emmanuel Carrère
     * </pre>
     * <pre>
     *   $a Afrique noire occidentale et centrale
     *   $h Tome 3
     *   $i De la colonisation aux indépendances
     *   $e 1945-1960
     *   $h 1
     *   $i Crise du système colonial et capitalisme monopoliste d'État
     * </pre>
     *
     * @param text to parse
     * @param book to update
     */
    private void processTitle(@NonNull final String text,
                              @NonNull final Book book) {
        final Map<Character, String> fields = parseUnimarcField(text);
        final String s = fields.get('a');
        if (s != null && !s.isEmpty()) {
            book.setTitle(s);
        }
    }

    /**
     * {@code "210"}  PUBLICATION, DISTRIBUTION, ETC.,<br/>
     * {@code "214"} PRODUCTION, PUBLICATION, DISTRIBUTION, MANUFACTURE STATEMENTS.
     * <ul>
     * <li>{@code $c} Name of Publisher, Distributor, etc.</li>
     * <li>{@code $d} Date of Publication, Distribution, etc.</li>
     * </ul>
     *
     * @param text to parse
     * @param book to update
     */
    private void processPublication(@NonNull final String text,
                                    @NonNull final Book book) {
        String s;
        final Map<Character, String> fields = parseUnimarcField(text);
        s = fields.get('c');
        if (s != null && !s.isEmpty()) {
            book.add(Publisher.from(s));
        }
        // Date of Publication is free-form ... best effort try to find a year
        s = parseYear(fields.get('d'));
        if (s != null) {
            book.setPublicationDate(s);
        }
    }

    /**
     * {@code "215"} PHYSICAL DESCRIPTION.
     * <ul>
     * <li>{@code $a} Specific Material Designation and Extent</li>
     * <li>{@code $c} Other Physical Details</li>
     * </ul>
     *
     * @param context Current context
     * @param text    to parse
     * @param book    to update
     */
    private void processPhysicalDescription(@NonNull final Context context,
                                            @NonNull final String text,
                                            @NonNull final Book book) {
        String s;
        final Map<Character, String> fields = parseUnimarcField(text);
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
            // This is gamble.... there is no structure.
            // We simply look for "illustration" and "couleur"
            if (s.contains("ill") && s.contains("coul")) {
                book.setColor(context.getString(R.string.book_color_full_color));
            }
        }
    }

    /**
     * {@code "225"} SERIES.
     * {@code "410"} SERIES.
     * {@code "461"} SET
     * <ul>
     * <li>{@code $a} Title</li>
     * <li>{@code $v} Volume Designation</li>
     * </ul>
     *
     * @param text to parse
     * @param book to update
     */
    private void processSeries(@NonNull final String text,
                               @NonNull final Book book) {
        final Map<Character, String> fields = parseUnimarcField(text);
        final String title = fields.get('a');
        if (title != null && !title.isEmpty()) {
            final String nr = fields.get('v');
            final Series series = Series.from(title, nr);
            // As we parse multiple fields for the series,
            // we need to check if it's not already present.
            // We could still end up with duplicates though.
            // Example: "Quarto" and "Quarto (Paris)".
            if (book.getSeries().stream().noneMatch(series1 -> series1.equals(series))) {
                book.add(series);
            }
        }
    }

    /**
     * {@code "330"} SUMMARY OR ABSTRACT.
     * <ul>
     * <li>{@code $a} Text of Note</li>
     * <li>{@code $u} Uniform Resource Identifier (URI) </li>
     * </ul>
     *
     * @param text to parse
     * @param book to update
     */
    private void processDescription(@NonNull final String text,
                                    @NonNull final Book book) {
        final Map<Character, String> fields = parseUnimarcField(text);
        String s = fields.get('a');
        if (s != null && !s.isEmpty()) {
            book.setDescription(s);
        } else {
            // Only grab the url if there was no text
            s = fields.get('u');
            if (s != null && !s.isEmpty()) {
                book.setDescription(s);
            }
        }
    }

    /**
     * {@code "454"} TRANSLATION OF.
     * <ul>
     * <li>{@code $d} Date of Publication</li>
     * <li>{@code $t} Title</li>
     * </ul>
     *
     * @param text to parse
     * @param book to update
     */
    private void processTranslation(@NonNull final String text,
                                    @NonNull final Book book) {
        final Map<Character, String> fields = parseUnimarcField(text);
        String s = fields.get('t');
        if (s != null && !s.isEmpty()) {
            book.setTranslatedFromTitle(s);
        }
        // Date of Publication is free-form ... best effort try to find a year
        s = parseYear(fields.get('d'));
        if (s != null) {
            book.setFirstPublicationDate(s);
        }
    }

    /**
     * {@code "700"} PERSONAL NAME – PRIMARY RESPONSIBILITY.
     * {@code "701"} PERSONAL NAME – ALTERNATIVE RESPONSIBILITY.
     * {@code "702"} PERSONAL NAME – SECONDARY RESPONSIBILITY.
     * {@code "710"} CORPORATE BODY NAME – PRIMARY RESPONSIBILITY.
     * {@code "711"} CORPORATE BODY NAME – ALTERNATIVE RESPONSIBILITY.
     * <ul>
     * <li>{@code $a} Entry Element</li>
     * <li>{@code $b} Part of Name Other than Entry Element</li>
     * <li>{@code $o} International Standard Identifier for the Name</li>
     * <li>{@code $3} Authority Record Identifier or Standard Number </li>
     * <li>{@code $4} Relator Code</li>
     * </ul>
     *
     * @param html to parse
     * @param text to parse
     * @param book to update
     */
    private void processAuthor(@NonNull final Element html,
                               @NonNull final String text,
                               @NonNull final Book book) {
        // 700 .| $3 12066277 $o ISNI0000000120373451 $a Reza $b Yasmina $f 1959-.... $4 070
        // We're not parsing the dates (which are free-form) as resolving
        // from wikidata will likely get better/more info.
        final Map<Character, String> fields = parseUnimarcField(text);
        final String familyName = fields.get('a');
        if (familyName != null) {
            final String givenNames = fields.get('b');
            final Author author = new Author(familyName, givenNames);
            final String isniStr = fields.get('o');
            if (isniStr != null && isniStr.startsWith("ISNI")) {
                final Code isni = new ISNI(isniStr.substring(4));
                if (isni.isValid()) {
                    author.setIdentifierValue(Identifier.SID_ISNI, isni.asText());
                }
            }
            final String code = fields.get('4');
            if (code != null && !code.isEmpty()) {
                final Integer role = AUTHOR_CODES.get(code);
                if (role != null) {
                    author.setRole(role);
                }
            }

            // The $3 field has a url, we must parse the zone element.
            // <div class="zone"><span class="etiquetteMarc">700 </span>
            // <span class="fixe">.|</span><span class="etiquetteMarc"> $3 </span>
            // <a href="/ark:/12148/cb12464370b"><span class="fixe">12464370</span></a>
            //
            // 12464370 is the "pure" number
            // cb12464370b is the full number, where 'b' is a checksum
            // Note we could also just parse the text, prefix "cb" and calculate the checksum.
            //
            // see https://arks.org/resources/noid/
            final Element a = html.selectFirst("a");
            if (a != null) {
                final String sid = parseSid(a.attr("href"));
                if (sid != null) {
                    author.setIdentifierValue(Identifier.SID_BNF, sid);
                }
            }
            book.add(author);
        }
    }

    @Nullable
    private String parseSid(@NonNull final String text) {
        // Sanity check that this IS an 'ark' url.
        if (text.contains(ARK_12148)) {
            final int lastIndex = text.lastIndexOf('/');
            if (lastIndex > 0) {
                final String sid = text.substring(lastIndex + 1);
                if (!sid.isBlank()) {
                    return sid;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("TypeMayBeWeakened")
    @Nullable
    private String parseYear(@Nullable final String text) {
        if (text != null && !text.isEmpty()) {
            final Matcher matcher = YEAR_PATTERN.matcher(text);
            if (matcher.find()) {
                final String s = matcher.group(1);
                if (s != null && !s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    @NonNull
    private Map<Character, String> parseUnimarcField(@NonNull final String text) {
        // 0..2: 3 digit tag
        // 4: space
        // 5: indicator
        // 6: indicator
        // 7: space
        // 8: first $
        final String[] fields = text.substring(7).split("\\$");
        return Arrays.stream(fields)
                     // Sanity check
                     .filter(sub -> sub.length() > 2)
                     .collect(Collectors.toMap(
                             // Field name
                             sub -> sub.charAt(0),
                             // Field value
                             sub -> sub.substring(1).strip(),
                             (a, b) -> b));
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
