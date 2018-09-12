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

    // Used for formatting dates for sql; everything is assumed to be UTC, or converted to UTC since
    // UTC is the default SQLite TZ.
    private static final TimeZone tzUtc = TimeZone.getTimeZone("UTC");

    // Used for date parsing and display
    private static final SimpleDateFormat mDateFullHMSSqlSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static { mDateFullHMSSqlSdf.setTimeZone(tzUtc); }
    private static final SimpleDateFormat mDateFullHMSqlSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    static { mDateFullHMSqlSdf.setTimeZone(tzUtc); }
    private static final SimpleDateFormat mDateSqlSdf = new SimpleDateFormat("yyyy-MM-dd");
    static { mDateSqlSdf.setTimeZone(tzUtc); }
    private static final DateFormat mDateDispSdf = DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
    private static final SimpleDateFormat mLocalDateSqlSdf = new SimpleDateFormat("yyyy-MM-dd");
    static { mLocalDateSqlSdf.setTimeZone(Calendar.getInstance().getTimeZone()); }

    private static final ArrayList<SimpleDateFormat> mParseDateFormats = new ArrayList<>();
    static {
        final boolean isEnglish = (Locale.getDefault().getLanguage().equals(Locale.ENGLISH.getLanguage()));
        addParseDateFormat(!isEnglish, "dd-MMM-yyyy HH:mm:ss");
        addParseDateFormat(!isEnglish, "dd-MMM-yyyy HH:mm");
        addParseDateFormat(!isEnglish, "dd-MMM-yyyy");

        addParseDateFormat(!isEnglish, "dd-MMM-yy HH:mm:ss");
        addParseDateFormat(!isEnglish, "dd-MMM-yy HH:mm");
        addParseDateFormat(!isEnglish, "dd-MMM-yy");

        addParseDateFormat(false, "MM-dd-yyyy HH:mm:ss");
        addParseDateFormat(false, "MM-dd-yyyy HH:mm");
        addParseDateFormat(false, "MM-dd-yyyy");

        addParseDateFormat(false, "dd-MM-yyyy HH:mm:ss");
        addParseDateFormat(false, "dd-MM-yyyy HH:mm");
        addParseDateFormat(false, "dd-MM-yyyy");

        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat(!isEnglish, "EEE MMM dd HH:mm:ss ZZZZ yyyy");
        addParseDateFormat(!isEnglish, "EEE MMM dd HH:mm ZZZZ yyyy");
        addParseDateFormat(!isEnglish, "EEE MMM dd ZZZZ yyyy");

        mParseDateFormats.add(mDateFullHMSSqlSdf);
        mParseDateFormats.add(mDateFullHMSqlSdf);
        mParseDateFormats.add(mDateSqlSdf);
    }
    /**
     * Add a format to the parser list
     *
     * @param needEnglish   if set, also add the localized english version
     * @param format        date format to add
     */
    private static void addParseDateFormat(boolean needEnglish, @NonNull final String format) {
        mParseDateFormats.add(new SimpleDateFormat(format));
        if (needEnglish) {
            mParseDateFormats.add(new SimpleDateFormat(format, Locale.ENGLISH));
        }
    }

    public static String toLocalSqlDateOnly(Date d) {
        return mLocalDateSqlSdf.format(d);
    }
    public static String toSqlDateOnly(Date d) {
        return mDateSqlSdf.format(d);
    }
    public static String toSqlDateTime(Date d) {
        return mDateFullHMSSqlSdf.format(d);
    }
    public static String toPrettyDate(Date d) {
        return mDateDispSdf.format(d);
    }
    public static String toPrettyDateTime(Date d) {
        return DateFormat.getDateTimeInstance().format(d);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param s		String to parse
     * @return		Resulting date if parsed, otherwise null
     */
    @Nullable
    public static Date parseDate(String s) {
        Date d;
        // First try to parse using strict rules
        d = parseDate(s, false);
        // If we got a date, exit
        if (d != null)
            return d;
        // OK, be lenient
        return parseDate(s, true);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats; allow
     * for caller to specify if the parsing should be strict or lenient.
     *
     * If any Exception, returns null
     *
     * @param s				String to parse
     * @param lenient		True if parsing should be lenient
     *
     * @return				Resulting date if parsed, otherwise null
     */
    @Nullable
    private static Date parseDate(@NonNull final String s, boolean lenient) {
        Date d;
        for (SimpleDateFormat sdf : mParseDateFormats ) {
            try {
                sdf.setLenient(lenient);
                d = sdf.parse(s);
                return d;
            } catch (ParseException ignore) {
            }
        }
        // All SDFs failed, try locale-specific...
        try {
            java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
            df.setLenient(lenient);
            d = df.parse(s);
            return d;
        } catch (ParseException ignore) {
        }
        return null;
    }

    /** Calendar to construct dates from month numbers */
    private static Calendar mCalendar = null;
    /** Formatter for month names given dates */
    private static SimpleDateFormat mMonthNameFormatter = null;

    @NonNull
    public static String getMonthName(int month) {
        if (mMonthNameFormatter == null)
            mMonthNameFormatter = new SimpleDateFormat("MMMM");
        // Create static calendar if necessary
        if (mCalendar == null)
            mCalendar = Calendar.getInstance();
        // Assumes months are integers and in sequence...which everyone seems to assume
        mCalendar.set(Calendar.MONTH, month - 1 + java.util.Calendar.JANUARY);
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

//    public String convertDate(String date) {
//        switch (date.length()) {
//            case 2:
//                //assume yy
//                try {
//                    date = Integer.parseInt(date) < 15 ? "20" + date + "-01-01" : "19" + date + "-01-01";
//                } catch (NumberFormatException e) {
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
