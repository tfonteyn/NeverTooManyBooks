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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * ENHANCE: Migrate to java.time.* ... which requires Android 8.0 (API 26)
 * or use this backport: https://github.com/JakeWharton/ThreeTenABP
 */
public final class DateUtils {

    /** Log tag. */
    private static final String TAG = "DateUtils";

    /** Month full names cache for each Locale. */
    private static final Map<String, String[]> MONTH_LONG_NAMES = new HashMap<>();
    /** Month abbreviated names cache for each Locale. */
    private static final Map<String, String[]> MONTH_SHORT_NAMES = new HashMap<>();
    /** List of formats we'll use to parse dates. */
    private static final Collection<SimpleDateFormat> PARSE_DATE_FORMATS = new ArrayList<>();
    /** List of formats we'll use to parse SQL dates. */
    private static final Collection<SimpleDateFormat> PARSE_SQL_DATE_FORMATS = new ArrayList<>();
    /** These come first. */
    private static final String[] PARSE_FORMAT_NUMERICAL = {
            "MM-dd-yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm",
            "MM-dd-yyyy",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy",
            };

    /** These come in the middle, the SQL/UTC specific ones. */
    private static final String[] PARSE_FORMAT_SQL = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy-MM",
            };

    /** These come after we add the UTC_SQL_* formats. */
    private static final String[] PARSE_FORMAT_TEXT = {
            "dd-MMM-yyyy HH:mm:ss",
            "dd-MMM-yyyy HH:mm",
            "dd-MMM-yyyy",

            "dd-MMM-yy HH:mm:ss",
            "dd-MMM-yy HH:mm",
            "dd-MMM-yy",

            // Amazon: 12 jan. 2017
            "dd MMM. yyyy",

            // OpenLibrary
            "dd MMM yyyy",
            // OpenLibrary
            "MMM d, yyyy",
            // OpenLibrary
            "MMM yyyy",

            // Not sure these are really needed.
            // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
            "EEE MMM dd HH:mm:ss ZZZZ yyyy",
            "EEE MMM dd HH:mm ZZZZ yyyy",
            "EEE MMM dd ZZZZ yyyy",
            };

    /** SQL Date formatter, System Locale, local time. */
    private static final SimpleDateFormat LOCAL_SQL_DATE =
            new SimpleDateFormat("yyyy-MM-dd", LocaleUtils.getSystemLocale());
    /** SQL Datetime formatter, UTC. */
    private static final SimpleDateFormat UTC_SQL_DATE_TIME_HH_MM_SS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
    /** SQL Date formatter, UTC. */
    private static final SimpleDateFormat UTC_SQL_DATE_YYYY_MM_DD =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    /** Simple match for a 4 digit year. */
    private static final SimpleDateFormat YEAR = new SimpleDateFormat("yyyy", Locale.ENGLISH);

    static {
        TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
        UTC_SQL_DATE_TIME_HH_MM_SS.setTimeZone(TZ_UTC);
        UTC_SQL_DATE_YYYY_MM_DD.setTimeZone(TZ_UTC);
    }

    private DateUtils() {
    }

    /**
     * Create the parser list. These will be tried IN THE ORDER DEFINED HERE.
     *
     * @param locales the locales to use
     */
    public static void create(@NonNull final Locale... locales) {
        // The numerical formats are top of the list.
        // SQL based formats
        // Text based formats
        String[][] allFormats = {PARSE_FORMAT_NUMERICAL, PARSE_FORMAT_SQL, PARSE_FORMAT_TEXT};
        create(PARSE_DATE_FORMATS, allFormats, locales);

        // SQL based formats
        String[][] sqlFormats = {PARSE_FORMAT_SQL};
        create(PARSE_SQL_DATE_FORMATS, sqlFormats, Locale.ENGLISH);
        TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
        for (SimpleDateFormat sdf : PARSE_SQL_DATE_FORMATS) {
            sdf.setTimeZone(TZ_UTC);
        }
    }

    /**
     * <strong>Create</strong> the parser list.
     * <p>
     * If English is not part of the passed list of Locales, it is automatically added.
     *
     * @param group   collection to add to
     * @param formats list of formats to add
     * @param locales to use
     */
    private static void create(@NonNull final Collection<SimpleDateFormat> group,
                               @NonNull final String[][] formats,
                               @NonNull final Locale... locales) {
        // allow re-creating.
        group.clear();
        addParseDateFormats(group, formats, locales);

        // add english if the user's Locale is not English.
        boolean hasEnglish = false;
        for (Locale locale : locales) {
            if (Locale.ENGLISH.equals(locale)) {
                hasEnglish = true;
                break;
            }
        }

        if (!hasEnglish) {
            addParseDateFormats(group, formats, Locale.ENGLISH);
        }
    }

    /**
     * <strong>Add</strong> to the parser list.
     *
     * @param group   collection to add to
     * @param formats list of formats to add
     * @param locales to use
     */
    private static void addParseDateFormats(@NonNull final Collection<SimpleDateFormat> group,
                                            @NonNull final String[][] formats,
                                            @NonNull final Locale... locales) {
        // track duplicates for each group separably
        Collection<Locale> added = new HashSet<>();
        for (String[] groupFormats : formats) {
            added.clear();
            for (Locale locale : locales) {
                if (!added.contains(locale)) {
                    added.add(locale);
                    for (String format : groupFormats) {
                        group.add(new SimpleDateFormat(format, locale));
                    }
                }
            }
        }
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date (with time==12:00:00) if parsed, otherwise {@code null}
     */
    @Nullable
    public static Date parseDate(@Nullable final String dateString) {
        Date date = parseDate(PARSE_DATE_FORMATS, dateString);
        // set time to noon, to avoid any overflow due to timezone or DST.
        if (date != null) {
            date.setHours(12);
            date.setMinutes(0);
            date.setSeconds(0);
        }
        return date;
    }

    /**
     * Attempt to parse a date string based on a range of possible formats using the passed locale.
     *
     * @param locale     to use
     * @param dateString String to parse
     *
     * @return Resulting date if successfully parsed, otherwise {@code null}
     */
    public static Date parseDate(@NonNull final Locale locale,
                                 @NonNull final String dateString) {
        // URGENT: parseDate should use passed locale FIRST
        return parseDate(dateString);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    public static Date parseDateTime(@Nullable final String dateString) {
        return parseDate(PARSE_DATE_FORMATS, dateString);
    }

    /**
     * Attempt to parse a datetime string based on the SQL formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    public static Date parseSqlDateTime(@Nullable final String dateString) {
        return parseDate(PARSE_SQL_DATE_FORMATS, dateString);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    private static Date parseDate(@NonNull final Iterable<SimpleDateFormat> formats,
                                  @Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        // shortcut for plain 4 digit years.
        if (dateString.length() == 4) {
            try {
                return YEAR.parse(dateString);
            } catch (@NonNull final ParseException ignore) {
                // ignore
            }
        }

        // First try to parse using strict rules
        Date d = parseDate(formats, dateString, false);
        if (d != null) {
            return d;
        }
        // try again being lenient
        return parseDate(formats, dateString, true);
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
    private static Date parseDate(@NonNull final Iterable<SimpleDateFormat> formats,
                                  @NonNull final String dateString,
                                  final boolean lenient) {
        for (DateFormat df : formats) {
            try {
                df.setLenient(lenient);
                return df.parse(dateString);
            } catch (@NonNull final ParseException ignore) {
                // ignore
            }
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

        if (dateString.length() < 6) {
            // 0: empty
            // 1,3,5: invalid, no need to parse
            // 2: we *could* parse and add either 1900 or 2000... but this is error prone
            // 4: shortcut for input: YYYY
            // Any of the above: just return the incoming string
            return dateString;

        } else if (dateString.length() == 7) {
            // input: YYYY-MM
            int month = Integer.parseInt(dateString.substring(5));
            // just swap: MMM YYYY
            return getMonthName(locale, month, true) + ' ' + dateString.substring(0, 4);

        } else {
            // Try to parse for length == 6 or > 7
            Date date = parseDate(dateString);
            if (date != null) {
                return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date);
            }
            // failed to parse
            if (BuildConfig.DEBUG /* always */) {
                Logger.e(TAG, "toPrettyDate=" + dateString, new Throwable());
            }
            return dateString;
        }

    }

    /**
     * Pretty format a datetime.
     *
     * @param date   to format
     * @param locale to use
     */
    @NonNull
    public static String toPrettyDateTime(@NonNull final Date date,
                                          @NonNull final Locale locale) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
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
        return UTC_SQL_DATE_TIME_HH_MM_SS.format(date);
    }

    /**
     * @return today's SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTimeForToday() {
        Calendar calendar = Calendar.getInstance(LocaleUtils.getSystemLocale());
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
     * Get the name of the month for the given month number.
     *
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

        if (longNames == null || shortNames == null) {
            // Build the cache for this Locale.
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
            return shortNames[month - 1];
        } else {
            return longNames[month - 1];
        }
    }

    /**
     * Passed date components build a (partial) SQL format date string.
     * Locale independent.
     *
     * @param month 1..12 based (or null for no month)
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
