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
package com.hardbacknutter.nevertoomanybooks.core.parsers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Parser for dates coming from the database or other sources
 * where we are certain the format is ISO.
 * Negative years are fully supported.
 * <p>
 * The result is always a {@link LocalDateTime}.
 * <p>
 * Typical use is Date or DateTime timestamps.
 * Partial-date patters <strong>are accepted</strong>
 * but will be completed with "01" for the missing parts.
 * A missing time part is set to midnight, 00:00, at the start of this date.
 */
public class ISODateParser
        implements DateParser<LocalDateTime> {

    @NonNull
    private final Locale locale;
    @Nullable
    private DateTimeFormatter sqliteIsoDateTime;

    /**
     * Constructor.
     *
     * @param systemLocale to use for parsing
     */
    public ISODateParser(@NonNull final Locale systemLocale) {
        this.locale = systemLocale;
    }

    /**
     * Parse the date string based on exact known length patterns.
     * <p>
     * For optimum performance, do NOT call for length: 6, 9, or outside of the 4..11 range.
     *
     * @param dateStr to parse
     * @param len     the length of dateStr
     *
     * @return result
     */
    @NonNull
    private static Optional<LocalDateTime> parseLenBased(@NonNull final CharSequence dateStr,
                                                         final int len) {
        try {
            switch (len) {
                case 4: {
                    // yyyy
                    return Optional.of(Year.parse(dateStr).atDay(1).atStartOfDay());
                }
                case 5: {
                    // -yyyy
                    if (dateStr.charAt(0) != '-') {
                        return Optional.empty();
                    }
                    return Optional.of(Year.parse(dateStr).atDay(1).atStartOfDay());
                }
                case 7: {
                    // yyyy-MM
                    return Optional.of(YearMonth.parse(dateStr).atDay(1).atStartOfDay());
                }
                case 8: {
                    // -yyyy-MM
                    if (dateStr.charAt(0) != '-') {
                        return Optional.empty();
                    }
                    return Optional.of(YearMonth.parse(dateStr).atDay(1).atStartOfDay());
                }
                case 10: {
                    // yyyy-MM-dd
                    return Optional.of(LocalDate.parse(dateStr).atStartOfDay());
                }
                case 11: {
                    // -yyyy-MM-dd
                    if (dateStr.charAt(0) != '-') {
                        return Optional.empty();
                    }
                    return Optional.of(LocalDate.parse(dateStr).atStartOfDay());
                }
                default:
                    break;
            }
        } catch (@NonNull final DateTimeParseException ignore) {
            // ignore
        }
        return Optional.empty();
    }

    /**
     * Attempt to parse a date string using ISO parsers.
     * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
     * If the year is missing, {@code Optional.empty()} is returned.
     *
     * @param dateStr String to parse
     *
     * @return Resulting date if parsed, otherwise {@code Optional.empty()}
     */
    @NonNull
    @Override
    public Optional<LocalDateTime> parse(@Nullable final CharSequence dateStr) {
        if (dateStr == null) {
            return Optional.empty();
        }

        // Fast fail
        final int len = dateStr.length();
        if (len < 4) {
            return Optional.empty();
        }

        // Don't try invalid lengths for parseLenBased
        if (len != 6 && len != 9 && len < 12) {
            // try parsing based on the length of the string
            final Optional<LocalDateTime> lenBasedResult = parseLenBased(dateStr, len);
            if (lenBasedResult.isPresent()) {
                return lenBasedResult;
            }
        }

        // secondly, try full parsing for more complicated patterns.

        // Time separator at position 10 (or 11 for negative year -yyyy)
        final int sepPos = (dateStr.charAt(0) == '-') ? 11 : 10;
        // sanity check
        if (len <= sepPos) {
            return Optional.empty();
        }

        // ISO-8601 Standard; with a 'T' between date and time
        final char sep = dateStr.charAt(sepPos);
        if (sep == 'T' || sep == 't') {
            try {
                return Optional.of(LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME));
            } catch (@NonNull final DateTimeParseException ignore) {
                return Optional.empty();
            }
        }

        if (sep == ' ') {
            if (sqliteIsoDateTime == null) {
                createSQLiteDateTimeFormatter();
            }
            // SQLite Standard; with a ' ' between date and time
            try {
                return Optional.of(LocalDateTime.parse(dateStr, sqliteIsoDateTime));
            } catch (@NonNull final DateTimeParseException ignore) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private void createSQLiteDateTimeFormatter() {
        /*
         * Variant of DateTimeFormatter.ISO_DATE_TIME using a space instead of the normal 'T'
         * '2011-12-03 10:15:30',
         * '2011-12-03 10:15:30+01:00'
         * '2011-12-03 10:15:30+01:00[Europe/Paris]'
         */
        sqliteIsoDateTime = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                // A space instead of the normal 'T'
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalStart()
                .appendLiteral('[')
                .parseCaseSensitive()
                .appendZoneRegionId()
                .appendLiteral(']')
                // Uses ResolverStyle.SMART and 'null' Chronology
                .toFormatter(locale);
    }
}
