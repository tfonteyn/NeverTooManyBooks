/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.annotation.SuppressLint;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.debug.Logger;

public final class DateUtils {

    /* ----------------------------- FORMATTING ------------------------------------------------- */

    /** Month full names cache for each Locale. */
    private static final Map<String, String[]> MONTH_LONG_NAMES = new HashMap<>();
    /** Month abbreviated names cache for each Locale. */
    private static final Map<String, String[]> MONTH_SHORT_NAMES = new HashMap<>();

    /**
     * SQL Date formatter, System Locale.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat LOCAL_SQL_DATE;

    /**
     * SQL Date formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE;

    /**
     * SQL Datetime (no seconds) formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_HH_MM;

    /**
     * SQL Datetime (with seconds) formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_HH_MM_SS;


    /* ------------------------------ PARSING --------------------------------------------------- */

    /** List of formats we'll use to parse dates. */
    private static final ArrayList<SimpleDateFormat> PARSE_DATE_FORMATS;

    /** Simple match for a 4 digit year. */
    private static final SimpleDateFormat YEAR =
            new SimpleDateFormat("yyyy", LocaleUtils.getSystemLocale());


    static {
        // Used for formatting *user* dates, in the locale timezone, for SQL. e.g. date read...
        LOCAL_SQL_DATE =
                new SimpleDateFormat("yyyy-MM-dd", LocaleUtils.getSystemLocale());

        // Used for formatting *non-user* dates for SQL. e.g. publication dates...
        TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
        UTC_SQL_DATE_HH_MM_SS =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", LocaleUtils.getSystemLocale());
        UTC_SQL_DATE_HH_MM_SS.setTimeZone(TZ_UTC);
        UTC_SQL_DATE_HH_MM =
                new SimpleDateFormat("yyyy-MM-dd HH:mm", LocaleUtils.getSystemLocale());
        UTC_SQL_DATE_HH_MM.setTimeZone(TZ_UTC);
        UTC_SQL_DATE =
                new SimpleDateFormat("yyyy-MM-dd", LocaleUtils.getSystemLocale());
        UTC_SQL_DATE.setTimeZone(TZ_UTC);
    }

    static {

        // create the parser list. These will be tried IN THE ORDER DEFINED HERE.

        // 2019-05-04: there are 21 formats, setting capacity to 22.
        PARSE_DATE_FORMATS = new ArrayList<>(22);

        // pure numerical formats
        addParseDateFormat("MM-dd-yyyy HH:mm:ss", false);
        addParseDateFormat("MM-dd-yyyy HH:mm", false);
        addParseDateFormat("MM-dd-yyyy", false);

        addParseDateFormat("dd-MM-yyyy HH:mm:ss", false);
        addParseDateFormat("dd-MM-yyyy HH:mm", false);
        addParseDateFormat("dd-MM-yyyy", false);

        // SQL date formats, pure numerical
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_HH_MM_SS);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_HH_MM);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE);


        // add english if the user's System Locale is not English.
        // This is done because internet sites we search are english.
        final boolean addEnglish = !Objects.equals(Locale.ENGLISH.getISO3Language(),
                                                   LocaleUtils.getSystemLocale().getISO3Language());
        //FIXME: these are created at startup, so do not support switching Locale on the fly.
        // the month is (localized) text, or english
        addParseDateFormat("dd-MMM-yyyy HH:mm:ss", addEnglish);
        addParseDateFormat("dd-MMM-yyyy HH:mm", addEnglish);
        addParseDateFormat("dd-MMM-yyyy", addEnglish);

        addParseDateFormat("dd-MMM-yy HH:mm:ss", addEnglish);
        addParseDateFormat("dd-MMM-yy HH:mm", addEnglish);
        addParseDateFormat("dd-MMM-yy", addEnglish);

        // "13 March 2009" added due to OpenLibrary
        addParseDateFormat("dd MMM yyyy", addEnglish);
        // "January 12, 1987" added due to OpenLibrary
        addParseDateFormat("MMM d, yyyy", addEnglish);

        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy", addEnglish);
        addParseDateFormat("EEE MMM dd HH:mm ZZZZ yyyy", addEnglish);
        addParseDateFormat("EEE MMM dd ZZZZ yyyy", addEnglish);

        // TEST: PARTIAL format... "March 2009" added due to OpenLibrary
        addParseDateFormat("MMM yyyy", addEnglish);
    }

    private DateUtils() {
    }

    /**
     * Add a format to the parser list. It's always added in the System Locale format,
     * and optionally in English.
     *
     * @param format     date format to add
     * @param addEnglish if set, also add the localized english version
     */
    private static void addParseDateFormat(@NonNull final String format,
                                           final boolean addEnglish) {
        PARSE_DATE_FORMATS.add(new SimpleDateFormat(format, LocaleUtils.getSystemLocale()));
        if (addEnglish) {
            PARSE_DATE_FORMATS.add(new SimpleDateFormat(format, Locale.ENGLISH));
        }
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    public static Date parseDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        // shortcut for plain 4 digit years.
        if (dateString.length() == 4) {
            try {
                return YEAR.parse(dateString);
            } catch (@NonNull final ParseException ignore) {
            }
        }

        // First try to parse using strict rules
        Date d = parseDate(dateString, false);
        if (d != null) {
            return d;
        }
        // OK, be lenient
        return parseDate(dateString, true);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats; allow
     * for caller to specify if the parsing should be strict or lenient.
     *
     * @param dateString String to parse
     * @param lenient    {@code true} if parsing should be lenient
     *
     * @return Resulting date if successfully parsed, otherwise {@code null}
     */
    @Nullable
    private static Date parseDate(@NonNull final String dateString,
                                  final boolean lenient) {
        // try all formats until one fits.
        for (SimpleDateFormat sdf : PARSE_DATE_FORMATS) {
            try {
                sdf.setLenient(lenient);
                return sdf.parse(dateString);
            } catch (@NonNull final ParseException ignore) {
            }
        }

        // try Default Locale.
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
            df.setLenient(lenient);
            return df.parse(dateString);
        } catch (@NonNull final ParseException ignore) {
        }

        // try System Locale.
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT,
                                                       LocaleUtils.getSystemLocale());
            df.setLenient(lenient);
            return df.parse(dateString);
        } catch (@NonNull final ParseException ignore) {
        }
        return null;
    }

    /**
     * Pretty format a (potentially partial) SQL date;  Locale based.
     *
     * @param locale     to use
     * @param dateString SQL formatted date.
     *
     * @return human readable date string
     *
     * @throws NumberFormatException on failure to parse
     */
    public static String toPrettyDate(@NonNull final Locale locale,
                                      @NonNull final String dateString)
            throws NumberFormatException {
        switch (dateString.length()) {
            case 10:
                // YYYY-MM-DD
                Date date = parseDate(dateString);
                if (date != null) {
                    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date);
                }
                // failed to parse
                if (BuildConfig.DEBUG) {
                    Logger.warnWithStackTrace(DateUtils.class, "failed: " + dateString);
                }
                return dateString;

            case 7:
                // input: YYYY-MM
                int month = Integer.parseInt(dateString.substring(5));
                // just swap: MMM YYYY
                return getMonthName(locale, month, true) + ' ' + dateString.substring(0, 4);

            case 4:
                // input: YYYY
                return dateString;

            default:
                // failed to parse
                if (BuildConfig.DEBUG) {
                    Logger.warnWithStackTrace(DateUtils.class, "failed: " + dateString);
                }
                return dateString;
        }
    }

    /**
     * Pretty format a datetime; Locale based.
     *
     * @param locale to use
     * @param date   to format
     */
    @NonNull
    public static String toPrettyDateTime(@NonNull final Locale locale,
                                          @NonNull final Date date) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM,
                                              locale).format(date);
    }

    /**
     * Get today's date.
     * Should be used for dates directly related to the user (date acquired, date read, etc...)
     *
     * @return SQL datetime-string, for the System Locale.
     */
    @NonNull
    public static String localSqlDateForToday() {
        Calendar calendar = Calendar.getInstance(LocaleUtils.getSystemLocale());
        return LOCAL_SQL_DATE.format(calendar.getTime());
    }

    /**
     * Convert a Date to a UTC based SQL datetime-string.
     *
     * @return SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTime(@NonNull final Date date) {
        return UTC_SQL_DATE_HH_MM_SS.format(date);
    }

    /**
     * @return today's SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTimeForToday() {
        return UTC_SQL_DATE_HH_MM_SS.format(
                Calendar.getInstance(LocaleUtils.getSystemLocale()).getTime());
    }

    /**
     * Convert a Date to a UTC based SQL date-string.
     *
     * @return SQL date-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDate(@NonNull final Date date) {
        return UTC_SQL_DATE.format(date);
    }

    /**
     * @param locale    to use
     * @param month     1-12 based month number
     * @param shortName {@code true} to get the abbreviated name instead of the full name.
     *
     * @return localised name of Month
     */
    @NonNull
    public static String getMonthName(@NonNull final Locale locale,
                                      @IntRange(from = 1, to = 12) final int month,
                                      final boolean shortName) {

        String iso3 = locale.getISO3Language();
        String[] longNames = MONTH_LONG_NAMES.get(iso3);
        String[] shortNames = MONTH_SHORT_NAMES.get(iso3);

        if (longNames == null) {
            // Build the cache for this locale.
            Calendar calendar = Calendar.getInstance(locale);
            SimpleDateFormat longNameFormatter = new SimpleDateFormat("MMMM", locale);
            SimpleDateFormat shortNameFormatter = new SimpleDateFormat("MMM", locale);

            longNames = new String[12];
            shortNames = new String[12];
            for (int m = 0; m < 12; m++) {
                calendar.set(Calendar.MONTH, m);
                longNames[m] = longNameFormatter.format(calendar.getTime());
                shortNames[m] = shortNameFormatter.format(calendar.getTime());
            }
            MONTH_LONG_NAMES.put(iso3, longNames);
            MONTH_SHORT_NAMES.put(iso3, shortNames);
        }

        if (shortName) {
            //noinspection ConstantConditions
            return shortNames[month - 1];
        } else {
            return longNames[month - 1];
        }
    }

    /**
     * Passed date components build a (partial) SQL format date string.
     * Locale independent.
     *
     * @return Formatted date, e.g. '2011-11-01' or '2011-11'
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    public static String buildPartialDate(@Nullable final Integer year,
                                          @Nullable final Integer month,
                                          @Nullable final Integer day) {
        if (year == null) {
            return "";
        } else {
            String value = String.format("%04d", year);
            if (month != null && month > 0) {
                String mm = month.toString();
                if (mm.length() == 1) {
                    mm = '0' + mm;
                }

                value += '-' + mm;

                if (day != null && day > 0) {
                    String dd = day.toString();
                    if (dd.length() == 1) {
                        dd = '0' + dd;
                    }
                    value += '-' + dd;
                }
            }
            return value;
        }
    }
}
