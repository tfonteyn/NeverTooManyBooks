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
import java.util.Locale;
import java.util.TimeZone;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * All date handling here is for UTC/sql only, hence no Locale used.
 */
@SuppressLint("SimpleDateFormat")
public final class DateUtils {

    /**
     * Used for formatting *non-user* dates for SQL.
     */
    private static final TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
    /**
     * Date formatter for {@link #toPrettyDate}.
     * only used to display dates in the local timezone.
     */
    private static final DateFormat PRETTY_DATE_FORMATTER =
            DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
    /**
     * Date formatter for {@link #toPrettyDateTime(Date)}.
     * only used to display dates in the local timezone.
     */
    private static final DateFormat PRETTY_DATETIME_FORMATTER =
            DateFormat.getDateTimeInstance();

    /**
     * SQL Date formatter, Locale timezone. Used for *user* dates (read-end etc).
     */
    private static final SimpleDateFormat LOCAL_SQL_DATE =
            new SimpleDateFormat("yyyy-MM-dd");
    /**
     * SQL Date formatter, UTC. Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE =
            new SimpleDateFormat("yyyy-MM-dd");
    /**
     * SQL Datetime (with seconds) formatter, UTC. Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_HH_MM_SS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * SQL Datetime (no seconds) formatter, UTC. Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_HH_MM =
            new SimpleDateFormat("yyyy-MM-dd HH:mm");


    /** List of formats we'll use to parse dates. */
    private static final ArrayList<SimpleDateFormat> PARSE_DATE_FORMATS = new ArrayList<>();
    /** Calendar to construct dates from month numbers. */
    private static Calendar mCalendar;
    /** Formatter for month names given dates. */
    private static SimpleDateFormat mMonthNameFormatter;
    private static SimpleDateFormat mMonthShortNameFormatter;

    static {
        // set desired timezones
        PRETTY_DATE_FORMATTER.setTimeZone(Calendar.getInstance().getTimeZone());
        PRETTY_DATETIME_FORMATTER.setTimeZone(Calendar.getInstance().getTimeZone());

        LOCAL_SQL_DATE.setTimeZone(Calendar.getInstance().getTimeZone());

        UTC_SQL_DATE_HH_MM_SS.setTimeZone(TZ_UTC);
        UTC_SQL_DATE_HH_MM.setTimeZone(TZ_UTC);
        UTC_SQL_DATE.setTimeZone(TZ_UTC);

        // create the parser list. These will be tried IN THE ORDER DEFINED HERE.
        // the reasoning is (I think...) that only english speaking countries
        // even consider using Month first formatting.
        final boolean userSpeaksEnglish = Locale.getDefault()
                                                .getISO3Language()
                                                .equals(Locale.ENGLISH.getISO3Language());

        addParseDateFormat("dd-MMM-yyyy HH:mm:ss", !userSpeaksEnglish);
        addParseDateFormat("dd-MMM-yyyy HH:mm", !userSpeaksEnglish);
        addParseDateFormat("dd-MMM-yyyy", !userSpeaksEnglish);

        addParseDateFormat("dd-MMM-yy HH:mm:ss", !userSpeaksEnglish);
        addParseDateFormat("dd-MMM-yy HH:mm", !userSpeaksEnglish);
        addParseDateFormat("dd-MMM-yy", !userSpeaksEnglish);

        addParseDateFormat("MM-dd-yyyy HH:mm:ss", false);
        addParseDateFormat("MM-dd-yyyy HH:mm", false);
        addParseDateFormat("MM-dd-yyyy", false);

        addParseDateFormat("dd-MM-yyyy HH:mm:ss", false);
        addParseDateFormat("dd-MM-yyyy HH:mm", false);
        addParseDateFormat("dd-MM-yyyy", false);

        // "13 March 2009" added due to OpenLibrary
        addParseDateFormat("dd MMM yyyy", !userSpeaksEnglish);
        // "January 12, 1987" added due to OpenLibrary
        addParseDateFormat("MMM d, yyyy", !userSpeaksEnglish);

        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy", !userSpeaksEnglish);
        addParseDateFormat("EEE MMM dd HH:mm ZZZZ yyyy", !userSpeaksEnglish);
        addParseDateFormat("EEE MMM dd ZZZZ yyyy", !userSpeaksEnglish);

        // SQL date formats (UTC bases, not dependent on locale)
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_HH_MM_SS);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_HH_MM);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE);

        // TOMF,TEST: PARTIAL format... "March 2009" added due to OpenLibrary
        addParseDateFormat("MMM yyyy", !userSpeaksEnglish);
    }

    private DateUtils() {
    }

    /**
     * Add a format to the parser list.
     *
     * @param format      date format to add
     * @param needEnglish if set, also add the localized english version
     */
    private static void addParseDateFormat(@NonNull final String format,
                                           final boolean needEnglish) {
        PARSE_DATE_FORMATS.add(new SimpleDateFormat(format));
        if (needEnglish) {
            PARSE_DATE_FORMATS.add(new SimpleDateFormat(format, Locale.ENGLISH));
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Get today's date for the Locale timezone.
     * Should be used for dates related to the user
     */
    @NonNull
    public static String localSqlDateForToday() {
        return LOCAL_SQL_DATE.format(Calendar.getInstance().getTime());
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Pretty format a (potentially partial) SQL date; local timezone.
     *
     * @param dateString SQL formatted date.
     *
     * @return human readable date string
     *
     * @throws NumberFormatException on failure to parse
     */
    public static String toPrettyDate(@NonNull final String dateString)
            throws NumberFormatException {
        switch (dateString.length()) {
            case 10:
                // YYYY-MM-DD, full date parsing.
                Date date = parseDate(dateString);
                if (date != null) {
                    return PRETTY_DATE_FORMATTER.format(date);
                }
                // failed to parse
                if (BuildConfig.DEBUG) {
                    Logger.error("failed: " + dateString);
                }
                return dateString;

            case 7:
                // input: YYYY-MM,
                int month = Integer.parseInt(dateString.substring(5));
                // just swap: MMM YYYY
                return getMonthName(month, true) + ' ' + dateString.substring(0, 4);

            case 4:
                // YYYY
                return dateString;

            default:
                // failed to parse
                if (BuildConfig.DEBUG) {
                    Logger.error("failed: " + dateString);
                }
                return dateString;
        }
    }

    /**
     * Pretty format a datetime; local timezone.
     */
    @NonNull
    public static String toPrettyDateTime(@NonNull final Date date) {
        return PRETTY_DATETIME_FORMATTER.format(date);
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Convert a Date to a UTC based SQL date-string.
     *
     * @return SQL date-string
     */
    @NonNull
    public static String utcSqlDate(@NonNull final Date date) {
        return UTC_SQL_DATE.format(date);
    }

    /**
     * Convert a Date to a UTC based SQL datetime-string.
     *
     * @return SQL datetime-string
     */
    @NonNull
    public static String utcSqlDateTime(@NonNull final Date date) {
        return UTC_SQL_DATE_HH_MM_SS.format(date);
    }

    /**
     * @return today's date for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTimeForToday() {
        return UTC_SQL_DATE_HH_MM_SS.format(Calendar.getInstance().getTime());
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date if parsed, otherwise null
     */
    @Nullable
    public static Date parseDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
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
     * @param lenient    <tt>true</tt> if parsing should be lenient
     *
     * @return Resulting date if successfully parsed, otherwise null
     */
    @Nullable
    private static Date parseDate(@NonNull final String dateString,
                                  final boolean lenient) {
        // try all formats until one fits.
        for (SimpleDateFormat sdf : PARSE_DATE_FORMATS) {
            try {
                sdf.setLenient(lenient);
                return sdf.parse(dateString);
            } catch (ParseException ignore) {
            }
        }

        // All SDFs failed, try locale-specific...
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
            df.setLenient(lenient);
            return df.parse(dateString);
        } catch (ParseException ignore) {
        }
        return null;
    }

    @NonNull
    public static String getMonthName(@IntRange(from = 1, to = 12) final int month) {
        return getMonthName(month, false);
    }

    /**
     * @param month 1-12 based month number
     *
     * @return localised name of Month
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static String getMonthName(@IntRange(from = 1, to = 12) final int month,
                                      final boolean shortName) {
        if (mMonthNameFormatter == null) {
            mMonthNameFormatter = new SimpleDateFormat("MMMM");
            mMonthShortNameFormatter = new SimpleDateFormat("MMM");
        }
        // Create static calendar if necessary
        if (mCalendar == null) {
            mCalendar = Calendar.getInstance();
        }
        // Assumes months are integers and in sequence...which everyone seems to assume
        mCalendar.set(Calendar.MONTH, month - 1 + Calendar.JANUARY);
        if (shortName) {
            return mMonthShortNameFormatter.format(mCalendar.getTime());
        } else {
            return mMonthNameFormatter.format(mCalendar.getTime());
        }
    }

    /**
     * Passed date components build a (partial) SQL format date string.
     *
     * @return Formatted date, eg. '2011-11-01' or '2011-11'
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
