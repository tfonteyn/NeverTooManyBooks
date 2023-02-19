/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.utils.dates;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.utils.LocaleListUtils;

/**
 * Parser for dates coming from the database or other sources
 * where we are certain of the format is ISO.
 * <p>
 * TEST: <a href="https://issuetracker.google.com/issues/158417777">DateTimeParseException</a>
 * seems to be fixed, but the bug was never closed?
 */
public class ISODateParser
        implements DateParser {

    /** List of patterns we'll use to parse ISO datetime stamps.. */
    private Collection<DateTimeFormatter> parsers;

    /**
     * Attempt to parse a date string using ISO parsers.
     * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
     * If the year is missing, {@code null} is returned.
     *
     * @param dateStr String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    @Override
    public LocalDateTime parse(@Nullable final String dateStr) {
        if (dateStr == null) {
            return null;
        }

        final int len = dateStr.length();
        // invalid lengths
        if (len < 4 || len == 5 || len == 6 || len == 8 || len == 9) {
            return null;
        }

        // Check the partial patterns first.
        try {
            switch (len) {
                case 4:
                    // yyyy
                    return Year.parse(dateStr).atDay(1).atStartOfDay();

                case 7:
                    // yyyy-MM
                    return YearMonth.parse(dateStr).atDay(1).atStartOfDay();

                case 10:
                    // yyyy-MM-dd
                    return LocalDate.parse(dateStr).atStartOfDay();

                default:
                    break;
            }
        } catch (@NonNull final DateTimeParseException ignore) {
            // ignore
        }

        if (parsers == null) {
            create();
        }
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
     * Create the parser list.
     */
    private void create() {
        parsers = new ArrayList<>();

        // '2011-12-03T10:15:30',
        // '2011-12-03T10:15:30+01:00'
        // '2011-12-03T10:15:30+01:00[Europe/Paris]'
        // Uses ResolverStyle.STRICT / IsoChronology.INSTANCE
        // This parser includes ISO_LOCAL_DATE_TIME
        parsers.add(DateTimeFormatter.ISO_DATE_TIME);

        /*
         * Variant of DateTimeFormatter.ISO_DATE_TIME using a space instead of the normal 'T'
         * '2011-12-03 10:15:30',
         * '2011-12-03 10:15:30+01:00'
         * '2011-12-03 10:15:30+01:00[Europe/Paris]'
         */
        final DateTimeFormatter sqliteIsoDateTime = new DateTimeFormatterBuilder()
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
                .toFormatter(LocaleListUtils.getSystemLocale());

        parsers.add(sqliteIsoDateTime);
    }
}
