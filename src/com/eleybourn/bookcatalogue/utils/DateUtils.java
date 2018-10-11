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
    private DateUtils() {
    }

    /** Used for formatting dates for sql; everything is assumed to be UTC, or converted to UTC since
     *  UTC is the default SQLite TZ.
     */
    private static final TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");

    // Used for date parsing and display
    private static final SimpleDateFormat DATE_FULL_HMSS_SQL = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static { DATE_FULL_HMSS_SQL.setTimeZone(TZ_UTC); }

    private static final SimpleDateFormat DATE_FULL_HMS_SQL = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    static { DATE_FULL_HMS_SQL.setTimeZone(TZ_UTC); }

    private static final SimpleDateFormat DATE_SQL = new SimpleDateFormat("yyyy-MM-dd");
    static { DATE_SQL.setTimeZone(TZ_UTC); }

    private static final SimpleDateFormat LOCAL_DATE_SQL = new SimpleDateFormat("yyyy-MM-dd");
    static { LOCAL_DATE_SQL.setTimeZone(Calendar.getInstance().getTimeZone()); }

    private static final DateFormat DATE_DISPLAY = DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);

    private static final ArrayList<SimpleDateFormat> mParseDateFormats = new ArrayList<>();
    static {
        // the reasoning is that only english speaking countries even consider using Month first formatting.
        final boolean isEnglish = (Locale.getDefault().getLanguage().equals(Locale.ENGLISH.getLanguage()));

        addParseDateFormat("dd-MMM-yyyy HH:mm:ss", !isEnglish);
        addParseDateFormat("dd-MMM-yyyy HH:mm", !isEnglish);
        addParseDateFormat("dd-MMM-yyyy", !isEnglish);

        addParseDateFormat("dd-MMM-yy HH:mm:ss", !isEnglish);
        addParseDateFormat("dd-MMM-yy HH:mm", !isEnglish);
        addParseDateFormat("dd-MMM-yy", !isEnglish);

        addParseDateFormat("MM-dd-yyyy HH:mm:ss", false);
        addParseDateFormat("MM-dd-yyyy HH:mm", false);
        addParseDateFormat("MM-dd-yyyy", false);

        addParseDateFormat("dd-MM-yyyy HH:mm:ss", false);
        addParseDateFormat("dd-MM-yyyy HH:mm", false);
        addParseDateFormat("dd-MM-yyyy", false);

        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy", !isEnglish);
        addParseDateFormat("EEE MMM dd HH:mm ZZZZ yyyy", !isEnglish);
        addParseDateFormat("EEE MMM dd ZZZZ yyyy", !isEnglish);

        mParseDateFormats.add(DATE_FULL_HMSS_SQL);
        mParseDateFormats.add(DATE_FULL_HMS_SQL);
        mParseDateFormats.add(DATE_SQL);
    }

    /**
     * Add a format to the parser list
     *
     * @param format        date format to add
     * @param needEnglish   if set, also add the localized english version
     */
    private static void addParseDateFormat(@NonNull final String format, final boolean needEnglish) {
        mParseDateFormats.add(new SimpleDateFormat(format));
        if (needEnglish) {
            mParseDateFormats.add(new SimpleDateFormat(format, Locale.ENGLISH));
        }
    }
    @NonNull
    public static String toLocalSqlDateOnly(@NonNull final Date d) {
        return LOCAL_DATE_SQL.format(d);
    }
    @NonNull
    public static String toSqlDateOnly(@NonNull final Date d) {
        return DATE_SQL.format(d);
    }
    @NonNull
    public static String todaySqlDateOnly() {
        return DATE_SQL.format(new Date());
    }
    @NonNull
    public static String toSqlDateTime(@NonNull final Date d) {
        return DATE_FULL_HMSS_SQL.format(d);
    }
    @NonNull
    public static String toPrettyDate(@NonNull final Date d) {
        return DATE_DISPLAY.format(d);
    }
    @NonNull
    public static String toPrettyDateTime(@NonNull final Date d) {
        return DateFormat.getDateTimeInstance().format(d);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString	String to parse
     * @return	Resulting date if parsed, otherwise null
     */
    @Nullable
    public static Date parseDate(@Nullable final String dateString) {
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
     * If any Exception, returns null
     *
     * @param dateString	String to parse
     * @param lenient		True if parsing should be lenient
     *
     * @return				Resulting date if parsed, otherwise null
     */
    @Nullable
    private static Date parseDate(@Nullable final String dateString, final boolean lenient) {
        if (dateString == null) {
            return null;
        }
        // try all formats until one fits.
        for (SimpleDateFormat sdf : mParseDateFormats ) {
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

    /** Calendar to construct dates from month numbers */
    @Nullable
    private static Calendar mCalendar = null;
    /** Formatter for month names given dates */
    @Nullable
    private static SimpleDateFormat mMonthNameFormatter = null;

    @NonNull
    public static String getMonthName(final int month) {
        if (mMonthNameFormatter == null) {
            mMonthNameFormatter = new SimpleDateFormat("MMMM");
        }
        // Create static calendar if necessary
        if (mCalendar == null) {
            mCalendar = Calendar.getInstance();
        }
        // Assumes months are integers and in sequence...which everyone seems to assume
        mCalendar.set(Calendar.MONTH, month - 1 + Calendar.JANUARY);
        return mMonthNameFormatter.format(mCalendar.getTime());
    }

    /**
     * Passed date components build a (partial) SQL format date string.
     *
     * @return		Formatted date, eg. '2011-11-01' or '2011-11'
     */
    @SuppressLint("DefaultLocale")
    @NonNull
    public static String buildPartialDate(@Nullable final Integer year, @Nullable final Integer month, @Nullable final Integer day) {
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

//    public String convertDate(@NonNull final String date) {
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
