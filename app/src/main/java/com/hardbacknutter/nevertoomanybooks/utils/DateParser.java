/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

public class DateParser {

    /** ISO patterns only. */
    private static final String[] ISO_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy-MM",
            };

    /** All numerical (i.e. Locale independent) patterns. */
    private static final String[] NUMERICAL = {
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
    private static final String[] TEXT = {
            // These are the wide spread common formats
            "dd-MMM-yyyy",
            "dd-MMMM-yyyy",
            "dd-MMM-yy",
            "dd-MMMM-yy",

            // Used by Goodreads; Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
            "EEE MMM dd HH:mm:ss ZZZZ yyyy",

            // Used by Amazon; e.g. "12 jan. 2017"
            "dd MMM. yyyy",

            // Used by OpenLibrary
            "dd MMM yyyy",
            "dd MMMM yyyy",
            "MMM d, yyyy",
            "MMMM d, yyyy",
            "MMM yyyy",
            "MMMM yyyy",
            };

    /**
     * Create the parser lists.
     *
     * @param locales the locales to use
     */
    public static void create(@NonNull final Locale... locales) {
        ALL.create(locales);
        ISO.create();
    }

    /**
     * <strong>Add</strong> patterns to the given group.
     *
     * @param group    collection to add to
     * @param patterns list of patterns to add
     * @param locales  to use
     */
    private static void addParsers(@NonNull final Collection<DateTimeFormatter> group,
                                   @NonNull final String[] patterns,
                                   @NonNull final Locale... locales) {
        // prevent duplicate locales
        final Collection<Locale> added = new HashSet<>();
        for (Locale locale : locales) {
            if (!added.contains(locale)) {
                added.add(locale);
                for (String pattern : patterns) {
                    final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .appendPattern(pattern)
                            .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);

                    group.add(builder.toFormatter(locale));
                }
            }
        }
    }

    /**
     * Attempt to parse a date string.
     * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
     * If the year is missing, {@code null} is returned.
     *
     * @param dateStr String to parse
     * @param locale  (optional) Locale to apply/try before the default list.
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    private static LocalDateTime parse(@NonNull final Iterable<DateTimeFormatter> parsers,
                                       @Nullable final String dateStr,
                                       @Nullable final Locale locale) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        // shortcut for plain 4 digit years.
        if (dateStr.length() == 4) {
            try {
                return Year.parse(dateStr).atDay(1).atTime(0, 0);
            } catch (@NonNull final DateTimeParseException ignore) {
                // ignored
            }
        }

        // Try the specified Locale first
        if (locale != null) {
            for (DateTimeFormatter dtf : parsers) {
                try {
                    return LocalDateTime.parse(dateStr, dtf.withLocale(locale));
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignore and try the next one
                }
            }
        }

        // Parse using the default ResolverStyle
        for (DateTimeFormatter dtf : parsers) {
            try {
                return LocalDateTime.parse(dateStr, dtf);
            } catch (@NonNull final DateTimeParseException ignore) {
                // ignore and try the next one
            }
        }

        // 2020-06-02: disabled 'lenient' as it seems to cause more false results then good
//        // Try again being lenient (ResolverStyle.LENIENT)
//        for (DateTimeFormatter dtf : parsers) {
//            try {
//                // Keep in mind this creates a new copy of the formatter.
//                return LocalDateTime.parse(dateStr, dtf.withResolverStyle(ResolverStyle.LENIENT));
//            } catch (@NonNull final DateTimeParseException ignore) {
//                // ignore and try the next one
//            }
//        }

        return null;
    }

    /**
     * Parses generic date and date-time strings.
     */
    public static final class ALL {

        /** List of patterns we'll use to parse dates. */
        private static final Collection<DateTimeFormatter> PARSERS = new ArrayList<>();

        public static void create(@NonNull final Locale... locales) {
            final Locale systemLocale = LocaleUtils.getSystemLocale();
            PARSERS.clear();
            addParsers(PARSERS, NUMERICAL, systemLocale);
            addParsers(PARSERS, ISO_PATTERNS, systemLocale);
            addParsers(PARSERS, TEXT, locales);
            addEnglish(PARSERS, TEXT, locales);
        }

        /**
         * add english if not already in the list of locales.
         */
        private static void addEnglish(@SuppressWarnings("SameParameterValue")
                                       @NonNull final Collection<DateTimeFormatter> group,
                                       @SuppressWarnings("SameParameterValue")
                                       @NonNull final String[] patterns,
                                       @NonNull final Locale[] locales) {
            boolean hasEnglish = false;
            for (Locale locale : locales) {
                if (Locale.ENGLISH.equals(locale)) {
                    hasEnglish = true;
                    break;
                }
            }
            if (!hasEnglish) {
                addParsers(group, patterns, Locale.ENGLISH);
            }
        }

        /**
         * Attempt to parse a date string using the passed locale.
         * This method is meant to be used by site-specific code where the site Locale is known.
         *
         * @param locale  to try first; i.e. before the pre-defined list.
         * @param dateStr String to parse
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        public static LocalDateTime parse(@NonNull final Locale locale,
                                          @NonNull final String dateStr) {
            return DateParser.parse(PARSERS, dateStr, locale);
        }

        /**
         * Attempt to parse a date string.
         *
         * @param dateStr String to parse
         *
         * @return Resulting date if parsed, otherwise {@code null}
         */
        @Nullable
        public static LocalDateTime parse(@Nullable final String dateStr) {
            return DateParser.parse(PARSERS, dateStr, null);
        }
    }

    /**
     * Parses ISO formatted date and date-time strings.
     */
    public static final class ISO {

        /** List of patterns we'll use to parse ISO datetime stamps.. */
        private static final Collection<DateTimeFormatter> PARSERS = new ArrayList<>();

        public static void create() {
            PARSERS.clear();
            addParsers(PARSERS, ISO_PATTERNS, LocaleUtils.getSystemLocale());
        }

        /**
         * Attempt to parse a date string.
         * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
         * If the year is missing, {@code null} is returned.
         *
         * @param dateStr String to parse
         *
         * @return Resulting date if parsed, otherwise {@code null}
         */
        @Nullable
        public static LocalDateTime parse(@Nullable final String dateStr) {
            return DateParser.parse(PARSERS, dateStr, null);
        }
    }
}
