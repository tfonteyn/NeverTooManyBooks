package com.eleybourn.bookcatalogue.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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
    private static void addParseDateFormat(boolean needEnglish, String format) {
        mParseDateFormats.add(new SimpleDateFormat(format));
        if (needEnglish)
            mParseDateFormats.add(new SimpleDateFormat(format, Locale.ENGLISH));
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
     * @param s				String to parse
     * @param lenient		True if parsing should be lenient
     *
     * @return				Resulting date if parsed, otherwise null
     */
    private static Date parseDate(String s, boolean lenient) {
        Date d;
        for ( SimpleDateFormat sdf : mParseDateFormats ) {
            try {
                sdf.setLenient(lenient);
                d = sdf.parse(s);
                return d;
            } catch (Exception e) {
                // Ignore
            }
        }
        // All SDFs failed, try locale-specific...
        try {
            java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
            df.setLenient(lenient);
            d = df.parse(s);
            return d;
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /** Calendar to construct dates from month numbers */
    private static Calendar mCalendar = null;
    /** Formatter for month names given dates */
    private static SimpleDateFormat mMonthNameFormatter = null;

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
}
