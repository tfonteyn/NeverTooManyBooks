/*
 * @Copyright 2019 HardBackNutter
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
import androidx.annotation.VisibleForTesting;

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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * FIXME: Migrate to java.time.* ... which required Android 8.0 (API 26)
 * or use this backport: https://github.com/JakeWharton/ThreeTenABP
 */
public final class DateUtils {

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
     * SQL Datetime formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_TIME_HH_MM_SS;
    /**
     * SQL Datetime formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_TIME_HH_MM;
    /**
     * SQL Date formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_YYYY_MM_DD;
    /**
     * SQL Date formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_YYYY_MM;

    /** Simple match for a 4 digit year. */
    private static final SimpleDateFormat YEAR =
            new SimpleDateFormat("yyyy", App.getSystemLocale());

    /**
     * List of formats we'll use to parse dates.
     * 2019-08-03: there are 22 formats, setting capacity to 25.
     */
    private static final ArrayList<SimpleDateFormat> PARSE_DATE_FORMATS = new ArrayList<>(25);

    static {
        // This set of formats are locale agnostic;
        // but we must make sure these use the real system Locale.
        Locale systemLocale = App.getSystemLocale();

        // Used for formatting *user* dates, in the locale timezone, for SQL. e.g. date read...
        LOCAL_SQL_DATE = new SimpleDateFormat("yyyy-MM-dd", systemLocale);

        // Used for formatting *non-user* dates for SQL. e.g. publication dates...
        TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
        UTC_SQL_DATE_TIME_HH_MM_SS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", systemLocale);
        UTC_SQL_DATE_TIME_HH_MM_SS.setTimeZone(TZ_UTC);

        UTC_SQL_DATE_TIME_HH_MM = new SimpleDateFormat("yyyy-MM-dd HH:mm", systemLocale);
        UTC_SQL_DATE_TIME_HH_MM.setTimeZone(TZ_UTC);

        UTC_SQL_DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd", systemLocale);
        UTC_SQL_DATE_YYYY_MM_DD.setTimeZone(TZ_UTC);

        UTC_SQL_DATE_YYYY_MM = new SimpleDateFormat("yyyy-MM", systemLocale);
        UTC_SQL_DATE_YYYY_MM.setTimeZone(TZ_UTC);
    }

    private DateUtils() {
    }

    /**
     * create the parser list. These will be tried IN THE ORDER DEFINED HERE.
     *
     * @param alsoAddEnglish Flag. (We use a parameter to allow testing)
     */
    @VisibleForTesting
    static void createParseDateFormats(@NonNull final Locale locale,
                                       final boolean alsoAddEnglish) {
        // allow re-creating.
        PARSE_DATE_FORMATS.clear();

        // numerical formats
        addParseDateFormat("MM-dd-yyyy HH:mm:ss", locale, false);
        addParseDateFormat("MM-dd-yyyy HH:mm", locale, false);
        addParseDateFormat("MM-dd-yyyy", locale, false);

        addParseDateFormat("dd-MM-yyyy HH:mm:ss", locale, false);
        addParseDateFormat("dd-MM-yyyy HH:mm", locale, false);
        addParseDateFormat("dd-MM-yyyy", locale, false);

        // SQL date formats, locale agnostic.
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_TIME_HH_MM_SS);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_TIME_HH_MM);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_YYYY_MM_DD);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_YYYY_MM);

        // add english if the user's System Locale is not English.
        // This is done because most (all?) internet sites we search are english.
        addParseDateFormat("dd-MMM-yyyy HH:mm:ss", locale, !alsoAddEnglish);
        addParseDateFormat("dd-MMM-yyyy HH:mm", locale, !alsoAddEnglish);
        addParseDateFormat("dd-MMM-yyyy", locale, !alsoAddEnglish);

        addParseDateFormat("dd-MMM-yy HH:mm:ss", locale, !alsoAddEnglish);
        addParseDateFormat("dd-MMM-yy HH:mm", locale, !alsoAddEnglish);
        addParseDateFormat("dd-MMM-yy", locale, !alsoAddEnglish);

        // added due to OpenLibrary
        addParseDateFormat("dd MMM yyyy", locale, !alsoAddEnglish);
        // added due to OpenLibrary
        addParseDateFormat("MMM d, yyyy", locale, !alsoAddEnglish);
        // added due to OpenLibrary
        addParseDateFormat("MMM yyyy", locale, !alsoAddEnglish);

        // Not sure these are really needed.
        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy", locale, !alsoAddEnglish);
        addParseDateFormat("EEE MMM dd HH:mm ZZZZ yyyy", locale, !alsoAddEnglish);
        addParseDateFormat("EEE MMM dd ZZZZ yyyy", locale, !alsoAddEnglish);
    }

    /**
     * Add a format to the parser list using the passed Locale.
     * Optionally add English Locale as well.
     *
     * @param format     date format to add
     * @param locale     locale to use
     * @param addEnglish if set, also add Locale.ENGLISH
     */
    private static void addParseDateFormat(@NonNull final String format,
                                           @NonNull final Locale locale,
                                           final boolean addEnglish) {
        PARSE_DATE_FORMATS.add(new SimpleDateFormat(format, locale));
        if (addEnglish && !Locale.ENGLISH.equals(locale)) {
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
     * <p>
     * <strong>Note:</strong> the timestamp part is always set to 00:00:00
     *
     * @param dateString String to parse
     * @param lenient    {@code true} if parsing should be lenient
     *
     * @return Resulting date if successfully parsed, otherwise {@code null}
     */
    @Nullable
    private static Date parseDate(@NonNull final String dateString,
                                  final boolean lenient) {
        // create on first use.
        if (PARSE_DATE_FORMATS.isEmpty()) {
            // check the device language
            boolean userIsEnglishSpeaking =
                    Objects.equals("eng", App.getSystemLocale().getISO3Language());

            createParseDateFormats(App.getSystemLocale(), userIsEnglishSpeaking);
        }

        // try all formats until one fits.
        for (DateFormat df : PARSE_DATE_FORMATS) {
            try {
                return parseDate(df, dateString, lenient);
            } catch (@NonNull final ParseException ignore) {
            }
        }

        // try Default Locale.
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
            return parseDate(df, dateString, lenient);
        } catch (@NonNull final ParseException ignore) {
        }

        // try System Locale.
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, App.getSystemLocale());
            return parseDate(df, dateString, lenient);
        } catch (@NonNull final ParseException ignore) {
        }

        // give up.
        return null;
    }

    @Nullable
    private static Date parseDate(@NonNull final DateFormat df,
                                  @NonNull final String dateString,
                                  final boolean lenient)
            throws ParseException {
        df.setLenient(lenient);
        Date date = df.parse(dateString);
        // set time to noon, to avoid any overflow due to timezone or DST.
        if (date != null) {
            date.setHours(12);
            date.setMinutes(0);
            date.setSeconds(0);
        }
        return date;
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
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.DATETIME) {
                    Logger.debugWithStackTrace(DateUtils.class, "dateString=" + dateString);
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
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.DATETIME) {
                    Logger.debugWithStackTrace(DateUtils.class, "dateString=" + dateString);
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
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
                         .format(date);
    }

    /**
     * Get today's date.
     * Should be used for dates directly related to the user (date acquired, date read, etc...)
     *
     * @return SQL datetime-string, for the System Locale.
     */
    @NonNull
    public static String localSqlDateForToday() {
        Calendar calendar = Calendar.getInstance(App.getSystemLocale());
        return LOCAL_SQL_DATE.format(calendar.getTime());
    }

    /**
     * Convert a Date to a UTC based SQL datetime-string.
     *
     * @return SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTime(@NonNull final Date date) {
        return UTC_SQL_DATE_TIME_HH_MM_SS.format(date);
    }

    /**
     * @return today's SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTimeForToday() {
        Calendar calendar = Calendar.getInstance(App.getSystemLocale());
        return UTC_SQL_DATE_TIME_HH_MM_SS.format(calendar.getTime());
    }

    /**
     * Convert a Date to a UTC based SQL date-string.
     *
     * @return SQL date-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDate(@NonNull final Date date) {
        // set time to noon, to avoid any overflow due to timezone or DST.
        date.setHours(12);
        date.setMinutes(0);
        date.setSeconds(0);
        return UTC_SQL_DATE_YYYY_MM_DD.format(date);
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

        String iso = locale.getISO3Language();
        String[] longNames = MONTH_LONG_NAMES.get(iso);
        String[] shortNames = MONTH_SHORT_NAMES.get(iso);

        if (longNames == null) {
            // Build the cache for this locale.
            Calendar calendar = Calendar.getInstance(locale);
            SimpleDateFormat longNameFormatter = new SimpleDateFormat("MMMM", locale);
            SimpleDateFormat shortNameFormatter = new SimpleDateFormat("MMM", locale);

            longNames = new String[12];
            shortNames = new String[12];
            for (int m = 0; m < 12; m++) {
                // prevent wrapping
                calendar.set(Calendar.DATE, 1);
                calendar.set(Calendar.MONTH, m);
                longNames[m] = longNameFormatter.format(calendar.getTime());
                shortNames[m] = shortNameFormatter.format(calendar.getTime());
            }
            MONTH_LONG_NAMES.put(iso, longNames);
            MONTH_SHORT_NAMES.put(iso, shortNames);
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
    @NonNull
    public static String buildPartialDate(@Nullable final Integer year,
                                          @Nullable final Integer month,
                                          @Nullable final Integer day) {
        if (year == null || year == 0) {
            return "";
        } else {
            String value = String.format(Locale.ENGLISH, "%04d", year);
            if (month != null && month > 0) {
                //noinspection CallToNumericToString
                String mm = month.toString();
                if (mm.length() == 1) {
                    mm = '0' + mm;
                }

                value += '-' + mm;

                if (day != null && day > 0) {
                    //noinspection CallToNumericToString
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
