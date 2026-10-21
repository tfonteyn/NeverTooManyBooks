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

package com.hardbacknutter.nevertoomanybooks.searchengines.kbnl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Identifier;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISBN;
import com.hardbacknutter.nevertoomanybooks.entities.codes.ISNI;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineUtils;

class BookParser {

    /** Example: {@code REL?PPN=068561504}. */
    private static final Pattern AUTHOR_ID = Pattern.compile("REL\\?PPN=(\\d+)");
    /** Split the author name from the trailing bracketed sections. */
    private static final Pattern AUTHOR_NAME = Pattern.compile("(?=\\()");
    /** Loop over the bracketed sections after the author name. */
    private static final Pattern AUTHOR_BRACKETED_SUFFIX = Pattern.compile("\\((.*?)\\)");
    /** Parse the bracketed section with the author dates. */
    private static final Pattern BIRTH_DEATH_DATES =
            Pattern.compile("(\\d\\d\\d\\d)-(\\d\\d\\d\\d|)");
    private static final Pattern PAGES_PATTERN = Pattern.compile(".*?(\\d+) pagina.*",
                                                                 Pattern.UNICODE_CASE);
    private static final Pattern ISBN_BOUNDARY_PATTERN = Pattern.compile("[;)]");
    private static final Pattern TRANSLATION_PATTERN = Pattern.compile(
            ".*?(?:Vert\\. van:|Vertaling van:)\\s*(.*)",
            Pattern.UNICODE_CASE);
    /** Example: {@code "https://webggc.oclc.org/cbs/DB=2.37/XMLPRS=Y/PPN?PPN=852323123"}. */
    static final Pattern PERMALINK_PATTERN = Pattern.compile(
            ".*?https.*?PPN=(\\d+).*", Pattern.UNICODE_CASE);

    @Nullable
    private String tmpSeriesNr;

    @Nullable
    Author parseAuthor(@NonNull final CharSequence fullText,
                       @Nullable final CharSequence href) {

        final String[] parts = BookParser.AUTHOR_NAME.split(fullText, 2);

        String name = parts[0].strip();
        // reject separators as for example: <psi:text>;</psi:text>
        if (name.length() == 1) {
            return null;
        }

        name = SearchEngineUtils.cleanName(name);
        if (name.isBlank()) {
            return null;
        }

        final Author author = Author.from(name);

        if (parts.length > 1) {
            // "Isaak Judovič Ozimov (1920-1992) (ISNI 0000 0001 2259 0564)"
            // parts[1] == "(1920-1992) (ISNI 0000 0001 2259 0564)"
            // parts[1] == "(1976-) (ISNI 0000 0004 8004 6868)"
            final Matcher matcher = BookParser.AUTHOR_BRACKETED_SUFFIX.matcher(parts[1]);
            while (matcher.find()) {
                final String s = matcher.group(1);
                if (s != null) {
                    if (s.startsWith("ISNI")) {
                        parseAuthorIssn(s, author);
                    } else {
                        parseAuthorDates(s, author);
                    }
                }
            }
        }

        if (href != null) {
            parseAuthorSid(href, author);
        }

        return author;
    }

    private void parseAuthorIssn(@NonNull final String s,
                                 @NonNull final Author author) {
        final ISNI isni = new ISNI(s.substring(4));
        if (isni.isValid()) {
            author.setIdentifierValue(Identifier.SID_ISNI, isni.asText());
        }
    }

    private void parseAuthorDates(@NonNull final CharSequence s,
                                  @NonNull final Author author) {
        final Matcher bddMatcher = BookParser.BIRTH_DEATH_DATES.matcher(s);
        if (bddMatcher.find()) {
            author.setBirthDate(bddMatcher.group(1));
            final String deathDate = bddMatcher.group(2);
            if (deathDate != null) {
                author.setDeathDate(deathDate);
            }
        }
    }

    private void parseAuthorSid(@NonNull final CharSequence href,
                                @NonNull final Author author) {
        final Matcher matcher = BookParser.AUTHOR_ID.matcher(href);
        if (matcher.find()) {
            final String siId = matcher.group(1);
            if (siId != null) {
                author.setIdentifierValue(Identifier.SID_KBNL, siId);
            }
        }
    }

    void parsePublisher(@NonNull final String fullText,
                        @NonNull final Book book) {
        String text = fullText;
        // the part before the ":" is (usually?) the city. 2nd part is the name
        if (fullText.contains(":")) {
            text = text.split(":")[1].strip();
        }
        text = SearchEngineUtils.cleanName(text);
        if (!text.isBlank()) {
            book.add(Publisher.from(text));
        }
    }

    void parseDatePublished(@NonNull final String fullText,
                            @NonNull final Book book) {
        // It's not good... we've seen some different notations.
        // e.g.:  [2019]
        // e.g.:  c1977, cover 1978
        // Grab the first bit before a comma, and strip it for digits + hope for the best
        final String year = SearchEngineUtils.digits(fullText.split(",")[0]);
        if (!year.isEmpty()) {
            try {
                book.setPublicationDate(Integer.parseInt(year));
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }
    }

    void parsePages(@NonNull final String fullText,
                    @NonNull final Book book) {

        final Matcher matcher = PAGES_PATTERN.matcher(fullText);
        if (matcher.find()) {
            try {
                final String group = matcher.group(1);
                if (group != null && !group.isEmpty()) {
                    book.setPages(Integer.parseInt(group));
                    return;
                }
            } catch (@NonNull final NumberFormatException ignore) {
                // ignore
            }
        }
        try {
            book.setPages(Integer.parseInt(fullText.split(" ")[0]));
            return;
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore
        }

        // use source
        book.setPages(fullText);
    }

    void parseProductCodeAndFormat(@NonNull final String fullText,
                                   @NonNull final Book book) {
        if (Character.isDigit(fullText.charAt(0))) {
            if (!book.hasProductCode()) {
                final String codeStr = ISBN.cleanText(fullText.split(":")[0]);
                // Do a crude test on the length and hope for the best
                // (don't do a full ISBN test here, no need)
                // 8 for ISSN, 10/13 for ISBN-
                if (codeStr.length() == 8
                    || codeStr.length() == 10
                    || codeStr.length() == 13) {
                    book.setRawProductCode(codeStr);
                }
            }
        } else if (fullText.charAt(0) == '(') {
            if (!book.contains(DBKey.FORMAT)) {
                // Skip the 1st bracket, and split either on closing or on semicolon
                book.setFormat(ISBN_BOUNDARY_PATTERN.split(fullText.substring(1))[0].strip());
            }
        }
    }

    void parseTitle(@NonNull final String[] cleanedData,
                    @NonNull final Book book) {
        final String text = cleanedData[0];
        final String s = SearchEngineUtils.cleanText(text);
        if (!s.isBlank()) {
            book.setTitle(s);
        }
        // It's temping to decode cleanedData[1],
        // but the data has proven to be very unstructured and mostly unusable.
    }

    void parseSeriesNumber(@NonNull final String fullText) {
        // This element is listed BEFORE the Series ("reeks") itself so store it tmp.
        // Note it's often missing altogether
        final String[] nrStr = fullText.split("/")[0].split(" ");
        if (nrStr.length > 1) {
            tmpSeriesNr = nrStr[1];
        } else {
            tmpSeriesNr = nrStr[0];
        }
    }

    void parseSeries(@NonNull final String fullText,
                     @NonNull final Book book) {
        // the number part is totally unstructured
        // we only use the separate specified number if present
        final String s = SearchEngineUtils.cleanName(fullText);
        if (!s.isBlank()) {
            book.add(Series.from(s, tmpSeriesNr));
        }
        tmpSeriesNr = null;
    }

    void parseIllustration(@NonNull final String fullText,
                           @NonNull final Book book) {
        if (!book.contains(DBKey.COLOR)) {
            book.setColor(fullText);
        }
    }

    void parseEditionInfo(@NonNull final String fullText,
                          @NonNull final Book book) {
        final String s = SearchEngineUtils.cleanText(fullText);
        if (!s.isBlank()) {
            book.setEditionInfo(s);
        }
    }

    void parseAnnotation(@NonNull final String fullText,
                         @NonNull final Book book) {
        final Matcher matcher = TRANSLATION_PATTERN.matcher(fullText);
        if (matcher.find()) {
            book.setTranslatedFromTitle(matcher.group(1));
        }
    }

    void parseDescription(@NonNull final String fullText,
                          @NonNull final Book book) {

        String desc = SearchEngineUtils.cleanText(fullText);
        if (!desc.isBlank()) {
            // append... the site has (at least) two fields we want to collect
            final String previous = book.getDescription();
            if (!previous.isEmpty()) {
                desc = previous + (previous.endsWith("\n") ? "" : "\n")
                       + '\n' + desc;
            }
            book.setDescription(desc);
        }
    }

    void parsePermalink(@NonNull final String permaLink,
                        @NonNull final Book book) {
        final Matcher matcher = PERMALINK_PATTERN.matcher(permaLink);
        if (matcher.find()) {
            final String ppn = matcher.group(1);
            book.setIdentifierValue(Identifier.SID_KBNL, ppn);
        }
    }
}
