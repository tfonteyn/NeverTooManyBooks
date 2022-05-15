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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Encapsulate the Book fields which can be shown on the Book-list screen.
 * <p>
 * Globally hidden field settings are ignored!
 */
public class BooklistBookFieldVisibility {

    public static final String PK_VISIBILITY = "style.list.show.fields";

    public static final int SHOW_COVER_0 = 1;
    public static final int SHOW_AUTHOR = 1 << 2;
    public static final int SHOW_PUBLISHER = 1 << 3;
    public static final int SHOW_PUB_DATE = 1 << 4;
    public static final int SHOW_ISBN = 1 << 5;
    public static final int SHOW_FORMAT = 1 << 6;
    public static final int SHOW_LOCATION = 1 << 7;
    public static final int SHOW_RATING = 1 << 8;
    public static final int SHOW_BOOKSHELVES = 1 << 9;

    /** by default, only show the cover. Other fields are hidden. */
    public static final int DEFAULT = SHOW_COVER_0;

    // not currently used
    private static final int SHOW_COVER_1 = 1 << 1;

    public static final int BITMASK_ALL = SHOW_COVER_0
                                          | SHOW_COVER_1
                                          | SHOW_AUTHOR
                                          | SHOW_PUBLISHER
                                          | SHOW_PUB_DATE
                                          | SHOW_ISBN
                                          | SHOW_FORMAT
                                          | SHOW_LOCATION
                                          | SHOW_RATING
                                          | SHOW_BOOKSHELVES;
    private int bits;

    /**
     * Constructor.
     */
    BooklistBookFieldVisibility(@NonNull final SharedPreferences global) {
        bits = global.getInt(PK_VISIBILITY, DEFAULT);
    }

    /**
     * Get the list of in-use book-detail-field names in a human readable format.
     * This is used to set the summary of the PreferenceScreen.
     *
     * @param context Current context
     *
     * @return list of labels, can be empty, but never {@code null}
     */
    @NonNull
    private List<String> getLabels(@NonNull final Context context) {
        final List<String> labels = new ArrayList<>();

        if ((bits & SHOW_COVER_0) != 0) {
            labels.add(context.getString(R.string.lbl_covers));
        }
        if ((bits & SHOW_AUTHOR) != 0) {
            labels.add(context.getString(R.string.lbl_author));
        }
        if ((bits & SHOW_PUBLISHER) != 0) {
            labels.add(context.getString(R.string.lbl_publisher));
        }
        if ((bits & SHOW_PUB_DATE) != 0) {
            labels.add(context.getString(R.string.lbl_date_published));
        }
        if ((bits & SHOW_ISBN) != 0) {
            labels.add(context.getString(R.string.lbl_isbn));
        }
        if ((bits & SHOW_FORMAT) != 0) {
            labels.add(context.getString(R.string.lbl_format));
        }
        if ((bits & SHOW_LOCATION) != 0) {
            labels.add(context.getString(R.string.lbl_location));
        }
        if ((bits & SHOW_RATING) != 0) {
            labels.add(context.getString(R.string.lbl_rating));
        }
        if ((bits & SHOW_BOOKSHELVES) != 0) {
            labels.add(context.getString(R.string.lbl_bookshelves));
        }

        Collections.sort(labels);
        return labels;
    }

    /**
     * Convenience method for use in the Preferences screen.
     * Get the summary text for the book fields to show in lists.
     *
     * @param context Current context
     *
     * @return summary text
     */
    @NonNull
    public String getSummaryText(@NonNull final Context context) {
        final List<String> labels = getLabels(context);
        if (labels.isEmpty()) {
            return context.getString(R.string.none);
        } else {
            return String.join(", ", labels);
        }
    }

    /**
     * Check if the given field should be displayed.
     *
     * @param bit to check
     *
     * @return {@code true} if in use
     */
    public boolean isShowField(@Option final int bit) {
        return (bits & bit) != 0;
    }

    public void setShowField(@Option final int bit,
                             final boolean show) {
        if (show) {
            bits |= bit & BITMASK_ALL;
        } else {
            bits &= ~bit;
        }
    }

    public int getValue() {
        return bits;
    }

    public void setValue(final int value) {
        bits = value & BITMASK_ALL;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistBookFieldVisibility{"
               + "bits=0b" + Integer.toBinaryString(bits)
               + '}';
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BooklistBookFieldVisibility that = (BooklistBookFieldVisibility) o;
        return bits == that.bits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bits);
    }

    @IntDef(flag = true, value = {SHOW_COVER_0,
                                  SHOW_COVER_1,
                                  SHOW_AUTHOR,
                                  SHOW_PUBLISHER,
                                  SHOW_PUB_DATE,
                                  SHOW_ISBN,
                                  SHOW_FORMAT,
                                  SHOW_LOCATION,
                                  SHOW_RATING,
                                  SHOW_BOOKSHELVES
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Option {

    }
}
