/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.utils.dates;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

/**
 * //URGENT: DateTimeParseException https://issuetracker.google.com/issues/158417777
 * Singleton.
 */
public class DateParser
        implements AppLocale.OnLocaleChangedListener {

    /** All numerical (i.e. Locale independent) patterns. */
    private static final String[] NUMERICAL = {
            // US format first
            "MM-dd-yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm",

            // International next
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",

            // same without time
            "MM-dd-yyyy",
            "dd-MM-yyyy",
            };
    /** Patterns with Locale dependent text. */
    private static final String[] TEXT = {
            // These are the wide spread common formats
            "dd-MMM-yyyy",
            "dd-MMMM-yyyy",
            "dd-MMM-yy",
            "dd-MMMM-yy",

            // Used by Goodreads; Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
            "EEE MMM dd HH:mm:ss ZZZZ yyyy",

            // Used by Amazon; e.g. "12 jan. 2017"
            "dd MMM. yyyy",

            // Used by OpenLibrary
            "dd MMM yyyy",
            "dd MMMM yyyy",
            "MMM d, yyyy",
            "MMMM d, yyyy",
            "MMM yyyy",
            "MMMM yyyy",
            };

    /** Singleton. */
    private static DateParser sInstance;

    /**
     * Variant of DateTimeFormatter.ISO_DATE_TIME using a space instead of the normal 'T'
     * '2011-12-03 10:15:30',
     * '2011-12-03 10:15:30+01:00'
     * '2011-12-03 10:15:30+01:00[Europe/Paris]'
     */
    @SuppressWarnings({"FieldCanBeLocal", "WeakerAccess"})
    public static DateTimeFormatter SQLITE_ISO_DATE_TIME;

    /** List of patterns we'll use to parse dates. */
    private final Collection<DateTimeFormatter> TEXT_PARSERS = new ArrayList<>();
    private final Collection<DateTimeFormatter> NUMERICAL_PARSERS = new ArrayList<>();
    /** List of patterns we'll use to parse ISO datetime stamps.. */
    private final Collection<DateTimeFormatter> ISO_PARSERS = new ArrayList<>();

    /**
     * Constructor. Use {@link #getInstance(Context)}.
     */
    private DateParser() {
    }

    /**
     * Get/create the singleton instance.
     *
     * @param context Current context; only used the single time the instance gets created.
     *
     * @return instance
     */
    @NonNull
    public static DateParser getInstance(@NonNull final Context context) {
        synchronized (DateParser.class) {
            if (sInstance == null) {
                sInstance = new DateParser();
                sInstance.create(AppLocale.getInstance().getUserLocale(context),
                                 AppLocale.getInstance().getSystemLocale());
                AppLocale.getInstance().registerOnLocaleChangedListener(sInstance);
            }
            return sInstance;
        }
    }

    /**
     * Get/create the singleton instance.
     * For testing purposes only: the singleton is created each time and we don't register
     * the OnLocaleChangedListener listener.
     * Other than that it is identical in it's logic.
     *
     * @param locales the locales to use
     *
     * @return instance
     */
    @VisibleForTesting
    @NonNull
    public static DateParser getTestInstance(@NonNull final Locale... locales) {
        sInstance = new DateParser();
        sInstance.create(locales);
        return sInstance;
    }

    /**
     * Create the parser lists.
     *
     * @param locales the locales to use
     */
    private void create(@NonNull final Locale... locales) {
        // allow recreating
        ISO_PARSERS.clear();

        final Locale systemLocale = AppLocale.getInstance().getSystemLocale();

        SQLITE_ISO_DATE_TIME = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                // A space instead of the normal 'T'
                .appendLiteral(' ')
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalStart()
                .appendLiteral('[')
                .parseCaseSensitive()
                .appendZoneRegionId()
                .appendLiteral(']')
                // Uses ResolverStyle.SMART and 'null' Chronology
                .toFormatter(systemLocale);

        // '2011-12-03T10:15:30',
        // '2011-12-03T10:15:30+01:00'
        // '2011-12-03T10:15:30+01:00[Europe/Paris]'
        // Uses ResolverStyle.STRICT / IsoChronology.INSTANCE
        // This parser includes ISO_LOCAL_DATE_TIME
        ISO_PARSERS.add(DateTimeFormatter.ISO_DATE_TIME);
        // and the same but with a ' ' separator.
        ISO_PARSERS.add(SQLITE_ISO_DATE_TIME);

        NUMERICAL_PARSERS.clear();
        addPatterns(NUMERICAL_PARSERS, NUMERICAL, systemLocale);

        TEXT_PARSERS.clear();
        addPatterns(TEXT_PARSERS, TEXT, locales);
        addEnglish(TEXT_PARSERS, TEXT, locales);
    }

    /**
     * <strong>Add</strong> patterns to the given group.
     *
     * @param group    collection to add to
     * @param patterns list of patterns to add
     * @param locales  to use
     */
    private void addPatterns(@NonNull final Collection<DateTimeFormatter> group,
                             @NonNull final String[] patterns,
                             @NonNull final Locale... locales) {
        // prevent duplicate locales
        final Collection<Locale> added = new HashSet<>();
        for (Locale locale : locales) {
            if (!added.contains(locale)) {
                added.add(locale);
                for (String pattern : patterns) {
                    final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .appendPattern(pattern)
                            // Allow the day of the month to be missing and use '1'
                            .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
                            // Allow the time to be completely missing.
                            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);

                    group.add(builder.toFormatter(locale));
                }
            }
        }
    }

    /**
     * Create an English variant of a parser if English not already in the list of locales.
     */
    private void addEnglish(@SuppressWarnings("SameParameterValue")
                            @NonNull final Collection<DateTimeFormatter> group,
                            @SuppressWarnings("SameParameterValue")
                            @NonNull final String[] patterns,
                            @NonNull final Locale[] locales) {
        boolean hasEnglish = false;
        for (Locale locale : locales) {
            if (Locale.ENGLISH.getISO3Language().equals(locale.getISO3Language())) {
                hasEnglish = true;
                break;
            }
        }
        if (!hasEnglish) {
            addPatterns(group, patterns, Locale.ENGLISH);
        }
    }

    /**
     * Attempt to parse a date string.
     *
     * @param dateStr String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    public LocalDateTime parse(@Nullable final String dateStr) {
        return parse(dateStr, null);
    }

    /**
     * Attempt to parse a date string using the passed locale.
     * This method is meant to be used by site-specific code where the site Locale is known.
     *
     * @param dateStr String to parse
     * @param locale  to try first; i.e. before the pre-defined list.
     *
     * @return Resulting date if successfully parsed, otherwise {@code null}
     */
    @Nullable
    public LocalDateTime parse(@Nullable final String dateStr,
                               @Nullable final Locale locale) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        LocalDateTime result = parseISO(dateStr);
        if (result == null) {
            result = parse(NUMERICAL_PARSERS, dateStr, locale);
            if (result == null) {
                result = parse(TEXT_PARSERS, dateStr, locale);
            }
        }

        return result;
    }

    /**
     * Attempt to parse a date string using ISO parsers.
     * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
     * If the year is missing, {@code null} is returned.
     *
     * @param dateStr String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    public LocalDateTime parseISO(@Nullable final String dateStr) {
        if (dateStr == null) {
            return null;
        }

        final int len = dateStr.length();
        // invalid lengths
        if (len < 4 || len == 5 || len == 6 || len == 8 || len == 9) {
            return null;
        }

        // Check the fixed patterns first. This has proven to be easier/faster than
        // trying to use DateTimeFormatter for date-strings without a time part.
        switch (len) {
            case 4:
                // yyyy
                try {
                    return Year.parse(dateStr).atDay(1).atStartOfDay();
//            } catch (@NonNull final DateTimeParseException ignore) {
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore and try the next one
                }
                break;

            // yyyy-MM
            case 7:
                try {
                    final Year year = Year.parse(dateStr.substring(0, 4));
                    final int mont = Integer.parseInt(dateStr.substring(5, 7));
                    return year.atMonth(mont).atDay(1).atStartOfDay();
//            } catch (@NonNull final DateTimeParseException ignore) {
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore and try the next one
                }
                break;

            // yyyy-MM-dd
            case 10:
                try {
                    return LocalDate.parse(dateStr).atStartOfDay();
//            } catch (@NonNull final DateTimeParseException ignore) {
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore and try the next one
                }
                break;

            default:
                break;
        }

        // try full date+time strings
        return parse(ISO_PARSERS, dateStr, null);
    }

    /**
     * Attempt to parse a date string.
     * Any missing parts of the pattern will get set to default: 1-Jan, 00:00:00
     * If the year is missing, {@code null} is returned.
     *
     * @param dateStr String to parse
     * @param locale  (optional) Locale to apply/try before the default list.
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    private LocalDateTime parse(@NonNull final Iterable<DateTimeFormatter> parsers,
                                @NonNull final CharSequence dateStr,
                                @Nullable final Locale locale) {

        // Try the specified Locale first
        if (locale != null) {
            for (DateTimeFormatter dtf : parsers) {
                try {
                    return LocalDateTime.parse(dateStr, dtf.withLocale(locale));
//            } catch (@NonNull final DateTimeParseException ignore) {
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore and try the next one
                }
            }
        }

        // Parse with the default locales, using the default ResolverStyle
        for (DateTimeFormatter dtf : parsers) {
            try {
                return LocalDateTime.parse(dateStr, dtf);
//            } catch (@NonNull final DateTimeParseException ignore) {
            } catch (@NonNull final RuntimeException ignore) {
                // ignore and try the next one
            }
        }

        // 2020-06-02: disabled 'lenient' as it seems to cause more false results than good
//        // Try again being lenient (ResolverStyle.LENIENT)
//        for (DateTimeFormatter dtf : parsers) {
//            try {
//                // Keep in mind this creates a new copy of the formatter.
//                return LocalDateTime.parse(dateStr, dtf.withResolverStyle(ResolverStyle.LENIENT));
//            } catch (@NonNull final DateTimeParseException ignore) {
//                // ignore and try the next one
//            }
//        }

        // give up
        return null;
    }

    @Override
    public void onLocaleChanged(@NonNull final Context context) {
        create(AppLocale.getInstance().getUserLocale(context),
               AppLocale.getInstance().getSystemLocale());
    }
}
