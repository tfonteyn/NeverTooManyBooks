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
 * Parser for dates coming from the database or other sources
 * where we are certain the format is ISO.
 * Years can be prefixed with '+' and '-'.
 * <p>
 * In addition, this parser also accept the following, but only with positive 4 digit years.
 * - MMM_YYYY and DD_MMM_YYYY: where MMM is a short or long alpha month string.
 * - MM_YYYY: specifically only a numeric 1 or 2-digit month + a 4 digit year
 * <p>
 * The result is always a {@link PartialDate}.
 * <p>
 * Typical use is for publication type dates.
 */
public class PartialDateParser
        implements DateParser<PartialDate> {

    private static final String TAG = "PartialDateParser";

    /**
     * Numeric 4-digit year. Negative years supported.
     */
    private static final Pattern PATTERN_YYYY =
            Pattern.compile("^(-?\\d{1,4})$");
    /**
     * Numeric 4-digit year. Negative years supported.
     * Followed by 1 or 2-digit month.
     */
    private static final Pattern PATTERN_YYYY_MM =
            Pattern.compile("^(-?\\d{1,4})[\\s/-](\\d{1,2})$");
    /**
     * Numeric 4-digit year. Negative years supported.
     * Followed by 1 or 2-digit month.
     */
    private static final Pattern PATTERN_YYYY_MM_DD_TIMESTAMP =
            Pattern.compile("^(-?\\d{1,4})[\\s/-](\\d{1,2})[/-](\\d{1,2}).*");
    /**
     * Numeric 1 or 2-digit month.
     * Followed by 4 digit year. Positive only.
     */
    private static final Pattern PATTERN_MM_YYYY =
            Pattern.compile("^(\\d{1,2})[\\s/-](\\d\\d\\d\\d)$");
    /**
     * Alpha month various formats.
     * Followed by 4 digit year. Positive only.
     */
    private static final Pattern PATTERN_MMM_YYYY =
            Pattern.compile("^(\\D.*)[\\s/-](\\d\\d\\d\\d)$");
    /**
     * Numeric 1 or 2 digit day.
     * Followed by alpha month various formats.
     * Followed by 4 digit year. Positive only.
     */
    private static final Pattern PATTERN_DD_MMM_YYYY =
            Pattern.compile("^(\\d{1,2})[\\s/-](.*)[\\s/-](\\d\\d\\d\\d)$");

    /** Used to transform SQL-ISO to Java-ISO datetime format for UTC conversions. */
    private static final Pattern SPACE = Pattern.compile(" ");

    @NonNull
    private static Optional<PartialDate> parseLenBased(@NonNull final CharSequence dateStr) {

        final int len = dateStr.length();
        // invalid lengths
        if (len < 4 || len == 6 || len == 9) {
            return Optional.empty();
        }

        try {
            switch (len) {
                case 4: {
                    // yyyy
                    return Optional.of(new PartialDate(Year.parse(dateStr).getValue(), 0, 0));
                }
                case 5: {
                    // -yyyy
                    if (dateStr.charAt(0) != '-') {
                        return Optional.empty();
                    }
                    return Optional.of(new PartialDate(Year.parse(dateStr).getValue(), 0, 0));
                }
                case 7: {
                    // yyyy-MM
                    final LocalDateTime dt = YearMonth.parse(dateStr).atDay(1).atStartOfDay();
                    return Optional.of(new PartialDate(dt.getYear(),
                                                       dt.getMonthValue(),
                                                       0));
                }
                case 8: {
                    // -yyyy-MM
                    if (dateStr.charAt(0) != '-') {
                        return Optional.empty();
                    }
                    final LocalDateTime dt = YearMonth.parse(dateStr).atDay(1).atStartOfDay();
                    return Optional.of(new PartialDate(dt.getYear(),
                                                       dt.getMonthValue(),
                                                       0));
                }
                case 10: {
                    // yyyy-MM-dd
                    final LocalDateTime dt = LocalDate.parse(dateStr).atStartOfDay();
                    return Optional.of(new PartialDate(dt.getYear(),
                                                       dt.getMonthValue(),
                                                       dt.getDayOfMonth()));
                }
                case 11: {
                    // -yyyy-MM-dd
                    if (dateStr.charAt(0) != '-') {
                        return Optional.empty();
                    }
                    final LocalDateTime dt = LocalDate.parse(dateStr).atStartOfDay();
                    return Optional.of(new PartialDate(dt.getYear(),
                                                       dt.getMonthValue(),
                                                       dt.getDayOfMonth()));
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
     * Parse a string into a month number.
     *
     * @param monthStr (optional) to parse; when missing {@code 0} is returned
     * @param locale   (optional) Locale to try to decode month names.
     *                 If set to {@code null} we'll use {@code Locale.ENGLISH}.
     *
     * @return month 1..12, or {@code 0} on parsing error
     */
    private static int parseMonth(@Nullable final String monthStr,
                                  @Nullable final Locale locale) {
        if (monthStr == null || monthStr.isEmpty()) {
            return 0;
        }

        int monthNumber = 0;
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
                    .toFormatter(Objects.requireNonNullElse(locale, Locale.ENGLISH))
                    .parse(monthStr)
                    .get(ChronoField.MONTH_OF_YEAR);
        } catch (DateTimeParseException | NumberFormatException e) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().d(TAG, "parseMonth", "monthStr=" + monthStr,
                                            e.getMessage());
            }
        }
        return monthNumber;
    }

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
     * See the Pattern definitions in the class for supported formats.
     * <ul>
     *     <li>digit separators can be {@code space}, {@code -} or {@code /}</li>
     *     <li>Month {@code MM} can be one or two digits; 01..12  or 1..9</li>
     *     <li>Day {@code dd} can be one or two digits; 01..31  or 1..9</li>
     * </ul>
     *
     * @param date   a pattern as above, or {@code null}, or {@code ""}
     * @param locale (optional) Locale to try to decode month names.
     *               If set to {@code null} we'll use {@code Locale.ENGLISH}.
     * @param isUtc  Set to {@code true} if dates are to be converted from UTC
     *               to the local timezone.
     *               Set to {@code false} to use the date is used as-is,
     *               i.e. in the current timezone.
     *
     * @return Resulting date if parsed, otherwise {@code Optional.empty()}
     */
    @NonNull
    public Optional<PartialDate> parse(@Nullable final CharSequence date,
                                       @Nullable final Locale locale,
                                       final boolean isUtc) {
        if (date == null) {
            return Optional.empty();
        }

        // Cut off any leading '+'; easiest to handle
        String dateStr = date.toString();
        if (dateStr.startsWith("+")) {
            dateStr = dateStr.substring(1);
        }

        if (dateStr.isEmpty()) {
            return Optional.empty();
        }

        // Try a fast and dirty parse step based on the length of the string.
        // This should handle all pure ISO formats (partial and full)
        // containing Year/Month/Day fields only
        final Optional<PartialDate> lenBasedResult = parseLenBased(dateStr);
        if (lenBasedResult.isPresent()) {
            return lenBasedResult;
        }

        // secondly, try full parsing.
        final LocalDate localDate;
        Matcher matcher;
        try {
            matcher = PATTERN_YYYY_MM_DD_TIMESTAMP.matcher(dateStr);
            if (matcher.find()) {
                final int year = Integer.parseInt(matcher.group(1));
                final int month = Integer.parseInt(matcher.group(2));
                final int day = Integer.parseInt(matcher.group(3));

                if (isUtc && month > 0 && day > 0) {
                    // Full date match with an optional timestamp; simply pass the whole group
                    // Creating the LocalDateTime object will automatically adjust for timezones.
                    localDate = LocalDateTime
                            .parse(SPACE.matcher(matcher.group()).replaceFirst("T"))
                            .atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate();
                    return Optional.of(new PartialDate(localDate));
                }
                // We do not requiring timezone adjust OR one of the date values was zero.
                // Construct directly from the Y,M,D groups.
                // This allows for 00 months/days.
                return Optional.of(new PartialDate(year, month, day));
            }

            matcher = PATTERN_MM_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(2)).atDay(1);
                final int month = Integer.parseInt(matcher.group(1));
                return Optional.of(new PartialDate(localDate.getYear(), month, 0));
            }

            matcher = PATTERN_MMM_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(2)).atDay(1);
                final int monthNumber = parseMonth(matcher.group(1), locale);
                return Optional.of(new PartialDate(localDate.getYear(), monthNumber, 0));
            }
            matcher = PATTERN_DD_MMM_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(3)).atDay(1);
                final int monthNumber = parseMonth(matcher.group(2), locale);
                final int dayNumber = Integer.parseInt(matcher.group(1));
                return Optional.of(new PartialDate(localDate.getYear(), monthNumber, dayNumber));
            }

            // Paranoia: already handled by #parseLenBased
            matcher = PATTERN_YYYY.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group()).atDay(1);
                return Optional.of(new PartialDate(localDate.getYear(), 0, 0));
            }

            // Paranoia: already handled by #parseLenBased
            matcher = PATTERN_YYYY_MM.matcher(dateStr);
            if (matcher.find()) {
                localDate = Year.parse(matcher.group(1)).atDay(1);
                final int month = Integer.parseInt(matcher.group(2));
                return Optional.of(new PartialDate(localDate.getYear(), month, 0));
            }

        } catch (@NonNull final DateTimeParseException | NumberFormatException e) {
            if (BuildConfig.DEBUG /* always */) {
                LoggerFactory.getLogger().e(TAG, e, "dateStr=" + dateStr);
            }
        }

        return Optional.empty();
    }
}
