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

package com.hardbacknutter.nevertoomanybooks.searchengines.dnb;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.DateParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.NumberParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.Money;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.Tag;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * A MARC21 xml parser, with DNB specific handling.
 * If we need to parse other sources, we'll need to split/subclass this class.
 * <p>
 * IMPORTANT: do NOT combine multiple tags in one select if the order is important!
 *
 * @see <a href="https://www.loc.gov/marc/bibliographic/">marc21</a>
 */
class DnbBookParser {

    private static final Pattern DE_PRICE_PATTERN =
            Pattern.compile("EUR\\s*(\\d+\\.\\d{2})\\s*\\(DE\\)");
    private static final Pattern AT_PRICE_PATTERN =
            Pattern.compile("EUR\\s*(\\d+\\.\\d{2})\\s*\\(AT\\)");
    private static final Pattern CH_PRICE_PATTERN =
            Pattern.compile("(CHF\\s*\\d+\\.\\d{2})");

    private static final String SUBFIELD_CODE_0 = "subfield[code='0']";
    private static final String SUBFIELD_CODE_2 = "subfield[code='2']";
    private static final String SUBFIELD_CODE_4 = "subfield[code='4']";
    private static final String SUBFIELD_CODE_A = "subfield[code='a']";
    private static final String SUBFIELD_CODE_B = "subfield[code='b']";
    private static final String SUBFIELD_CODE_C = "subfield[code='c']";
    private static final String SUBFIELD_CODE_D = "subfield[code='d']";
    private static final String SUBFIELD_CODE_H = "subfield[code='h']";
    private static final String SUBFIELD_CODE_T = "subfield[code='t']";
    private static final String SUBFIELD_CODE_V = "subfield[code='v']";
    private static final String SUBFIELD_CODE_W = "subfield[code='w']";
    private static final String SUBFIELD_CODE_X = "subfield[code='x']";

    private static final int CF_008_FREQUENCY = 18;
    private static final int CF_008_REGULARITY = 19;
    private static final int CF_008_TYPE_OF_CONTINUING_RESOURCE = 21;

    @NonNull
    private final Context context;
    @NonNull
    private final Document document;
    @NonNull
    private final Book book;
    private final List<Identifier.Value> ivs = new ArrayList<>();

    @NonNull
    private final Locale userLocale;
    private final DnbParser dnbParser;

    /**
     * Constructor.
     *
     * @param context  Current context
     * @param document to parse
     * @param book     to populate
     */
    DnbBookParser(@NonNull final Context context,
                  @NonNull final Document document,
                  @NonNull final Book book) {
        this.context = context;
        this.document = document;
        this.book = book;

        dnbParser = new DnbParser(document);

        userLocale = context.getResources().getConfiguration().getLocales().get(0);
    }

    /**
     * DNB identifier.
     */
    void sid() {
        final Identifier.Value value = dnbParser.sid();
        if (value != null) {
            ivs.add(value);
        }
    }

    /**
     * The ISBN and list-price for books.
     * <p>
     * {@code 020}  - International Standard Book Number (R)
     */
    void isbn() {
        final Element tag = document.selectFirst("datafield[tag='020']");
        if (tag == null) {
            return;
        }

        // $a - International Standard Book Number (NR)
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            book.setRawProductCode(ISBN.cleanText(DnbParser.normalise(a)));
        }
        // $c - Terms of availability (NR)
        final Element c = tag.selectFirst(SUBFIELD_CODE_C);
        if (c != null) {
            // Festeinband : EUR 17.00 (DE), EUR 17.50 (AT), CHF 24.50 (freier Preis)
            final Money price = parseListPrice(c);
            if (price != null && !price.isZero()) {
                book.setPriceListed(price);
            }
        }
    }

    @Nullable
    private Money parseListPrice(@NonNull final Element element) {
        final String priceString = DnbParser.normalise(element);
        if (priceString.isBlank()) {
            return null;
        }

        final Pattern pattern;
        final Currency currency;
        switch (userLocale.getCountry()) {
            case "AT": {
                pattern = AT_PRICE_PATTERN;
                currency = Money.EURO;
                break;
            }
            case "CH": {
                pattern = CH_PRICE_PATTERN;
                currency = Currency.getInstance(userLocale);
                break;
            }
            default: {
                pattern = DE_PRICE_PATTERN;
                // hardcoded to EURO, don't use the userLocale
                currency = Money.EURO;
                break;
            }
        }

        final Matcher matcher = pattern.matcher(priceString);
        if (matcher.find()) {
            final String s = matcher.group(1);
            if (s == null) {
                return null;
            }
            try {
                // The values use a dot as decimal separator.
                // Don't use our RealNumberParser as that would use a ',' for germany etc...
                final double val = Double.parseDouble(s);
                return new Money(BigDecimal.valueOf(val), currency);
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }

        return null;
    }

    /**
     * The ISSN for Periodicals.
     */
    void issn() {
        final String issn = dnbParser.issn();
        if (issn != null) {
            book.setRawProductCode(issn);
        }
    }

    /**
     * The EAN-13 product code.
     * <p>
     * Overwrites any previously parse product-code.
     */
    void ean13() {
        final String ean13 = dnbParser.ean13();
        if (ean13 != null) {
            // overwrite !
            book.setRawProductCode(ean13);
        }
    }

    /**
     * Parse and <strong>add</strong> Identifiers found.
     */
    void identifiers() {
        ivs.addAll(dnbParser.identifiers());
    }

    /**
     * Language and original-language.
     */
    void languages() {
        // 041 - Language Code (R)
        final Element tag = document.selectFirst("datafield[tag='041']");
        if (tag == null) {
            return;
        }
        // $a - Language code of text or sound track (R)
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            book.setLanguage(DnbParser.normalise(a));
        }
        // $h - Language code of original (R)
        final Element h = tag.selectFirst(SUBFIELD_CODE_H);
        if (h != null) {
            book.setTranslatedFromLanguage(DnbParser.normalise(h));
        }
    }

    void originalTitle() {
        final String title = dnbParser.originalTitle();
        if (title != null) {
            book.setTranslatedFromTitle(title);
        }
    }

    void title() {
        final String title = dnbParser.title(context);
        if (title != null) {
            book.setTitle(title);
        }
    }

    /**
     * Parse and <strong>add</strong> Authors found.
     */
    void authors() {
        dnbParser.authors().forEach(book::add);
    }

    /**
     * Parse and <strong>add</strong> Publishers found.
     */
    void publishers() {
        final Pair<List<Publisher>, PartialDate> pubData = dnbParser.publishers();
        if (!pubData.first.isEmpty()) {
            pubData.first.forEach(book::add);
        }
        if (!PartialDate.NOT_SET.equals(pubData.second)) {
            book.setPublicationDate(pubData.second);
        }
    }

    void description() {
        final String description = dnbParser.description();
        if (description != null) {
            book.setDescription(description);
        }
    }

    /**
     * Parse pages, format and colour.
     * <p>
     * {@code 300} - Physical Description (R)
     */
    void physicalDescription() {
        final Element tag = document.selectFirst("datafield[tag='300']");
        if (tag == null) {
            return;
        }
        // $a - Extent (R)
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a == null) {
            return;
        }
        final String pages = DnbParser.normalise(a).split(" ")[0];
        try {
            final int p = NumberParser.toInt(pages);
            book.setPages(p);
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        // $b - Other physical details
        final Element b = tag.selectFirst(SUBFIELD_CODE_B);
        if (b != null) {
            // there may be more text snippets we can check for.
            final String text = DnbParser.normalise(b);

            if (text.contains("Online-Ressource")) {
                book.setFormat(context.getString(R.string.book_format_ebook));
            }

            if (text.contains("farb. Ill.")
                || text.contains("illus. in color")
                || text.contains("Farbig illustriert")
                || text.contains("Mit farbigen Zeichnungen")) {
                book.setColor(context.getString(R.string.book_color_full_color));
            }
        }
    }

    /**
     * Check if this is an eBook.
     * If so, the format as set in {@link #physicalDescription()} will be overwritten.
     */
    void ebook() {
        @Nullable
        final Boolean eBook = dnbParser.isEBook();
        if (eBook == null) {
            return;
        }
        if (eBook) {
            // overwrite any previous code from tag 300.
            book.setFormat(context.getString(R.string.book_format_ebook));
        }
    }

    /**
     * Parse and <strong>add</strong> Series found.
     */
    void series() {
        final List<Series> series = dnbParser.series();
        if (!series.isEmpty()) {
            series.forEach(book::add);
        }
    }

    /**
     * Parse and <strong>set</strong> genre tags as {@link Tag}s.
     */
    void genreTags() {
        final Set<Tag> genreTags = dnbParser.genreTags();
        if (!genreTags.isEmpty()) {
            book.setTags(genreTags);
        }
    }

    /**
     * Check for, and parse Periodical data if found.
     * No-op if the book does not have an ISSN as product-code.
     */
    void periodicals() {
        SearchEngineUtils.ensurePeriodicalSeries(book).ifPresent(this::periodicals);
    }

    /**
     * Parse control field 008; this is only valid for ISSN search.
     * <ul>
     * <li>Position 18/19: frequency.</li>
     * <li>
     * Position 21: Type of continuing resource
     * <pre>
     *    # - None of the following
     *    a - Activity report
     *    d - Updating database
     *    g - Magazine
     *    h - Blog
     *    i - Serial zine
     *    j - Journal
     *    l - Updating loose-leaf
     *    m - Monographic series
     *    n - Newspaper
     *    p - Periodical
     *    q - Serial podcast
     *    r - Repository
     *    s - Newsletter
     *    t - Directory
     *    w - Updating Web site
     *    | - No attempt to code
     * </pre>
     * </li>
     * <li>Position 35-37 provides the language, but so does tag 041;
     *     we're ONLY using the latter</li>
     * </ul>
     *
     * @param series to update
     */
    private void periodicals(@NonNull final Series series) {

        final Element tag = document.selectFirst("controlfield[tag='008']");
        if (tag == null) {
            return;
        }
        final String codedData = DnbParser.normalise(tag);
        // sanity check
        if (codedData.length() != 40) {
            return;
        }

        switch (codedData.charAt(CF_008_TYPE_OF_CONTINUING_RESOURCE)) {
            // n - Newspaper
            case 'n': {
                book.setFormat(context.getString(R.string.book_format_newspaper));
                break;
            }
            case 'j': {
                book.setFormat(context.getString(R.string.book_format_journal));
                break;
            }
            // g - Magazine
            // i - Serial zine
            // p - Periodical
            case 'g':
            case 'i':
            case 'p': {
                book.setFormat(context.getString(R.string.book_format_periodical));
                break;
            }
        }

        final PublicationFrequency frequency = PublicationFrequency.fromMarc21(
                codedData.charAt(CF_008_FREQUENCY),
                codedData.charAt(CF_008_REGULARITY));
        if (frequency.getType() != PublicationFrequency.Type.Unknown) {
            series.setPublicationFrequency(frequency);
        }
    }

    /**
     * Finish the parsing process.
     *
     * @param searchedCode to code which the user was searching for.
     *                     It will be set on the book as its product code,
     *                     if the the latter was not retrieved during the search
     */
    void finish(@Nullable final String searchedCode) {
        ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }

        if (!book.hasProductCode() && searchedCode != null) {
            book.setRawProductCode(searchedCode);
        }
    }


    // the core parser logic, which will return values.
    static class DnbParser {

        // Last updated 2026-06-21.
        // These are fixed-case/as-is as they came from the SRU official list.
        private static final Map<String, String> IDENTIFIER_MAPPING = Map.ofEntries(
                // Inline; i.e. as a "(key)value" subfield.
                Map.entry("DE-101", Identifier.SID_DNB),
                Map.entry("OCoLC", Identifier.SID_OCLC),
                Map.entry("Be", Identifier.SID_KBR),
                Map.entry("NL-HaKB", Identifier.SID_KBNL),
                Map.entry("DLC", Identifier.SID_LCCN),
                Map.entry("US-dlc", Identifier.SID_LCCN),
                Map.entry("PoLiBN", Identifier.SID_PORBASE),
                Map.entry("SE-LIBR", Identifier.SID_LIBRIS),
                // tag=024, ind1=7, where $2 contains the key and $a the value
                Map.entry("doi", Identifier.SID_DOI),
                Map.entry("urn", Identifier.SID_URN),
                Map.entry("wikidata", Identifier.SID_WIKIDATA)
        );

        private static final Map<String, Integer> AUTHOR_ROLE_MAPPING = Map.ofEntries(
                Map.entry("aut", AuthorRole.WRITER),
                Map.entry("aus", AuthorRole.ORIGINAL_SCRIPT_WRITER),
                Map.entry("wpr", AuthorRole.FOREWORD),
                Map.entry("aft", AuthorRole.AFTERWORD),
                Map.entry("trl", AuthorRole.TRANSLATOR),
                Map.entry("aui", AuthorRole.INTRODUCTION),
                Map.entry("edt", AuthorRole.EDITOR),
                Map.entry("ctb", AuthorRole.CONTRIBUTOR),
                Map.entry("cov", AuthorRole.COVER_ARTIST),
                Map.entry("nrt", AuthorRole.NARRATOR),
                Map.entry("art", AuthorRole.ARTIST),
                Map.entry("ink", AuthorRole.INKING),
                Map.entry("clr", AuthorRole.COLORIST),
                Map.entry("ltr", AuthorRole.LETTERING)
        );

        /**
         * Suffixes we try to detect and remove from the title field.
         * Although we've not found others... it's presumed there are more.
         * Will be added when needed.
         */
        private static final String[] TITLE_SUFFIXES = {
                ": Roman",
                ": Thriller",
                ": Psychothriller",
                ": Kriminalroman",
                // We've seen this suffix without the ": " as well.
                "Kriminalroman"
        };

        /** These need to be removed from all strings we read. */
        private static final Pattern CONTROL_CHARACTERS_PATTERN = Pattern.compile(
                "[\\x80-\\x9F\\u02DC\\u0153\\u2018\\u2019]");

        private static final Pattern IDENT_PATTERN = Pattern.compile("^\\(([^)]+)\\)(.+)$");
        private static final Pattern DOT_PATTERN = Pattern.compile("\\.");
        private static final Pattern PATTERN_BAR = Pattern.compile("\\|");
        private static final Pattern PATTERN_SLASH = Pattern.compile("/");

        private static final Pattern TRAILING_AUTHOR_LASTNAME_PREFIX_PATTERN =
                Pattern.compile("(?i)\\b(von|zu|von\\s+und\\s+zu|vom|zum)$");
        private static final Pattern DESC_PREFIX_PATTERN =
                Pattern.compile("(?i)^(Inhalt|Rezension|Zusammenfassung):\\s*");

        /**
         * Matches an optional {@code YYYY} or {@code DD.MM.YYYY}.
         */
        private static final String DATE_REGEX = "([0-9]{4}|[0-9]{2}\\.[0-9]{2}\\.[0-9]{4})?";
        /**
         * Matches an optional date/year, a mandatory hyphen, and an optional date/year.
         * Either of the pre-dash, or post-dash parts can be missing.
         * Group 1 = Birth
         * Group 2 = Death
         */
        private static final Pattern LIFESPAN_PATTERN = Pattern.compile(
                "^" + DATE_REGEX + "-" + DATE_REGEX + "$");

        @NonNull
        private final Document document;

        private final DateParser<PartialDate> partialDateParser = new PartialDateParser();

        /**
         * Constructor.
         *
         * @param document to parse
         */
        DnbParser(@NonNull final Document document) {
            this.document = document;
        }

        /**
         * Helper method.
         * <p>
         * Some elements can contain MARC21 control characters which need cleaning up.
         * e.g. {@code <subfield code="a">&#152;Der&#156; Spiegel online Themen</subfield>}
         *
         * @param element to normalize
         *
         * @return clean string
         */
        @NonNull
        static String normalise(@NonNull final Element element) {
            final String s = CONTROL_CHARACTERS_PATTERN.matcher(element.wholeText())
                                                       .replaceAll("");
            return Normalizer.normalize(s, Normalizer.Form.NFC)
                             .strip();
        }

        /**
         * Special rules in german where names with a "von" etc are reordered.
         * {@code Humboldt, Dorothee von}
         * To be backwards (and worldwide) compatible,
         * rearrange this to {@code "von Humboldt" + "Dorothee"}
         *
         * @param element to parse
         *
         * @return author found
         */
        @NonNull
        private static Author normaliseAuthor(@NonNull final Element element) {
            final String text = normalise(element);
            if (text.contains(",")) {
                final String[] parts = text.split(",");
                if (parts.length == 1) {
                    return Author.from(text);
                }

                final String rawLastName = parts[0].strip();
                final String rawFirstName = parts[1].strip();
                final Matcher matcher = TRAILING_AUTHOR_LASTNAME_PREFIX_PATTERN
                        .matcher(rawFirstName);
                if (matcher.find()) {
                    final String particle = matcher.group(1);
                    if (particle != null) {
                        // Move particle to the front of the last name
                        final String lastName = particle + " " + rawLastName;
                        // Strip the particle
                        final String givenNames = rawFirstName
                                .substring(0, matcher.start()).strip();

                        return new Author(lastName, givenNames);
                    }
                }
            }
            // fallback
            return Author.from(text);
        }

        /**
         * The title data can have a suffix stating what type of book it is.
         * This is not desirable, and we cut it down.
         * <p>
         * 978-3-453-32189-2
         * Nemesis : Roman
         * 978-3-426-22668-1
         * Totholz : Was vergraben ist, ist nicht vergessen. Kriminalroman
         * 978-3-453-44215-3
         * Wenn sie wüsste : Thriller
         * 978-3-96584-423-0
         * Der Glukose-Masterplan
         *
         * @param title to clean
         *
         * @return cleansed title
         */
        @NonNull
        private static String cleanTitle(@NonNull final String title) {
            String text = title;

            if (text.contains("/")) {
                text = PATTERN_SLASH.split(text)[0];
            }
            if (text.contains("|")) {
                text = PATTERN_BAR.split(text)[0];
            }

            text = SearchEngineUtils.cleanName(text);
            for (final String suffix : TITLE_SUFFIXES) {
                if (text.endsWith(suffix)) {
                    text = text.substring(0, text.length() - suffix.length()).strip();
                }
            }

            return text;
        }

        /**
         * Reformat the site "DD.MM.YYYY" format to "YYYY-MM-DD".
         *
         * @param date to reformat
         *
         * @return iso formatted date
         */
        @Nullable
        private String normaliseDate(@Nullable final String date) {
            if (date == null || !date.contains(".")) {
                return date;
            }
            final String[] split = DOT_PATTERN.split(date);
            if (split.length == 3) {
                return split[2] + '-' + split[1] + '-' + split[0];
            }
            return date;
        }

        /**
         * Parse the DNB identifier.
         * <p>
         * {@code 001} - Control Number (NR)
         *
         * @return identifier.
         */
        @Nullable
        Identifier.Value sid() {
            final Element cf = document.selectFirst("controlfield[tag='001']");
            if (cf == null) {
                return null;
            }

            return new Identifier.Value(Identifier.SID_DNB, normalise(cf));
        }

        /**
         * Parse the EAN-13 product code.
         * <p>
         * {@code 024} - Other Standard Identifier (R)
         * {@code ind1='3'} - International Article Number
         *
         * @return ean-13
         */
        @Nullable
        String ean13() {
            // $a - Standard number or code (NR)
            final Element tag = document.selectFirst(
                    "datafield[tag='024'][ind1='3'] > subfield[code='a']");
            if (tag == null) {
                return null;
            }
            return ISBN.cleanText(normalise(tag));
        }

        /**
         * The ISSN for Periodicals.
         * <p>
         * {@code 022} - International Standard Serial Number (R)
         *
         * @return ISSN
         */
        @Nullable
        String issn() {
            // $a - International Standard Serial Number (NR)
            final Element tag = document.selectFirst("datafield[tag='022'] > subfield[code='a']");
            if (tag == null) {
                return null;
            }
            return ISBN.cleanText(normalise(tag));
        }

        /**
         * Parse Identifiers.
         * <ul>
         *     <li>{@code 035} - System Control Number (R)</li>
         *     <li>{@code 024} - Other Standard Identifier (R)</li>
         * </ul>
         * <p>
         * Identifiers from tag {@code 035} are <strong>always added/mapped</strong>.
         * Tag {@code 024} identifiers are only added/mapped if we recognise them.
         *
         * @return list; may contain duplicates.
         */
        @NonNull
        List<Identifier.Value> identifiers() {
            // $a - System control number
            final List<Element> fields = document.select(
                    "datafield[tag='035'] > subfield[code='a']");

            final List<Identifier.Value> ivs = parseInlineIdentifiers(fields, null);

            ivs.addAll(parseTag024Identifiers());

            return ivs;
        }

        /**
         * Parse authors.
         * <ul>
         *     <li>{@code 100} - Main Entry-Personal Name (NR)</li>
         *     <li>{@code 700} - Added Entry-Personal Name (R)</li>
         * </ul>
         *
         * @return list
         */
        @NonNull
        List<Author> authors() {
            return authors("100", "700");
        }

        /**
         * Parse the given author tags.
         * <ul>
         *     <li>{@code 100} - Main Entry-Personal Name (NR)</li>
         *     <li>{@code 700} - Added Entry-Personal Name (R)</li>
         * </ul>
         *
         * @param tagNrs to parse
         *
         * @return list
         */
        @NonNull
        List<Author> authors(@NonNull final String... tagNrs) {

            final List<Author> authors = new ArrayList<>();

            for (final String tagNr : tagNrs) {
                final Collection<Element> tags = document.select(
                        "datafield[tag='" + tagNr + "']");
                for (final Element tag : tags) {
                    // $a - Personal name (NR)
                    final Element a = tag.selectFirst(SUBFIELD_CODE_A);
                    // Sanity check
                    if (a == null) {
                        continue;
                    }

                    final Author author = normaliseAuthor(a);

                    // Author role as used for books.
                    // We could also look at $e - Relator term (R),
                    // but this will have to do for now.
                    // $4 - Relationship (R)
                    final Elements s4s = tag.select(SUBFIELD_CODE_4);
                    for (final Element s4 : s4s) {
                        final String roleStr = normalise(s4);
                        //noinspection DataFlowIssue
                        final int role = AUTHOR_ROLE_MAPPING
                                .getOrDefault(roleStr, AuthorRole.UNKNOWN);
                        author.addRole(role);
                    }

                    final List<Identifier.Value> ivs = parseAuthorIdentifiers(tag);
                    if (!ivs.isEmpty()) {
                        ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
                        author.addIdentifiers(ivs);
                    }

                    // Birth and Death dates.
                    // $d - Dates associated with a name (NR)
                    final Pair<String, String> dates = parseAuthorDates(tag);
                    author.setBirthDate(dates.first);
                    author.setDeathDate(dates.second);

                    @Nullable
                    final Author realAuthor = authorTag500();
                    author.setRealAuthor(realAuthor);

                    authors.add(author);
                }
            }
            return authors;
        }

        /**
         * Parse publishers.
         * <ul>
         *     <li>264 - Production, Publication, Distribution, Manufacture,
         *         and Copyright Notice (R)</li>
         *     <li>710 - Added Entry-Corporate Name (R)</li>
         * </ul>
         *
         * @return list of publisher and the publication date.
         *         The list can be empty, and the date can be {@link PartialDate#NOT_SET}.
         *         Neither will be {@code null}.
         */
        @NonNull
        Pair<List<Publisher>, PartialDate> publishers() {
            final List<Publisher> publishers = new ArrayList<>();
            PartialDate date = PartialDate.NOT_SET;

            // 264 - Production, Publication, Distribution, Manufacture, and Copyright Notice (R)
            final Element primaryTag = document.selectFirst("datafield[tag='264']");
            if (primaryTag != null) {
                // $b - Name of producer, publisher, distributor, manufacturer (R)
                final Element b = primaryTag.selectFirst(SUBFIELD_CODE_B);
                @Nullable
                final Publisher publisher;
                if (b != null) {
                    publisher = Publisher.from(normalise(b));
                } else {
                    // $a - Place of production, publication, distribution, manufacture (R)
                    final Element a = primaryTag.selectFirst(SUBFIELD_CODE_A);
                    if (a != null) {
                        // It's unlikely we'd have a place without a name...
                        publisher = Publisher.from(normalise(a));
                    } else {
                        publisher = null;
                    }
                }

                if (publisher != null) {
                    // $c - Date of production, publication, ... (R)
                    final Element c = primaryTag.selectFirst(SUBFIELD_CODE_C);
                    if (c != null) {
                        // Dates are not uniform; we've seen:
                        // 01/2023
                        // Juni 2024
                        // 2014
                        // [2023]
                        // to be released in November 2024"   => date parsing will fail / ignored
                        String dateStr = normalise(c);
                        // Handle "[text]" with text a minimum of 4 characters, i.e. a year
                        if (dateStr.length() > 5
                            && dateStr.startsWith("[")
                            && dateStr.endsWith("]")) {
                            dateStr = dateStr.substring(1, dateStr.length() - 1);
                        }
                        date = partialDateParser.parse(dateStr, Locale.GERMAN)
                                                .orElse(PartialDate.NOT_SET);
                    }
                    publishers.add(publisher);
                }
            }

            // 710 - Added Entry-Corporate Name (R)
            final Elements tags = document.select("datafield[tag='710']");
            for (final Element tag : tags) {
                // $4 - Relationship (R)
                final Element s4 = tag.selectFirst(SUBFIELD_CODE_4);
                // only grab actual publisher "pbl" and not entries like "prt"==Printer
                if (s4 != null && "pbl".equals(s4.wholeText())) {
                    final Element b = tag.selectFirst(SUBFIELD_CODE_B);
                    if (b != null) {
                        publishers.add(Publisher.from(normalise(b)));
                    }
                }
            }

            return new Pair<>(publishers, date);
        }

        /**
         * Parse Series.
         * <ul>
         *     <li>800 - Series Added Entry - Personal Name (R)</li>
         *     <li>810 - Series Added Entry - Corporate Name (R)</li>
         *     <li>811 - Series Added Entry - Meeting Name (R)</li>
         *     <li>830 - Series Added Entry - Uniform Title (R)</li>
         *     <li>490 - Series Statement (R)</li>
         * </ul>
         *
         * @return list
         */
        @NonNull
        List<Series> series() {
            final List<Series> result = parseSeriesAddedTags();

            // If we got info from either or both, we're done
            if (!result.isEmpty()) {
                return result;
            }

            // Fallback to tag 490.
            // The 'v' field may not contain a pure number,
            // we leave it to the user to clean up
            // 490 - Series Statement (R)
            final Elements primarySeriesTags = document.select("datafield[tag='490']");
            for (final Element tag : primarySeriesTags) {
                // $a - Series statement (R)
                final Element a = tag.selectFirst(SUBFIELD_CODE_A);
                if (a != null) {
                    final Series series = Series.from(normalise(a));
                    // $v - Volume/sequential designation (NR)
                    final Element v = tag.selectFirst(SUBFIELD_CODE_V);
                    if (v != null) {
                        series.setNumber(normalise(v));
                    }
                    result.add(series);
                }
            }

            return result;
        }

        /**
         * Parse Series Added Entry tags.
         * <ul>
         *     <li>800 - Series Added Entry - Personal Name (R)</li>
         *     <li>810 - Series Added Entry - Corporate Name (R)</li>
         *     <li>811 - Series Added Entry - Meeting Name (R)</li>
         *     <li>830 - Series Added Entry - Uniform Title (R)</li>
         * </ul>
         *
         * @return list
         */
        @NonNull
        private List<Series> parseSeriesAddedTags() {

            final String[] tagNrs = {"800", "810", "811", "830"};
            final List<Series> result = new ArrayList<>();

            for (final String tagNr : tagNrs) {
                final Elements tags = document.select("datafield[tag='" + tagNr + "']");
                for (final Element tag : tags) {
                    // $t - Title of a work (NR)
                    final Element title = tag.selectFirst(SUBFIELD_CODE_T);
                    if (title != null) {
                        final Series series = Series.from(normalise(title));
                        // $v - Volume/sequential designation (NR)
                        final Element v = tag.selectFirst(SUBFIELD_CODE_V);
                        if (v != null) {
                            series.setNumber(normalise(v));
                        }
                        // $w - Bibliographic record control number (R)
                        final List<Identifier.Value> ivs =
                                parseInlineIdentifiers(tag.select(SUBFIELD_CODE_W), null);

                        // $x - International Standard Serial Number (NR)
                        final Element x = tag.selectFirst(SUBFIELD_CODE_X);
                        if (x != null) {
                            ivs.add(new Identifier.Value(Identifier.SID_ISSN, normalise(x)));
                        }

                        if (!ivs.isEmpty()) {
                            series.setIdentifiers(ivs);
                        }
                        result.add(series);
                    }
                }
            }
            return result;
        }

        /**
         * Parse description/summary.
         * <p>
         * {@code 520} - Summary, Etc. (R)
         *
         * @return description
         */
        @Nullable
        String description() {
            // $a - Summary, etc. (NR)
            final Elements tags = document.select("datafield[tag='520'] > subfield[code='a']");
            final StringJoiner sj = new StringJoiner("\n\n");
            for (final Element tag : tags) {
                final String text = DESC_PREFIX_PATTERN.matcher(normalise(tag))
                                                       .replaceFirst("")
                                                       .strip();
                if (!text.isBlank()) {
                    sj.add(text);
                }
            }
            if (sj.length() > 0) {
                return sj.toString();
            }
            return null;
        }

        /**
         * Parse title.
         * <p>
         * {@code 245} - Title Statement (NR)
         *
         * @param context Current context
         *
         * @return title
         */
        @Nullable
        String title(@NonNull final Context context) {
            final Element tag = document.selectFirst("datafield[tag='245']");
            if (tag == null) {
                return null;
            }
            // $a - Title (NR)
            final Element a = tag.selectFirst(SUBFIELD_CODE_A);
            if (a == null) {
                return null;
            }
            final String title;
            // $b - Remainder of title (NR)
            final Element b = tag.selectFirst(SUBFIELD_CODE_B);
            if (b == null) {
                title = normalise(a);
            } else {
                title = context.getString(R.string.name_colon_value, normalise(a), normalise(b));
            }
            return cleanTitle(title);
        }

        /**
         * Parse original title.
         * <p>
         * {@code 240} - Uniform Title (NR)
         *
         * @return original title
         */
        @Nullable
        String originalTitle() {
            // $a - Uniform title (NR)
            final Element tag = document.selectFirst("datafield[tag='240'] > subfield[code='a']");
            if (tag == null) {
                return null;
            }
            return normalise(tag);
        }

        /**
         * Parse genre-tags.
         * <p>
         * {@code 655} - Index Term-Genre/Form (R)
         *
         * @return genre-tags; can be empty
         */
        @NonNull
        Set<Tag> genreTags() {
            final Elements tags = document.select("datafield[tag='655']");
            for (final Element tag : tags) {
                // $2 - Source of term (NR)
                final Element s2 = tag.selectFirst(SUBFIELD_CODE_2);
                // https://www.loc.gov/standards/sourcelist/genre-form.html
                // Gattungsbegriffe (Leipzig & Frankfort: Deutsche Nationalbibliothek)
                if (s2 != null && "gatbeg".equals(s2.wholeText())) {
                    // $a - Genre/form data or focus term (NR)
                    final Element a = tag.selectFirst(SUBFIELD_CODE_A);
                    if (a != null) {
                        final Set<Tag> bookTags = new HashSet<>();
                        final String[] tagNames = normalise(a).split(",");
                        for (final String tagName : tagNames) {
                            final String t = tagName.strip();
                            if (!t.isBlank()) {
                                bookTags.add(new Tag(t));
                            }
                        }
                        // quit looping
                        return bookTags;
                    }
                }
            }

            return Set.of();
        }

        /**
         * Parse the carrier type for being an eBook.
         * <p>
         * {@code 338} - Carrier Type (R)
         *
         * @return {@code null} for unknown, or a flag.
         *
         * @see <a href="https://www.loc.gov/standards/valuelist/rdacarrier.html">
         *         rdacarrier codes</a>
         */
        @Nullable
        Boolean isEBook() {
            // 338 - Carrier Type (R)
            final Element tag = document.selectFirst("datafield[tag='338']");
            if (tag == null) {
                return null;
            }
            // $b - Carrier type code (R)
            final Element b = tag.selectFirst(SUBFIELD_CODE_B);
            if (b == null) {
                return null;
            }
            final String code = normalise(b);
            // "nc" == paper/physical, but there is no further distinction.
            // Hence, we simply check for "cr" or not.
            return "cr".equals(code);
        }

        /**
         * Parsing tags 548 and 100/500 for birth and death dates.
         * In theory, the dates can contain 'X' characters for unknown digits.
         * Examples: "XX.XX.1920-06.04.1992", "19XX-201X"  (really??)
         * We'll simply ignore those types and discard them. It's just to complicated.
         *
         * @param fallbackTag the 100/500 tag to use as a last resort.
         *
         * @return a pair of birth/death dates, never {@code null},
         *         but the members can either/both be {@code null}
         */
        @NonNull
        private Pair<String, String> parseAuthorDates(@Nullable final Element fallbackTag) {

            final Elements tags548 = document.select("datafield[tag='548']");
            Pair<String, String> dates;

            // Check for Exact Dates "DD.MM.YYYY-DD.MM.YYYY"
            dates = parseAuthorDates(tags548, "datx");
            if (dates != null) {
                return dates;
            }

            // Fallback to Years "YYYY-YYYY"
            dates = parseAuthorDates(tags548, "datl");
            if (dates != null) {
                return dates;
            }

            // Last chance, use the original author tag
            if (fallbackTag != null) {
                final Element d = fallbackTag.selectFirst(SUBFIELD_CODE_D);
                if (d != null) {
                    final Matcher matcher = LIFESPAN_PATTERN.matcher(normalise(d));
                    if (matcher.find()) {
                        // plain YYYY
                        return new Pair<>(matcher.group(1), matcher.group(2));
                    }
                }
            }
            return new Pair<>(null, null);
        }

        @Nullable
        private Pair<String, String> parseAuthorDates(@NonNull final Collection<Element> tags548,
                                                      @NonNull final String type) {
            for (final Element tag : tags548) {
                final Element s4 = tag.selectFirst(SUBFIELD_CODE_4);
                if (s4 != null) {
                    final String code4 = s4.text();
                    if (type.equals(code4)) {
                        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
                        if (a != null) {
                            final Matcher matcher = LIFESPAN_PATTERN.matcher(normalise(a));
                            if (matcher.find()) {
                                // YYYY or DD.MM.YYYY
                                return new Pair<>(normaliseDate(matcher.group(1)),
                                                  normaliseDate(matcher.group(2)));
                            }
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Parse tag 500 for author info.
         *
         * @return author
         *
         * @see <a href="https://sta.dnb.de/doc/GND-DF-BEZIEHUNG-ZU-PERSON-FAMILIE">
         *         Dnb docs</a>
         * @see <a href="https://wiki.dnb.de/download/attachments/90411361/eh-p-06.pdf">
         *         more docs</a>
         */
        @Nullable
        private Author authorTag500() {
            final Element tag = document.selectFirst("datafield[tag='500']");
            if (tag == null) {
                return null;
            }

            // https://sta.dnb.de/doc/GND-DF-BEZIEHUNG-ZU-PERSON-FAMILIE#Beziehungskennzeichnung
            for (final Element code4 : tag.select(SUBFIELD_CODE_4)) {
                final String text = normalise(code4);
                // modern: a schema url ending in realIdentity
                // older: nawi (Wirklicher Name == Real name)
                // very old: "pisa"
                if ("nawi".equals(text) || "pisa".equals(text) || text.contains("realIdentity")) {
                    final Element a = tag.selectFirst(SUBFIELD_CODE_A);
                    if (a != null) {
                        final Author realAuthor = normaliseAuthor(a);
                        final List<Identifier.Value> ivs = parseAuthorIdentifiers(tag);
                        if (!ivs.isEmpty()) {
                            ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
                            realAuthor.addIdentifiers(ivs);
                        }
                        final Pair<String, String> dates = parseAuthorDates(tag);
                        realAuthor.setBirthDate(dates.first);
                        realAuthor.setDeathDate(dates.second);
                        return realAuthor;
                    }
                }
            }
            return null;
        }

        /**
         * Identifiers as set on tag 100 or 500 itself.
         * <p>
         * We <strong>only</strong> accept the native DNB, DE-588 and ISNI identifiers.
         * All others are ignored.
         *
         * @param tag "100" or "500"
         *
         * @return list; may contain duplicates.
         */
        @NonNull
        private List<Identifier.Value> parseAuthorIdentifiers(@NonNull final Element tag) {
            // Author identifiers for DNB and ISNI
            // $0 - Authority record control number or standard number (R)
            return parseInlineIdentifiers(tag.select(SUBFIELD_CODE_0),
                                          List.of("DE-101", "DE-588", "isni"));
        }

        /**
         * Collect the identifiers from the given elements.
         * Identifiers are expected to be formatted "(source)identifier".
         * <p>
         * All keys are accepted (after optional filtering) as these will
         * represent 'official' (non-commercial) sources .
         *
         * @param list       to parse
         * @param filterKeys {@code null} to accept all keys,
         *                   or a list of keys to allow (keys not on the list will be dropped)
         *                   <strong>filtering is done BEFORE mapping</strong>
         *
         * @return identifier values
         */
        @NonNull
        private List<Identifier.Value> parseInlineIdentifiers(
                @NonNull final List<Element> list,
                @Nullable final List<String> filterKeys) {
            final List<Identifier.Value> result = new ArrayList<>();

            for (final Element element : list) {
                final Matcher matcher = IDENT_PATTERN.matcher(normalise(element));
                if (matcher.find()) {
                    final String key = matcher.group(1);
                    final String value = matcher.group(2);

                    if (key != null && value != null
                        && (filterKeys == null || filterKeys.contains(key))) {
                        // Accept all (filtered) keys
                        final String mappedKey = IDENTIFIER_MAPPING.getOrDefault(key, key);
                        //noinspection DataFlowIssue
                        result.add(new Identifier.Value(mappedKey, value));
                    }
                }
            }

            return result;
        }

        /**
         * {@code 024} Other Standard Identifier (R).
         * <p>
         * Tag 024 identifiers are only added if we can map them. Others are dropped.
         * We're doing this because there is no official SRU list of the ones we might see.
         *
         * @return list
         */
        @NonNull
        private List<Identifier.Value> parseTag024Identifiers() {
            final List<Identifier.Value> ivs = new ArrayList<>();

            // 024 - Other Standard Identifier (R)
            // 7 - Source specified in subfield $2
            final Elements fields = document.select("datafield[tag='024'][ind1='7']");
            for (final Element df : fields) {
                // $2 - Source of number or code (NR)
                final Element source = df.selectFirst(SUBFIELD_CODE_2);
                if (source != null) {
                    // Filter on listed keys only.
                    final String key = IDENTIFIER_MAPPING.get(normalise(source));
                    if (key != null) {
                        // $a - Standard number or code (NR)
                        final Element a = df.selectFirst(SUBFIELD_CODE_A);
                        if (a != null) {
                            final String value = normalise(a);
                            if (!value.isBlank()) {
                                ivs.add(new Identifier.Value(key, value));
                            }
                        }
                    }
                }
            }

            return ivs;
        }
    }
}
