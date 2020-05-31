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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

public class DateParser {

    /** {@link DATE}. */
    private static final String[] NUMERICAL_DATE = {
            "MM-dd-yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd",
            "yyyy-MM",
            };

    /** {@link DATETIME}. */
    private static final String[] NUMERICAL_DATE_TIME = {
            "MM-dd-yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",

            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            };

    /** {@link DATE} + {@link DATETIME}. */
    private static final String[] TEXT_DATE = {
            "dd-MMM-yyyy",
            "dd-MMMM-yyyy",
            "dd-MMM-yy",
            "dd-MMMM-yy",

            // Amazon: 12 jan. 2017
            "dd MMM. yyyy",

            // OpenLibrary
            "dd MMM yyyy",
            "dd MMMM yyyy",
            "MMM d, yyyy",
            "MMMM d, yyyy",
            "MMM yyyy",
            "MMMM yyyy",

            // Not sure these are really needed.
            // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
            "EEE MMM dd HH:mm:ss ZZZZ yyyy",
            "EEE MMM dd HH:mm ZZZZ yyyy",
            "EEE MMM dd ZZZZ yyyy",
            };

    /** {@link ISO} */
    private static final String[] ISO_DATE_TIME = {
            "yyyy-MM-dd",
            "yyyy-MM",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            };

    /**
     * Create the parser lists.
     *
     * @param locales the locales to use
     */
    public static void create(@NonNull final Locale... locales) {
        DATE.create(locales);
        DATETIME.create(locales);
        ISO.create(locales);
    }

    /**
     * add english if not already in the list of locales.
     */
    private static void addEnglish(@NonNull final Collection<DateTimeFormatter> group,
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
            addFormats(group, patterns, Locale.ENGLISH);
        }
    }

    /**
     * <strong>Add</strong> patterns to the given group.
     *
     * @param group    collection to add to
     * @param patterns list of patterns to add
     * @param locales  to use
     */
    private static void addFormats(@NonNull final Collection<DateTimeFormatter> group,
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

    public static final class DATE {

        /** List of patterns we'll use to parse dates. */
        private static final Collection<DateTimeFormatter> PARSERS = new ArrayList<>();

        public static void create(@NonNull final Locale... locales) {
            PARSERS.clear();
            addFormats(PARSERS, NUMERICAL_DATE, Locale.getDefault());
            addFormats(PARSERS, TEXT_DATE, locales);
            addEnglish(PARSERS, TEXT_DATE, locales);
        }

        /**
         * Attempt to parse a date string using the passed locale.
         *
         * @param locale  to use
         * @param dateStr String to parse
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        public static LocalDate parse(@NonNull final Locale locale,
                                      @NonNull final String dateStr) {
            // URGENT: parse should use passed locale FIRST
            return parse(dateStr);
        }

        /**
         * Attempt to parse a date string.
         *
         * @param dateStr String to parse
         *
         * @return Resulting date if parsed, otherwise {@code null}
         */
        @Nullable
        public static LocalDate parse(@Nullable final String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            // shortcut for plain 4 digit years.
            if (dateStr.length() == 4) {
                try {
                    return Year.parse(dateStr).atDay(1);
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignored
                }
            }

            // First try to parse using default rules (ResolverStyle.SMART)
            final LocalDate date = parse(dateStr, false);
            if (date != null) {
                return date;
            }
            // Try again being lenient (ResolverStyle.LENIENT)
            return parse(dateStr, true);
        }

        /**
         * Attempt to parse a date string;
         * Allow the caller to specify if the parsing should be smart (default) or lenient.
         *
         * @param dateStr String to parse
         * @param lenient {@code true} if parsing should be lenient
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        @Nullable
        private static LocalDate parse(@NonNull final CharSequence dateStr,
                                       final boolean lenient) {
            for (DateTimeFormatter dtf : PARSERS) {
                try {
                    if (!lenient) {
                        return LocalDate.parse(dateStr, dtf);
                    } else {
                        // Keep in mind this creates a new copy of the formatter.
                        return LocalDate.parse(dateStr,
                                               dtf.withResolverStyle(ResolverStyle.LENIENT));
                    }
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignore
                }
            }
            return null;
        }
    }

    public static final class DATETIME {

        /** List of patterns we'll use to parse datetime stamps. */
        private static final Collection<DateTimeFormatter> PARSERS = new ArrayList<>();

        public static void create(@NonNull final Locale... locales) {
            PARSERS.clear();
            addFormats(PARSERS, NUMERICAL_DATE, Locale.getDefault());
            addFormats(PARSERS, NUMERICAL_DATE_TIME, locales);
            addFormats(PARSERS, TEXT_DATE, locales);
            addEnglish(PARSERS, TEXT_DATE, locales);
        }

        /**
         * Attempt to parse a date string using the passed locale.
         *
         * @param locale  to use
         * @param dateStr String to parse
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        public static LocalDateTime parse(@NonNull final Locale locale,
                                          @NonNull final String dateStr) {
            // URGENT: parse should use passed locale FIRST
            return parse(dateStr);
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
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            // shortcut for plain 4 digit years.
            if (dateStr.length() == 4) {
                try {
                    return Year.parse(dateStr).atDay(1).atTime(12, 0);
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignored
                }
            }

            // First try to parse using default rules (ResolverStyle.SMART)
            final LocalDateTime date = parse(dateStr, false);
            if (date != null) {
                return date;
            }
            // Try again being lenient (ResolverStyle.LENIENT)
            return parse(dateStr, true);
        }

        /**
         * Attempt to parse a date string;
         * Allow the caller to specify if the parsing should be smart (default) or lenient.
         *
         * @param dateStr String to parse
         * @param lenient {@code true} if parsing should be lenient
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        @Nullable
        private static LocalDateTime parse(@NonNull final CharSequence dateStr,
                                           final boolean lenient) {
            for (DateTimeFormatter dtf : PARSERS) {
                try {
                    if (!lenient) {
                        return LocalDateTime.parse(dateStr, dtf);
                    } else {
                        // Keep in mind this creates a new copy of the formatter.
                        return LocalDateTime.parse(dateStr,
                                                   dtf.withResolverStyle(ResolverStyle.LENIENT));
                    }
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignore
                }
            }
            return null;
        }
    }

    public static final class ISO {

        /** List of patterns we'll use to parse ISO datetime stamps.. */
        private static final Collection<DateTimeFormatter> PARSERS = new ArrayList<>();

        public static void create(@SuppressWarnings("unused")
                                  @NonNull final Locale... locales) {
            PARSERS.clear();
            addFormats(PARSERS, ISO_DATE_TIME, Locale.getDefault());
        }

        /**
         * Attempt to parse a date string using the passed locale.
         *
         * @param locale  to use
         * @param dateStr String to parse
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        public static LocalDateTime parse(@NonNull final Locale locale,
                                          @NonNull final String dateStr) {
            // URGENT: parse should use passed locale FIRST
            return parse(dateStr);
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
            if (dateStr == null || dateStr.isEmpty()) {
                return null;
            }
            // shortcut for plain 4 digit years.
            if (dateStr.length() == 4) {
                try {
                    return Year.parse(dateStr).atDay(1).atTime(12, 0);
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignored
                }
            }

            // First try to parse using default rules (ResolverStyle.SMART)
            final LocalDateTime date = parse(dateStr, false);
            if (date != null) {
                return date;
            }
            // Try again being lenient (ResolverStyle.LENIENT)
            return parse(dateStr, true);
        }

        /**
         * Attempt to parse a date string;
         * Allow the caller to specify if the parsing should be smart (default) or lenient.
         *
         * @param dateStr String to parse
         * @param lenient {@code true} if parsing should be lenient
         *
         * @return Resulting date if successfully parsed, otherwise {@code null}
         */
        @Nullable
        private static LocalDateTime parse(@NonNull final CharSequence dateStr,
                                           final boolean lenient) {
            for (DateTimeFormatter dtf : PARSERS) {
                try {
                    if (!lenient) {
                        return LocalDateTime.parse(dateStr, dtf);
                    } else {
                        // Keep in mind this creates a new copy of the formatter.
                        return LocalDateTime.parse(dateStr,
                                                   dtf.withResolverStyle(ResolverStyle.LENIENT));
                    }
                } catch (@NonNull final DateTimeParseException ignore) {
                    // ignore
                }
            }
            return null;
        }
    }
}
