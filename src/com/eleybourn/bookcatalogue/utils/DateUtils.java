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
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * All date handling here is for UTC/sql only, hence no Locale used
 */
@SuppressLint("SimpleDateFormat")
public class DateUtils {
    /**
     * Used for formatting *non-user* dates for SQL
     */
    private static final TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
    /**
     * Date formatter for {@link #toPrettyDate(Date)}
     * only used to display dates in the local timezone.
     */
    private static final DateFormat PRETTY_DATE_FORMATTER = DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
    /**
     * Date formatter for {@link #toPrettyDateTime(Date)}
     * only used to display dates in the local timezone.
     */
    private static final DateFormat PRETTY_DATETIME_FORMATTER = DateFormat.getDateTimeInstance();
    /**
     * SQL Date formatter, Locale timezone. Used for *user* dates (read-end etc)
     */
    private static final SimpleDateFormat LOCAL_SQL_DATE = new SimpleDateFormat("yyyy-MM-dd");
    /**
     * SQL Date formatter, UTC. Used for *non-user* dates (date published etc)
     */
    private static final SimpleDateFormat UTC_SQL_DATE = new SimpleDateFormat("yyyy-MM-dd");
    /**
     * SQL Datetime (with seconds) formatter, UTC. Used for *non-user* dates (date published etc)
     */
    private static final SimpleDateFormat UTC_SQL_DATE_HH_MM_SS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * SQL Datetime (no seconds) formatter, UTC. Used for *non-user* dates (date published etc)
     */
    private static final SimpleDateFormat UTC_SQL_DATE_HH_MM = new SimpleDateFormat("yyyy-MM-dd HH:mm");


    /** List of formats we'll use to parse dates. */
    private static final ArrayList<SimpleDateFormat> mParseDateFormats = new ArrayList<>();
    /** Calendar to construct dates from month numbers */
    private static Calendar mCalendar = null;
    /** Formatter for month names given dates */
    private static SimpleDateFormat mMonthNameFormatter = null;
    private static SimpleDateFormat mMonthShortNameFormatter = null;

    static {
        // set desired timezones
        PRETTY_DATE_FORMATTER.setTimeZone(Calendar.getInstance().getTimeZone());
        PRETTY_DATETIME_FORMATTER.setTimeZone(Calendar.getInstance().getTimeZone());

        LOCAL_SQL_DATE.setTimeZone(Calendar.getInstance().getTimeZone());

        UTC_SQL_DATE_HH_MM_SS.setTimeZone(TZ_UTC);
        UTC_SQL_DATE_HH_MM.setTimeZone(TZ_UTC);
        UTC_SQL_DATE.setTimeZone(TZ_UTC);

        // create the parser list. These will be tried IN THE ORDER DEFINED HERE.
        // the reasoning is (I think...) that only english speaking countries even consider using Month first formatting.
        final boolean userSpeaksEnglish = (Locale.getDefault().getISO3Language().equals(Locale.ENGLISH.getISO3Language()));

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

        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy", !userSpeaksEnglish);
        addParseDateFormat("EEE MMM dd HH:mm ZZZZ yyyy", !userSpeaksEnglish);
        addParseDateFormat("EEE MMM dd ZZZZ yyyy", !userSpeaksEnglish);

        mParseDateFormats.add(UTC_SQL_DATE_HH_MM_SS);
        mParseDateFormats.add(UTC_SQL_DATE_HH_MM);
        mParseDateFormats.add(UTC_SQL_DATE);
    }

    /**
     * Add a format to the parser list
     *
     * @param format      date format to add
     * @param needEnglish if set, also add the localized english version
     */
    private static void addParseDateFormat(final @NonNull String format, final boolean needEnglish) {
        mParseDateFormats.add(new SimpleDateFormat(format));
        if (needEnglish) {
            mParseDateFormats.add(new SimpleDateFormat(format, Locale.ENGLISH));
        }
    }

    private DateUtils() {
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
     * Pretty format a (potentially) partial SQL date; local timezone
     */
    public static String toPrettyDate(final @NonNull String partialDate) {
        switch (partialDate.length()) {
            // YYYY-MM-DD
            case 10: {
                Date d = parseDate(partialDate);
                if (d != null) {
                    return toPrettyDate(d);
                }
                break;
            }
            // YYYY-MM
            case 7: {
                int month = Integer.parseInt(partialDate.substring(5));
                // MMM YYYY
                return getMonthName(month, true) + " " + partialDate.substring(0,4);
            }
        }
        // YYYY (or whatever came in)
        return partialDate;
    }

    /**
     * Pretty format a date; local timezone
     */
    @NonNull
    public static String toPrettyDate(final @NonNull Date d) {
        return PRETTY_DATE_FORMATTER.format(d);
    }

    /**
     * Pretty format a datetime; local timezone
     */
    @NonNull
    public static String toPrettyDateTime(final @NonNull Date d) {
        return PRETTY_DATETIME_FORMATTER.format(d);
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * Convert a Date to a UTC based SQL date-string
     */
    @NonNull
    public static String utcSqlDate(final @NonNull Date d) {
        return UTC_SQL_DATE.format(d);
    }

    /**
     * Convert a Date to a UTC based SQL datetime-string
     */
    @NonNull
    public static String utcSqlDateTime(final @NonNull Date d) {
        return UTC_SQL_DATE_HH_MM_SS.format(d);
    }

    /**
     * Get today's date for the UTC timezone.
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
    public static Date parseDate(final @Nullable String dateString) {
        if (dateString == null) {
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
     * @param lenient    True if parsing should be lenient
     *
     * @return Resulting date if successfully parsed, otherwise null
     */
    @Nullable
    private static Date parseDate(final @Nullable String dateString, final boolean lenient) {
        if (dateString == null) {
            return null;
        }
        // try all formats until one fits.
        for (SimpleDateFormat sdf : mParseDateFormats) {
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
    public static String getMonthName(final @IntRange(from = 1, to = 12) int month) {
        return getMonthName(month,false);
    }
    /**
     *
     * @param month 1-12 based month number
     *
     * @return localised name of Month
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static String getMonthName(final @IntRange(from = 1, to = 12) int month, final boolean shortName) {
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
    public static String buildPartialDate(final @Nullable Integer year,
                                          final @Nullable Integer month,
                                          final @Nullable Integer day) {
        if (year == null) {
            return "";
        } else {
            String value = String.format("%04d", year);
            if (month != null && month > 0) {
                String mm = month.toString();
                if (mm.length() == 1) {
                    mm = "0" + mm;
                }

                value += "-" + mm;

                if (day != null && day > 0) {
                    String dd = day.toString();
                    if (dd.length() == 1) {
                        dd = "0" + dd;
                    }
                    value += "-" + dd;
                }
            }
            return value;
        }
    }

//    public String convertDate(@NonNull String date) {
//        switch (date.length()) {
//            case 2:
//                //assume yy
//                try {
//                    date = Integer.parseInt(date) < 15 ? "20" + date + "-01-01" : "19" + date + "-01-01";
//                } catch (NumberFormatException error) {
//                    date = "";
//                }
//                break;
//            case 4:
//                //assume yyyy
//                date = date + "-01-01";
//                break;
//            case 6:
//                //assume yyyymm
//                date = date.substring(0, 4) + "-" + date.substring(4, 6) + "-01";
//                break;
//            case 7:
//                //assume yyyy-mm
//                date = date + "-01";
//                break;
//        }
//        return date;
//    }
}
