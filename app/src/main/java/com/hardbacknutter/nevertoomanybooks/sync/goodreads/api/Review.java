/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.api;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Goodreads specific field names we add to the bundle based on parsed XML data.
 */
public final class Review {

    public static final String TOTAL = "__r_total";
    public static final String ISBN13 = "__r_isbn13";

    public static final String LARGE_IMAGE_URL = "__r_largeImage";
    public static final String SMALL_IMAGE_URL = "__r_smallImage";

    public static final String PUBLICATION_YEAR = "__r_pub_year";
    public static final String PUBLICATION_MONTH = "__r_pub_month";
    public static final String PUBLICATION_DAY = "__r_pub_day";

    /** <date_added>Mon Feb 13 05:32:30 -0800 2012</date_added>. */
    public static final String ADDED = "__r_added";
    /** <date_updated>Mon Feb 13 05:32:31 -0800 2012</date_updated>. */
    public static final String UPDATED = "__r_updated";

    public static final String REVIEWS = "__r_reviews";
    public static final String AUTHORS = "__r_authors";
    public static final String AUTHOR_NAME_GF = "__r_author_name";
    public static final String AUTHOR_ROLE = "__r_author_role";
    public static final String SHELF = "__r_shelf";
    public static final String SHELVES = "__r_shelves";
    public static final String PUBLISHER = "__r_publisher";

    /** Type: long. */
    public static final String PAGES = "__r_pages";

    static final String BODY = "__r_body";
    static final String REVIEW_ID = "__r_review_id";
    static final String START = "__r_start";
    static final String END = "__r_end";

    /** Date format used for parsing the date fields. */
    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter
            .ofPattern("EEE MMM dd HH:mm:ss ZZZZ yyyy",
                       ServiceLocator.getSystemLocale());

    private Review() {
    }

    @Nullable
    public static LocalDateTime parseDate(@Nullable final String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr, DATE_PARSER);
            //FIXME: DateTimeParseException https://issuetracker.google.com/issues/158417777
//            } catch (@NonNull final DateTimeParseException ignore) {
        } catch (@NonNull final RuntimeException ignore) {
            return null;
        }
    }

    /**
     * Check a field for being a valid date.
     * If it's valid, store the reformatted value back into the bundle.
     * If it's invalid, remove the field from the bundle.
     */
    static void validateDate(@NonNull final Bundle bundle,
                             @NonNull final String key) {
        if (bundle.containsKey(key)) {
            final LocalDateTime date = parseDate(bundle.getString(key));
            if (date != null) {
                bundle.putString(key, date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else {
                bundle.remove(key);
            }
        }
    }

    /**
     * Copy a valid datetime string from the source to the destination bundle.
     * The source bundle is not changed.
     *
     * @return ISO datetime string, or {@code null} if parsing failed
     */
    @Nullable
    public static String copyDateIfValid(@NonNull final Bundle sourceBundle,
                                         @NonNull final String sourceKey,
                                         @NonNull final Book destBundle,
                                         @NonNull final String destKey) {

        final LocalDateTime date = parseDate(sourceBundle.getString(sourceKey));
        if (date != null) {
            final String value = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            destBundle.putString(destKey, value);
            return value;
        }

        return null;
    }
}
