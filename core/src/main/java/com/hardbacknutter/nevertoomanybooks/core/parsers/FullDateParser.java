/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Parser for dates comes from the internet and/or the user (either as direct input, or by import).
 * <p>
 * This object is thread-safe, but user locale specific at creation time.
 * <p>
 * TEST: <a href="https://issuetracker.google.com/issues/158417777">DateTimeParseException</a>
 * seems to be fixed, but the bug was never closed?
 * <p>
 * TODO: performance: create all parsers, then parse (and reuse the parsers)...
 * or create the parsers each time, but stop at first result.
 */
public class FullDateParser
        implements DateParser {

    /** All numerical (i.e. Locale independent) patterns. */
    private static final String[] NUMERICAL_PATTERNS = {
            // US format first
            "MM-dd-yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm",

            // International next
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",

            // same without time
            "MM-dd-yyyy",
            "dd-MM-yyyy",
    };

    /** Patterns with Locale dependent text. */
    private static final String[] TEXT_PATTERNS = {
            // Abbreviated month
            "dd-MMM-yyyy",
            "dd MMM yyyy",
            "dd MMM. yyyy",

            // Month in full
            "dd-MMMM-yyyy",
            "dd MMMM yyyy",

            // Dates of the form: 'Fri Apr 5 17:23:11 -0800 2012'
            "EEE MMM dd HH:mm:ss ZZZZ yyyy",

            // Used by OpenLibrary, maybe others
            "MMM d, yyyy",
            "MMMM d, yyyy",
            "MMM yyyy",
            "MMMM yyyy",

            // Occasionally we have seen the year in old style 2 digits
            "dd-MMM-yy",
            "dd-MMMM-yy",
    };

    /** Immutable. */
    @NonNull
    private final List<Locale> locales;
    @NonNull
    private final DateParser isoDateParser;
    /** List of patterns we'll use to parse dates. */
    @Nullable
    private Collection<DateTimeFormatter> textParsers;
    @Nullable
    private Collection<DateTimeFormatter> numericalParsers;

    /**
     * Constructor.
     *
     * @param systemLocale to use for ISO date parsing
     * @param locales      the locales to creates further parsers as needed.
     */
    public FullDateParser(@NonNull final Locale systemLocale,
                          @NonNull final List<Locale> locales) {
        this.isoDateParser = new ISODateParser(systemLocale);
        this.locales = locales;
    }

    /**
     * Attempt to parse a date string.
     *
     * @param dateStr String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    @Override
    public LocalDateTime parse(@Nullable final String dateStr) {
        return parse(dateStr, null);
    }

    /**
     * Attempt to parse a date string using the passed locale.
     * This method is meant to be used by site-specific code where the site Locale is known.
     *
     * @param dateStr String to parse
     * @param locale  to try first; i.e. before the pre-defined list.
     *
     * @return Resulting date if successfully parsed, otherwise {@code null}
     */
    @Nullable
    @Override
    public LocalDateTime parse(@Nullable final String dateStr,
                               @Nullable final Locale locale) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        // Try ISO first, then numerical, and lastly the extensive text based.
        LocalDateTime result = isoDateParser.parse(dateStr);

        if (result == null) {
            if (numericalParsers == null) {
                numericalParsers = new ArrayList<>();
                addPatterns(numericalParsers, NUMERICAL_PATTERNS, locales);
            }
            result = parse(numericalParsers, dateStr, locale);

            if (result == null) {
                if (textParsers == null) {
                    textParsers = new ArrayList<>();
                    addPatterns(textParsers, TEXT_PATTERNS, locales);
                    addEnglish(textParsers, TEXT_PATTERNS, locales);
                }
                result = parse(textParsers, dateStr, locale);
            }
        }

        return result;
    }

    /**
     * Attempt to parse a date string.
     * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
     * If the year is missing, {@code null} is returned.
     *
     * @param dateStr     String to parse
     * @param firstLocale (optional) Locale to apply/try before the default list.
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    private LocalDateTime parse(@NonNull final Iterable<DateTimeFormatter> parsers,
                                @NonNull final CharSequence dateStr,
                                @Nullable final Locale firstLocale) {

        // Try the specified Locale first
        if (firstLocale != null) {
            for (final DateTimeFormatter dtf : parsers) {
                try {
                    return LocalDateTime.parse(dateStr, dtf.withLocale(firstLocale));
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignore and try the next one
                }
            }
        }

        // Parse with the default locales, using the default ResolverStyle
        for (final DateTimeFormatter dtf : parsers) {
            try {
                return LocalDateTime.parse(dateStr, dtf);
            } catch (@NonNull final DateTimeParseException ignore) {
                // ignore and try the next one
            }
        }

        return null;
    }

    /**
     * <strong>Add</strong> patterns to the given group.
     *
     * @param group    collection to add to
     * @param patterns list of patterns to add
     * @param locales  to use for number parsing
     */
    private void addPatterns(@NonNull final Collection<DateTimeFormatter> group,
                             @NonNull final String[] patterns,
                             @NonNull final List<Locale> locales) {
        // prevent duplicate locales
        final Collection<Locale> added = new HashSet<>();
        for (final Locale locale : locales) {
            if (!added.contains(locale)) {
                added.add(locale);
                for (final String pattern : patterns) {
                    final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .appendPattern(pattern)
                            // Allow the day of the month to be missing and use '1'
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            // Allow the time to be completely missing.
                            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);

                    group.add(builder.toFormatter(locale));
                }
            }
        }
    }

    /**
     * Create an English variant of a parser if English not already in the list of locales.
     */
    private void addEnglish(@SuppressWarnings("SameParameterValue")
                            @NonNull final Collection<DateTimeFormatter> group,
                            @SuppressWarnings("SameParameterValue")
                            @NonNull final String[] patterns,
                            @NonNull final List<Locale> locales) {

        final String english = Locale.ENGLISH.getISO3Language();

        final boolean add = locales.stream()
                                   .map(Locale::getISO3Language)
                                   .noneMatch(english::equals);
        if (add) {
            addPatterns(group, patterns, List.of(Locale.ENGLISH));
        }
    }
}
