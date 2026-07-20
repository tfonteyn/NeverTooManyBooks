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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.parsers.MoneyParser;
import com.hardbacknutter.nevertoomanybooks.core.parsers.PartialDateParser;
import com.hardbacknutter.nevertoomanybooks.core.utils.LocaleListUtils;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorRole;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.PublicationFrequency;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISNI;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCode;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ProductCodeType;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Parser for the bnf unimarc format.
 * <p>
 * IMPORTANT: do NOT combine multiple tags in one select if the order is important!
 * <p>
 * <a href="https://www.ifla.org/unimarc-updates/unimarc-bibliographic-format-manual-online-ed/">
 * English PDF manuel</a>
 * <a href="https://www.transition-bibliographique.fr/unimarc/manuel-unimarc-format-bibliographique/">
 * French tags online</a>
 */
class BnfBookParser {

    /** These need to be removed from all strings we read. */
    private static final Pattern CONTROL_CHARACTERS_PATTERN = Pattern.compile(
            "[\\x80-\\x9F\\u02DC\\u0153\\u2018\\u2019]");

    /** The "extend" typically contains the number of pages in {@code () / []} brackets. */
    private static final Pattern PAGES_PATTERN = Pattern.compile(".*[(\\[](\\d*).*?[)\\]].*");

    /** {@code YYYY}. */
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}");
    /** Optional {@code YYYY}. */
    private static final String DATE_REGEX = "(\\d{4})?";
    /**
     * Matches an optional year, a mandatory hyphen, and an optional year.
     * with either of the pre-dash, or post-dash parts missing.
     * Group 1 = Birth
     * Group 2 = Death
     */
    private static final Pattern LIFESPAN_PATTERN = Pattern.compile(
            DATE_REGEX + "-" + DATE_REGEX);

    private static final String SUBFIELD_CODE_0 = "*|subfield[code='0']";
    private static final String SUBFIELD_CODE_3 = "*|subfield[code='3']";
    private static final String SUBFIELD_CODE_4 = "*|subfield[code='4']";
    private static final String SUBFIELD_CODE_A = "*|subfield[code='a']";
    private static final String SUBFIELD_CODE_B = "*|subfield[code='b']";
    private static final String SUBFIELD_CODE_C = "*|subfield[code='c']";
    private static final String SUBFIELD_CODE_D = "*|subfield[code='d']";
    private static final String SUBFIELD_CODE_F = "*|subfield[code='f']";
    private static final String SUBFIELD_CODE_O = "*|subfield[code='o']";
    private static final String SUBFIELD_CODE_T = "*|subfield[code='t']";
    private static final String SUBFIELD_CODE_U = "*|subfield[code='u']";
    private static final String SUBFIELD_CODE_V = "*|subfield[code='v']";
    private static final String SUBFIELD_CODE_X = "*|subfield[code='x']";

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

    private static final int TAG_110_FORMAT = 0;
    private static final int TAG_110_FREQUENCY = 1;
    private static final int TAG_110_REGULARITY = 2;

    private final List<Identifier.Value> ivs = new ArrayList<>();

    @NonNull
    private final Context context;
    @NonNull
    private final BnfSearchEngine searchEngine;
    @NonNull
    private final Document document;
    @NonNull
    private final Book book;

    private final PartialDateParser partialDateParser;

    // TODO: we don't really want to pass in the searchEngine
    //  but we use it for a helper in its base class.
    BnfBookParser(@NonNull final Context context,
                  @NonNull final BnfSearchEngine searchEngine,
                  @NonNull final Document document,
                  @NonNull final Book book) {
        this.context = context;
        this.searchEngine = searchEngine;
        this.document = document;
        this.book = book;

        partialDateParser = new PartialDateParser();
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
    private static String normalise(@NonNull final Element element) {
        final String s = CONTROL_CHARACTERS_PATTERN.matcher(element.wholeText())
                                                   .replaceAll("");
        return Normalizer.normalize(s, Normalizer.Form.NFC)
                         .strip();
    }

    /**
     * Parse the BNF identifier.
     * <p>
     * {@code 001} RECORD IDENTIFIER.
     */
    void sid() {
        final Element tag = document.selectFirst("*|controlfield[tag=001]");
        if (tag == null) {
            return;
        }

        final String s = tag.text();
        // paranoia...
        if (s.length() > 14) {
            ivs.add(new Identifier.Value(Identifier.SID_BNF, s.substring(5, 13)));
        }
    }


    /**
     * {@code 010} INTERNATIONAL STANDARD BOOK NUMBER (ISBN) (R).
     * <ul>
     * <li>{@code $a} Number (ISBN) (NR)</li>
     * <li>{@code $b} Qualification (R)</li>
     * <li>{@code $d} Terms of Availability and/or Price (NR)</li>
     * </ul>
     */
    void isbnFormatAndPrice() {
        productCodeFormatAndPrice("010");
    }

    /**
     * {@code 011} ISSN (R).
     * <ul>
     * <li>{@code $a} Number (ISSN) (NR)</li>
     * <li>{@code $b} Qualification (R)</li>
     * <li>{@code $d} Terms of Availability and/or Price (R)</li>
     * </ul>
     */
    void issnFormatAndPrice() {
        productCodeFormatAndPrice("011");
    }

    /**
     * {@code 010} INTERNATIONAL STANDARD BOOK NUMBER (ISBN) (R).
     * <ul>
     * <li>{@code $a} Number (ISBN) (NR)</li>
     * <li>{@code $b} Qualification (R)</li>
     * <li>{@code $d} Terms of Availability and/or Price (NR)</li>
     * </ul>
     * {@code 011} ISSN (R).
     * <ul>
     * <li>{@code $a} Number (ISSN) (NR)</li>
     * <li>{@code $b} Qualification (R)</li>
     * <li>{@code $d} Terms of Availability and/or Price (R)</li>
     * </ul>
     *
     * @param tagNr to parse, either 010 or 011
     */
    void productCodeFormatAndPrice(@NonNull final String tagNr) {
        final Element tag = document.selectFirst("*|datafield[tag=" + tagNr + "]");
        if (tag == null) {
            return;
        }

        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            final String codeStr = ISBN.cleanText(a.text());
            if (!codeStr.isBlank()) {
                book.setRawProductCode(codeStr);
            }
        }

        final Element b = tag.selectFirst(SUBFIELD_CODE_B);
        if (b != null) {
            format(normalise(b));
        }

        final Element d = tag.selectFirst(SUBFIELD_CODE_D);
        if (d != null) {
            listPrice(normalise(d));
        }
    }

    private void format(@NonNull final String s) {
        if (s.isBlank()) {
            return;
        }
        // The values are not well-defined; best effort
        final String lc = s.toLowerCase(Locale.FRANCE);
        switch (lc) {
            case "br.":
                book.setFormat(context.getString(R.string.book_format_paperback));
                break;
            case "rel.":
                book.setFormat(context.getString(R.string.book_format_hardcover));
                break;
            default:
                // The FormatMapper will hopefully transform as needed/permitted
                book.setFormat(s);
        }
    }

    private void listPrice(@NonNull final String s) {
        if (s.isBlank()) {
            return;
        }
        final LocaleList userLocales = context.getResources().getConfiguration()
                                              .getLocales();
        final List<Locale> allLocales = LocaleListUtils.asList(Locale.FRANCE, userLocales);
        final MoneyParser parser = new MoneyParser(Locale.FRANCE, allLocales);
        searchEngine.addPriceListed(context, parser, s, null, book);
    }

    /**
     * {@code 100} GENERAL PROCESSING DATA.
     */
    void publicationDate() {
        final Element tag = document.selectFirst("*|datafield[tag=100]");
        if (tag == null) {
            return;
        }
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            final String s = normalise(a);
            if (!s.isBlank() && s.length() >= 13) {
                // Positions 9-12 are the YYYY; parse paranoia...
                partialDateParser.parse(s.substring(9, 13)).ifPresent(book::setPublicationDate);
            }
        }
    }

    /**
     * {@code 101} LANGUAGE OF THE RESOURCE (R).
     * <ul>
     * <li>{@code $a} Language of Text (R)</li>
     * <li>{@code $c} Language of Original Work (R)</li>
     * </ul>
     */
    void languages() {
        final Element tag = document.selectFirst("*|datafield[tag=101]");
        if (tag == null) {
            return;
        }
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            book.setLanguage(normalise(a));
        }
        final Element c = tag.selectFirst(SUBFIELD_CODE_C);
        if (c != null) {
            book.setTranslatedFromLanguage(normalise(c));
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
     * {@code 110} CODED DATA FIELD: CONTINUING RESOURCES.
     * <ul>
     *     <li>Position 0: format</li>
     *     <li>Position 1/2: frequency</li>
     * </ul>
     *
     * @param series to update
     */
    void periodicals(@NonNull final Series series) {

        final Element tag = document.selectFirst("*|datafield[tag=110]");
        if (tag == null) {
            return;
        }
        final String codedData = tag.text();
        // sanity check
        if (codedData.length() < 4) {
            return;
        }

        switch (codedData.charAt(TAG_110_FORMAT)) {
            // c - Newspaper
            case 'c': {
                book.setFormat(context.getString(R.string.book_format_newspaper));
                break;
            }
            case 'j': {
                book.setFormat(context.getString(R.string.book_format_journal));
                break;
            }
            // a - Periodical
            // m - magazine
            // n - newsletter
            case 'a':
            case 'm':
            case 'n': {
                book.setFormat(context.getString(R.string.book_format_periodical));
                break;
            }
        }

        final PublicationFrequency frequency = PublicationFrequency.fromUnimarc(
                codedData.charAt(TAG_110_FREQUENCY),
                codedData.charAt(TAG_110_REGULARITY));
        if (frequency.getType() != PublicationFrequency.Type.Unknown) {
            series.setPublicationFrequency(frequency);
        }
    }

    /**
     * {@code 200} TITLE AND STATEMENT OF RESPONSIBILITY (NR).
     * <p>
     * Typically, we only need {@code $a} for the title.
     * <ul>
     * <li>{@code $a} Title Proper (R)</li>
     * <li>{@code $b} General Material Designation (R)</li>
     * <li>{@code $e} Other Title Information (R)</li>
     * <li>{@code $f} First Statement of Responsibility (R)</li>
     * <li>{@code $g} Subsequent Statement of Responsibility (R)</li>
     * <li>{@code $h} Number of a Part (R)</li>
     * <li>{@code $i} Name of a Part (R)</li>
     * </ul>
     */
    void title() {
        final Element a = document.selectFirst("*|datafield[tag=200] > *|subfield[code=a]");
        if (a == null) {
            return;
        }
        final String text = normalise(a);
        if (!text.isBlank()) {
            book.setTitle(text);
        }
    }

    /**
     * {@code 210}  PUBLICATION, DISTRIBUTION, ETC. (R).
     * {@code 214} PRODUCTION, PUBLICATION, DISTRIBUTION, MANUFACTURE STATEMENTS (R).
     * <ul>
     * <li>{@code $c} Name of Publisher, Distributor, etc. (R)</li>
     * <li>{@code $d} Date of Publication, Distribution, etc. (R)</li>
     * </ul>
     */
    void publication() {
        publication("214");
        if (!book.getPublishers().isEmpty()) {
            return;
        }
        publication("210");
    }

    private void publication(@NonNull final String tagNr) {
        final Elements tags = document.select("*|datafield[tag='" + tagNr + "']");
        for (final Element tag : tags) {
            final Element c = tag.selectFirst(SUBFIELD_CODE_C);
            if (c != null) {
                final String s = normalise(c);
                if (!s.isBlank()) {
                    book.add(Publisher.from(s));
                }
            }
        }
    }

    /**
     * {@code 215} PHYSICAL DESCRIPTION.
     * <ul>
     * <li>{@code $a} Specific Material Designation and Extent</li>
     * <li>{@code $c} Other Physical Details</li>
     * </ul>
     */
    void physicalDescription() {
        final Element tag = document.selectFirst("*|datafield[tag=215]");
        if (tag == null) {
            return;
        }
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            String s = normalise(a);
            if (!s.isBlank()) {
                final Matcher matcher = PAGES_PATTERN.matcher(s);
                if (matcher.find()) {
                    s = matcher.group(TAG_110_FREQUENCY);
                    if (s != null && !s.isBlank()) {
                        book.setPages(s);
                    }
                }
            }
        }
        final Element c = tag.selectFirst(SUBFIELD_CODE_C);
        if (c != null) {
            final String s = normalise(c);
            if (!s.isBlank()) {
                // This is gamble.... there is no structure.
                // We simply look for "illustration" and "couleur"
                if (s.contains("ill") && s.contains("coul")) {
                    book.setColor(context.getString(R.string.book_color_full_color));
                }
            }
        }
    }

    /**
     * {@code 410} SERIES.
     * {@code 461} SET
     * <ul>
     * <li>{@code $t} Title</li>
     * <li>{@code $v} Volume Designation</li>
     * </ul>
     */
    void series() {
        // parse 461 FIRST! It's the actual series, while 410 is a publisher collection/series.
        Stream.of("461", "410").forEach(this::series);

        if (book.getSeries().isEmpty()) {
            seriesT225();
        }
    }

    private void series(@NonNull final String tagNr) {
        final Elements tags = document.select("*|datafield[tag=" + tagNr + "]");
        for (final Element tag : tags) {
            final Element t = tag.selectFirst(SUBFIELD_CODE_T);
            if (t != null) {
                final String title = normalise(t);
                if (!title.isBlank()) {
                    // Use the constructor! Do NOT parse!
                    // We must ensure that "Quarto (Paris)" is used as the title only,
                    // and not parsed to using "Paris" as the number.
                    final Series series = new Series(title);
                    final Element v = tag.selectFirst(SUBFIELD_CODE_V);
                    if (v != null) {
                        series.setNumber(normalise(v));
                    }

                    final Element x = tag.selectFirst(SUBFIELD_CODE_X);
                    if (x != null) {
                        final ProductCode s = ISBN.parse(normalise(x));
                        if (s.getType() == ProductCodeType.Issn8
                            || s.getType() == ProductCodeType.Issn13) {
                            series.setIdentifierValue(Identifier.SID_ISSN,
                                                      s.asText(ProductCodeType.Issn8));
                        }
                    }

                    final Element s0 = tag.selectFirst(SUBFIELD_CODE_0);
                    if (s0 != null) {
                        final String s = normalise(s0);
                        if (!s.isBlank()) {
                            series.setIdentifierValue(Identifier.SID_BNF, s);
                        }
                    }

                    // As we parse multiple fields for the series,
                    // we need to check if it's not already present.
                    // We can still end up with duplicates though.
                    // Example: "Quarto" and "Quarto (Paris)".
                    if (book.getSeries().stream().noneMatch(series1 -> series1.equals(series))) {
                        book.add(series);
                    }
                }
            }
        }
    }

    /**
     * {@code 225} SERIES (R).
     * <ul>
     * <li>{@code $a} Title</li>
     * <li>{@code $v} Volume Designation</li>
     * </ul>
     */
    private void seriesT225() {
        final Element tag = document.selectFirst("*|datafield[tag=225]");
        if (tag == null) {
            return;
        }
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            final String title = normalise(a);
            if (!title.isBlank()) {
                final Series series = new Series(title);
                final Element v = tag.selectFirst(SUBFIELD_CODE_V);
                if (v != null) {
                    series.setNumber(normalise(v));
                }

                // As we parse multiple fields for the series,
                // we need to check if it's not already present.
                // We could still end up with duplicates though.
                // Example: "Quarto" and "Quarto (Paris)".
                if (book.getSeries().stream().noneMatch(series1 -> series1.equals(series))) {
                    book.add(series);
                }
            }
        }
    }

    /**
     * {@code 330} SUMMARY OR ABSTRACT.
     * <ul>
     * <li>{@code $a} Text of Note</li>
     * <li>{@code $u} Uniform Resource Identifier (URI) </li>
     * </ul>
     */
    void description() {
        final Element tag = document.selectFirst("*|datafield[tag=330]");
        if (tag == null) {
            return;
        }
        final Element a = tag.selectFirst(SUBFIELD_CODE_A);
        if (a != null) {
            final String s = normalise(a);
            if (!s.isBlank()) {
                book.setDescription(s);
            }
        } else {
            // Only grab the url if there was no text
            final Element u = tag.selectFirst(SUBFIELD_CODE_U);
            if (u != null) {
                final String s = normalise(u);
                if (!s.isBlank()) {
                    book.setDescription(s);
                }
            }
        }
    }

    /**
     * {@code 454} TRANSLATION OF.
     * <ul>
     * <li>{@code $d} Date of Publication</li>
     * <li>{@code $t} Title</li>
     * </ul>
     */
    void translation() {
        final Element tag = document.selectFirst("*|datafield[tag=454]");
        if (tag == null) {
            return;
        }
        final Element t = tag.selectFirst(SUBFIELD_CODE_T);
        if (t != null) {
            final String s = normalise(t);
            if (!s.isEmpty()) {
                book.setTranslatedFromTitle(s);
            }
        }

        final Element d = tag.selectFirst(SUBFIELD_CODE_D);
        if (d != null) {
            // Date of Publication is free-form ... best effort try to find a year
            final String s = parseYear(d);
            if (s != null) {
                book.setFirstPublicationDate(s);
            }
        }
    }

    /**
     * {@code 700} PERSONAL NAME – PRIMARY RESPONSIBILITY.
     * {@code 701} PERSONAL NAME – ALTERNATIVE RESPONSIBILITY.
     * {@code 702} PERSONAL NAME – SECONDARY RESPONSIBILITY.
     * {@code 710} CORPORATE BODY NAME – PRIMARY RESPONSIBILITY.
     * {@code 711} CORPORATE BODY NAME – ALTERNATIVE RESPONSIBILITY.
     * <ul>
     * <li>{@code $a} Entry Element</li>
     * <li>{@code $b} Part of Name Other than Entry Element</li>
     * <li>{@code $o} International Standard Identifier for the Name</li>
     * <li>{@code $3} Authority Record Identifier or Standard Number </li>
     * <li>{@code $4} Relator Code</li>
     * </ul>
     */
    void authors() {
        Stream.of("700", "701", "702", "710", "711").forEach(this::authors);
    }

    private void authors(@NonNull final String tagNr) {
        final Elements tags = document.select("*|datafield[tag=" + tagNr + "]");
        for (final Element tag : tags) {
            final Element a = tag.selectFirst(SUBFIELD_CODE_A);
            if (a == null) {
                return;
            }
            final String familyName = normalise(a);
            if (!familyName.isBlank()) {
                String givenNames = null;
                final Element b = tag.selectFirst(SUBFIELD_CODE_B);
                if (b != null) {
                    givenNames = normalise(b);
                }
                final Author author = new Author(familyName, givenNames);

                final Element o = tag.selectFirst(SUBFIELD_CODE_O);
                if (o != null) {
                    final String isniStr = normalise(o);
                    if (isniStr.startsWith("ISNI")) {
                        final ISNI isni = new ISNI(isniStr.substring(4));
                        if (isni.isValid()) {
                            author.setIdentifierValue(Identifier.SID_ISNI, isni.asText());
                        }
                    }
                }

                final Element s3 = tag.selectFirst(SUBFIELD_CODE_3);
                if (s3 != null) {
                    final String s = normalise(s3);
                    if (!s.isBlank()) {
                        author.setIdentifierValue(Identifier.SID_BNF, s);
                    }
                }

                final Element s4 = tag.selectFirst(SUBFIELD_CODE_4);
                if (s4 != null) {
                    final String s = normalise(s4);
                    final Integer role = AUTHOR_CODES.get(s);
                    if (role != null) {
                        author.setRole(role);
                    }
                }

                // This is best effort, as the field is free-form.
                // simply look for YYYY-YYYY, anything else is discarded.
                // We count on the AuthorResolver to find better dates.
                final Element f = tag.selectFirst(SUBFIELD_CODE_F);
                if (f != null) {
                    final Matcher matcher = LIFESPAN_PATTERN.matcher(normalise(f));
                    if (matcher.find()) {
                        author.setBirthDate(matcher.group(TAG_110_FREQUENCY));
                        author.setDeathDate(matcher.group(TAG_110_REGULARITY));
                    }
                }

                book.add(author);
            }
        }
    }

    @Nullable
    private String parseYear(@NonNull final Element element) {
        final String text = normalise(element);
        if (text.isBlank()) {
            return null;
        }
        final Matcher matcher = YEAR_PATTERN.matcher(text);
        if (matcher.find()) {
            final String s = matcher.group();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /**
     * {@code 856} ELECTRONIC LOCATION AND ACCESS (R).
     *
     * @return list of cover ids; can be empty.
     */
    @NonNull
    List<String> coverIds() {
        final List<String> result = new ArrayList<>();
        final Elements tags = document.select("*|datafield[tag=856]");
        for (final Element tag : tags) {
            // $u Uniform Resource Identifier (URI)
            final Element u = tag.selectFirst(SUBFIELD_CODE_U);
            if (u == null) {
                return List.of();
            }

            result.add(normalise(u));
        }
        return result;
    }

    /**
     * Finish the parsing process.
     *
     * @param searchedCode to code which the user was searching for.
     *                     It will be set on the book as its product code,
     *                     if the the latter was not retrieved during the search
     */
    public void finish(@Nullable final String searchedCode) {
        ServiceLocator.getInstance().getIdentifierDao().pruneList(ivs);
        if (!ivs.isEmpty()) {
            book.setIdentifiers(ivs);
        }

        if (!book.hasProductCode() && searchedCode != null) {
            book.setRawProductCode(searchedCode);
        }
    }
}
