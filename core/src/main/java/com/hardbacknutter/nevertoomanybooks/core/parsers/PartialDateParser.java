/*
 * @Copyright 2018-2024 HardBackNutter
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.core.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * TODO: implement/change the DateParser interface to allow generic return types.
 * <p>
 * TODO: fold all manual parsers into one or more DateTimeFormatter pattern
 */
public class PartialDateParser {

    private static final String TAG = "PartialDateParser";

    private static final Pattern PATTERN_YYYY =
            Pattern.compile("^(\\d\\d\\d\\d)$");
    private static final Pattern PATTERN_YYYY_MM =
            Pattern.compile("^(\\d\\d\\d\\d)[\\s/-](\\d{1,2})$");
    private static final Pattern PATTERN_YYYY_MM_DD_TIMESTAMP =
            Pattern.compile("^(\\d\\d\\d\\d)[\\s/-](\\d{1,2})[/-](\\d{1,2}).*");

    private static final Pattern PATTERN_MM_YYYY =
            Pattern.compile("^(\\d{1,2})[\\s/-](\\d\\d\\d\\d)$");

    private static final Pattern PATTERN_MMM_YYYY =
            Pattern.compile("^(.*)[\\s/-](\\d\\d\\d\\d)$");

    /** Used to transform SQL-ISO to Java-ISO datetime format for UTC conversions. */
    private static final Pattern SPACE = Pattern.compile(" ");

    @NonNull
    public Optional<PartialDate> parse(@Nullable final CharSequence dateStr) {
        return parse(dateStr, null, false);
    }

    @NonNull
    public Optional<PartialDate> parse(@Nullable final CharSequence dateStr,
                                       @Nullable final Locale locale) {
        return parse(dateStr, locale, false);
    }

    /**
     * Attempt to parse a date string.
     * <p>
     * Parsing is restricted to these formats:
     * <ul>
     *     <li>yyyy-MM-dd[...]</li>
     *     <li>yyyy-MM with MM being 1 or 2 digit </li>
     *     <li>yyyy</li>
     *     <li>Month-yyyy</li>
     *     <li>digit dividers can be {@code space}, {@code -} or {@code /}</li>
     *     <li>Month {@code MM} can be one or two digits; 01..12  or 1..9</li>
     *     <li>Day {@code dd} can be one or two digits; 01..31  or 1..9</li>
     * </ul>
     *
     * @param dateStr a pattern as above, or {@code null}, or {@code ""}
     * @param locale  (optional) Locale to try to decode month names.
     *                If set to {@code null} we'll use {@code Locale.ENGLISH}.
     * @param isUtc   Set to {@code true} if dates are to be converted from UTC
     *                to the local timezone.
     *                Set to {@code false} to use the date is used as-is,
     *                i.e. in the current timezone.
     *
     * @return Resulting date if parsed, otherwise {@code Optional.empty()}
     */
    @NonNull
    public Optional<PartialDate> parse(@Nullable final CharSequence dateStr,
                                       @Nullable final Locale locale,
                                       final boolean isUtc) {
        if (dateStr == null || dateStr.length() == 0) {
            return Optional.empty();
        }

        final LocalDate localDate;
        Matcher matcher;
        try {
            matcher = PATTERN_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group()).atDay(1);
                return Optional.of(new PartialDate(localDate.getYear(), 0, 0));
            }

            matcher = PATTERN_YYYY_MM.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(1)).atDay(1);
                final int month = Integer.parseInt(matcher.group(2));
                return Optional.of(new PartialDate(localDate.getYear(), month, 0));
            }

            matcher = PATTERN_MM_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(2)).atDay(1);
                final int month = Integer.parseInt(matcher.group(1));
                return Optional.of(new PartialDate(localDate.getYear(), month, 0));
            }

            matcher = PATTERN_YYYY_MM_DD_TIMESTAMP.matcher(dateStr);
            if (matcher.find()) {
                if (isUtc) {
                    // full date match with an optional timestamp; simply pass the whole group
                    localDate = LocalDateTime
                            .parse(SPACE.matcher(matcher.group()).replaceFirst("T"))
                            .atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate();
                } else {
                    // reconstruct using the Y,M,D groups
                    localDate = LocalDate.of(Integer.parseInt(matcher.group(1)),
                                             Integer.parseInt(matcher.group(2)),
                                             Integer.parseInt(matcher.group(3)));
                }
                return Optional.of(new PartialDate(localDate));
            }

            matcher = PATTERN_MMM_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(2)).atDay(1);
                final Locale withLocale = Objects.requireNonNullElse(locale, Locale.ENGLISH);
                int monthNumber = 0;
                final String monthStr = matcher.group(1);
                try {
                    // We check for these 3 different patterns...
                    // LLL     3      appendText(ChronoField.MONTH_OF_YEAR,
                    //                          TextStyle.SHORT_STANDALONE)
                    // LLLL    4      appendText(ChronoField.MONTH_OF_YEAR,
                    //                          TextStyle.FULL_STANDALONE)
                    // LLLLL   5      appendText(ChronoField.MONTH_OF_YEAR,
                    //                          TextStyle.NARROW_STANDALONE)
                    monthNumber = new DateTimeFormatterBuilder()
                            .parseLenient()
                            .parseCaseInsensitive()
                            .appendPattern("[LLLL][LLL][LLLLL]")
                            .toFormatter(withLocale)
                            .parse(monthStr)
                            .get(ChronoField.MONTH_OF_YEAR);
                } catch (DateTimeParseException | NumberFormatException e) {
                    if (BuildConfig.DEBUG /* always */) {
                        LoggerFactory.getLogger().e(TAG, e, "monthStr=" + monthStr);
                    }
                }

                return Optional.of(new PartialDate(localDate.getYear(), monthNumber, 0));
            }

        } catch (@NonNull final DateTimeParseException | NumberFormatException e) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().e(TAG, e, "dateStr=" + dateStr);
            }
        }

        return Optional.empty();
    }
}
