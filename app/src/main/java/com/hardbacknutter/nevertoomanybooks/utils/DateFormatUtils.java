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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Date;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * https://developer.android.com/studio/releases/#4-0-0-desugar
 * <p>
 * Format a date/time in either ISO or 'display' format.
 */
public class DateFormatUtils {

    /** Log tag. */
    private static final String TAG = "DateFormatUtils";


    /**
     * Today, formatted as a ISO date-string, for the local timezone.
     * <p>
     * Should be used for dates directly related to the user (date acquired, date read, etc...)
     *
     * @return ISO date-string, i.e. 'yyyy-mm-dd'
     */
    @NonNull
    public static String isoLocalDateForToday() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Format a UTC epochMilli a a ISO date-string, for the local timezone.
     *
     * @param utcEpochMilli long value as milliseconds from the UTC Epoch.
     *
     * @return ISO date-string, i.e. 'yyyy-mm-dd'
     */
    @NonNull
    public static String isoLocalDate(final long utcEpochMilli) {
        return Instant.ofEpochMilli(utcEpochMilli).atZone(ZoneId.systemDefault())
                      .toLocalDate()
                      .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }


    /**
     * Today, formatted as a ISO date-string, for the UTC timezone.
     *
     * @return ISO date-string, i.e. 'yyyy-mm-dd'
     */
    @NonNull
    public static String isoUtcDateForToday() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Format a Date as a ISO date-string, for the UTC timezone.
     *
     * @param date to format
     *
     * @return ISO date-string, i.e. 'yyyy-mm-dd'
     */
    @NonNull
    public static String isoUtcDate(@NonNull final Date date) {
        // force time to noon, to avoid any overflow.
        date.setHours(12);
        date.setMinutes(0);
        date.setSeconds(0);
        return date.toInstant().atZone(ZoneOffset.UTC)
                   .toLocalDate()
                   .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }


    /**
     * Today, formatted as a ISO datetime-string, for the UTC timezone.
     *
     * @return ISO datetime-string, i.e. 'yyyy-mm-dd hh:mm:ss'
     */
    @NonNull
    public static String isoUtcDateTimeForToday() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Format a Date as a ISO datetime-string, for the UTC timezone.
     *
     * @param date to format
     *
     * @return ISO datetime-string, i.e. 'yyyy-mm-dd hh:mm:ss'
     */
    @NonNull
    public static String isoUtcDateTime(@NonNull final Date date) {
        return date.toInstant().atZone(ZoneOffset.UTC)
                   .toLocalDate()
                   .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Format a local epochMilli as a ISO datetime-string, for the UTC timezone.
     *
     * @param localEpochMilli to format
     *
     * @return ISO datetime-string, i.e. 'yyyy-mm-dd hh:mm:ss'
     */
    @NonNull
    public static String isoUtcDateTime(final long localEpochMilli) {
        return Instant.ofEpochMilli(localEpochMilli).atZone(ZoneOffset.UTC)
                      .toLocalDate()
                      .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }


    /**
     * Format the date components as a (partial) ISO format date string.
     *
     * @param year  0..9999, or {@code null} for none
     * @param month 1..12, or {@code null} for none
     * @param day   1..31, or {@code null} for none
     *
     * @return ISO (partial) date-string, i.e. 'yyyy-mm-dd' or 'yyyy-mm' or 'yyyy' or ''
     */
    @NonNull
    public static String isoPartialDate(@Nullable final Integer year,
                                        @Nullable final Integer month,
                                        @Nullable final Integer day) {
        if (year == null || year == 0) {
            return "";
        } else {
            String value = String.format(Locale.ENGLISH, "%04d", year);

            if (month != null && month > 0) {
                String mm = Integer.toString(month);
                if (mm.length() == 1) {
                    mm = '0' + mm;
                }

                value += '-' + mm;

                if (day != null && day > 0) {
                    String dd = Integer.toString(day);
                    if (dd.length() == 1) {
                        dd = '0' + dd;
                    }
                    value += '-' + dd;
                }
            }
            return value;
        }
    }


    /**
     * Pretty format a (potentially partial) ISO date to a date-string, using the specified locale.
     *
     * @param isoDateStr    ISO formatted date.
     * @param displayLocale to use
     *
     * @return human readable date string
     *
     * @throws NumberFormatException on failure to parse
     */
    public static String toPrettyDate(@NonNull final String isoDateStr,
                                      @NonNull final Locale displayLocale)
            throws NumberFormatException {

        if (isoDateStr.length() < 6) {
            // 0: empty
            // 1,3,5: invalid, no need to parse
            // 2: we *could* parse and add 1900/2000... but this is error prone; handle as invalid
            // 4: shortcut for input: YYYY
            // Any of the above: just return the incoming string
            return isoDateStr;

        } else if (isoDateStr.length() == 7) {
            // input: YYYY-MM
            final int month = Integer.parseInt(isoDateStr.substring(5));
            // just swap: MMM YYYY
            return DateFormatUtils.toPrettyMonthName(month, true, displayLocale) + ' ' + isoDateStr
                    .substring(0, 4);

        } else {
            // Try to parse for length == 6 or length >= 8
            final Date date = DateUtils.parseDate(isoDateStr);
            if (date != null) {
                return toPrettyDate(date, displayLocale);
            }
            // failed to parse
            if (BuildConfig.DEBUG /* always */) {
                Logger.e(TAG, "toPrettyDate=" + isoDateStr, new Throwable());
            }
            return isoDateStr;
        }
    }

    /**
     * Pretty format a date to a date-string, using the specified locale.
     *
     * @param date          to format
     * @param displayLocale to use
     *
     * @return human readable date string
     */
    @NonNull
    public static String toPrettyDate(@NonNull final Date date,
                                      @NonNull final Locale displayLocale) {
        return date.toInstant().atZone(ZoneId.systemDefault())
                   .toLocalDate()
                   .format(DateTimeFormatter
                                   .ofLocalizedDate(FormatStyle.MEDIUM)
                                   .withLocale(displayLocale));
    }

    /**
     * Pretty format a date to a datetime-string, using the specified locale.
     *
     * @param date          to format
     * @param displayLocale to use
     *
     * @return human readable datetime string
     */
    @NonNull
    public static String toPrettyDateTime(@NonNull final Date date,
                                          @NonNull final Locale displayLocale) {
        return date.toInstant().atZone(ZoneId.systemDefault())
                   .toLocalDate()
                   .format(DateTimeFormatter
                                   .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                                   .withLocale(displayLocale));
    }

    /**
     * Pretty format the name of the month for the given month number.
     *
     * @param month         1-12 based month number
     * @param shortName     {@code true} to get the abbreviated name instead of the full name.
     * @param displayLocale to use
     *
     * @return human readable localised name of Month
     */
    @NonNull
    public static String toPrettyMonthName(@IntRange(from = 1, to = 12) final int month,
                                           final boolean shortName,
                                           @NonNull final Locale displayLocale) {
        if (shortName) {
            return Month.of(month).getDisplayName(TextStyle.SHORT_STANDALONE, displayLocale);
        } else {
            return Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, displayLocale);
        }
    }
}
