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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateUtils {

//    /** List of formats we'll use to parse dates. */
//    private static final Collection<SimpleDateFormat> PARSE_DATE_FORMATS = new ArrayList<>();
//    /** List of formats we'll use to parse SQL dates. */
//    private static final Collection<SimpleDateFormat> PARSE_SQL_DATE_FORMATS = new ArrayList<>();
//
//    /** These come first. */
//    private static final String[] PARSE_FORMAT_NUMERICAL = {
//            "MM-dd-yyyy HH:mm:ss",
//            "MM-dd-yyyy HH:mm",
//            "MM-dd-yyyy",
//            "dd-MM-yyyy HH:mm:ss",
//            "dd-MM-yyyy HH:mm",
//            "dd-MM-yyyy",
//            };
//
//    /** These come in the middle, the SQL/UTC specific ones. */
//    private static final String[] PARSE_FORMAT_SQL = {
//            "yyyy-MM-dd HH:mm:ss",
//            "yyyy-MM-dd HH:mm",
//            "yyyy-MM-dd",
//            "yyyy-MM",
//            };
//
//    /** These come after we add the UTC_SQL_* formats. */
//    private static final String[] PARSE_FORMAT_TEXT = {
//            "dd-MMM-yyyy HH:mm:ss",
//            "dd-MMM-yyyy HH:mm",
//            "dd-MMM-yyyy",
//
//            "dd-MMM-yy HH:mm:ss",
//            "dd-MMM-yy HH:mm",
//            "dd-MMM-yy",
//
//            // Amazon: 12 jan. 2017
//            "dd MMM. yyyy",
//
//            // OpenLibrary
//            "dd MMM yyyy",
//            // OpenLibrary
//            "MMM d, yyyy",
//            // OpenLibrary
//            "MMM yyyy",
//
//            // Not sure these are really needed.
//            // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
//            "EEE MMM dd HH:mm:ss ZZZZ yyyy",
//            "EEE MMM dd HH:mm ZZZZ yyyy",
//            "EEE MMM dd ZZZZ yyyy",
//            };

    /** Simple match for a 4 digit year. */
//    private static final SimpleDateFormat YEAR = new SimpleDateFormat("yyyy", Locale.ENGLISH);
    private DateUtils() {
    }

    @VisibleForTesting
    public static void clear() {
//        PARSE_DATE_FORMATS.clear();
//        PARSE_SQL_DATE_FORMATS.clear();
    }

    /**
     * Create the parser lists. These will be tried IN THE ORDER DEFINED HERE.
     * <ol>
     *     <li>numerical formats</li>
     *     <li>SQL based</li>
     *     <li>Text based</li>
     * </ol>
     *
     * @param locales the locales to use
     */
    public static void create(@NonNull final Locale... locales) {

//        final String[][] allFormats =
//              {PARSE_FORMAT_NUMERICAL, PARSE_FORMAT_SQL, PARSE_FORMAT_TEXT};
//        create(PARSE_DATE_FORMATS, allFormats, locales);
//
//        // SQL based formats with UTC/English
//        final String[][] sqlFormats = {PARSE_FORMAT_SQL};
//        create(PARSE_SQL_DATE_FORMATS, sqlFormats, Locale.ENGLISH);
//        final TimeZone utc = TimeZone.getTimeZone("UTC");
//        for (SimpleDateFormat sdf : PARSE_SQL_DATE_FORMATS) {
//            sdf.setTimeZone(utc);
//        }
    }

//    /**
//     * <strong>Create</strong> the given parser list.
//     * <p>
//     * If English is not part of the passed list of Locales, it is automatically added.
//     *
//     * @param group   collection to add to
//     * @param formats list of formats to add
//     * @param locales to use
//     */
//    private static void create(@NonNull final Collection<SimpleDateFormat> group,
//                               @NonNull final String[][] formats,
//                               @NonNull final Locale... locales) {
//        // allow re-creating.
//        group.clear();
//        addParseDateFormats(group, formats, locales);
//
//        // add english if the user's Locale is not English.
//        boolean hasEnglish = false;
//        for (Locale locale : locales) {
//            if (Locale.ENGLISH.equals(locale)) {
//                hasEnglish = true;
//                break;
//            }
//        }
//
//        if (!hasEnglish) {
//            addParseDateFormats(group, formats, Locale.ENGLISH);
//        }
//    }

//    /**
//     * <strong>Add</strong> formats to the given parser list.
//     *
//     * @param group   collection to add to
//     * @param formats list of formats to add
//     * @param locales to use
//     */
//    private static void addParseDateFormats(@NonNull final Collection<SimpleDateFormat> group,
//                                            @NonNull final String[][] formats,
//                                            @NonNull final Locale... locales) {
//        // track duplicates for each group separably
//        final Collection<Locale> added = new HashSet<>();
//        for (String[] groupFormats : formats) {
//            added.clear();
//            for (Locale locale : locales) {
//                if (!added.contains(locale)) {
//                    added.add(locale);
//                    for (String format : groupFormats) {
//                        group.add(new SimpleDateFormat(format, locale));
//                    }
//                }
//            }
//        }
//    }


    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString  String to parse
     * @param todayIfNone if {@code true}, and if the string was empty, default to today's date.
     *
     * @return the parsed time, or {@code null} on failure
     */
    @Nullable
    public static Long parseTime(@Nullable final String dateString,
                                 final boolean todayIfNone) {
        final Date date = parseDate(dateString);
        if (date != null) {
            return date.getTime();
        } else if (todayIfNone) {
            return Calendar.getInstance(LocaleUtils.getSystemLocale()).getTimeInMillis();
        } else {
            return null;
        }
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
     * @return Resulting date (with time==12:00:00) if parsed, otherwise {@code null}
     */
    @Nullable
    public static Date parseDate(@Nullable final String dateString) {

        final LocalDate ld = DateParser.DATE.parse(dateString);
        if (ld == null) {
            return null;
        }

        final Date date = Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
        //final Date date = parseDate(PARSE_DATE_FORMATS, dateString);

        // set time to noon, to avoid any overflow due to timezone or DST.
        if (date != null) {
            date.setHours(12);
            date.setMinutes(0);
            date.setSeconds(0);
        }
        return date;
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
//        return parseDate(PARSE_DATE_FORMATS, dateString);

        final LocalDateTime ldt = DateParser.DATETIME.parse(dateString);
        if (ldt == null) {
            return null;
        }
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
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
        // return parseDate(PARSE_SQL_DATE_FORMATS, dateString);
        final LocalDateTime ldt = DateParser.ISO.parse(dateString);
        if (ldt == null) {
            return null;
        }
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

//    /**
//     * Attempt to parse a date string based on a range of possible formats.
//     *
//     * @param formats    list of formats to try in order
//     * @param dateString String to parse
//     *
//     * @return Resulting date if parsed, otherwise {@code null}
//     */
//    @Nullable
//    private static Date parseDate(@NonNull final Iterable<SimpleDateFormat> formats,
//                                  @Nullable final String dateString) {
//        if (dateString == null || dateString.isEmpty()) {
//            return null;
//        }
//        // shortcut for plain 4 digit years.
//        if (dateString.length() == 4) {
//            try {
//                return YEAR.parse(dateString);
//            } catch (@NonNull final ParseException ignore) {
//                // ignore
//            }
//        }
//
//        // First try to parse using strict rules
//        final Date d = parseDate(formats, dateString, false);
//        if (d != null) {
//            return d;
//        }
//        // try again being lenient
//        return parseDate(formats, dateString, true);
//    }
//
//    /**
//     * Attempt to parse a date string based on a range of possible formats; allow
//     * for caller to specify if the parsing should be strict or lenient.
//     * <p>
//     * <strong>Note:</strong> the timestamp part is always set to 00:00:00
//     *
//     * @param formats    list of formats to try in order
//     * @param dateString String to parse
//     * @param lenient    {@code true} if parsing should be lenient
//     *
//     * @return Resulting date if successfully parsed, otherwise {@code null}
//     */
//    @Nullable
//    private static Date parseDate(@NonNull final Iterable<SimpleDateFormat> formats,
//                                  @NonNull final String dateString,
//                                  final boolean lenient) {
//        for (DateFormat df : formats) {
//            try {
//                df.setLenient(lenient);
//                return df.parse(dateString);
//            } catch (@NonNull final ParseException ignore) {
//                // ignore
//            }
//        }
//        return null;
//    }
}
