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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-list screen.
 * <p>
 * The defaults obey the global user preference.
 */
public class BooklistFieldVisibility {

    public static final String PK_VISIBILITY = "style.list.show.fields";

    public static final int SHOW_COVER_0 = 1;
    public static final int SHOW_COVER_1 = 1 << 1;
    public static final int SHOW_AUTHOR = 1 << 2;
    public static final int SHOW_PUBLISHER = 1 << 3;
    public static final int SHOW_PUB_DATE = 1 << 4;
    public static final int SHOW_ISBN = 1 << 5;
    public static final int SHOW_FORMAT = 1 << 6;
    public static final int SHOW_LOCATION = 1 << 7;
    public static final int SHOW_RATING = 1 << 8;
    public static final int SHOW_BOOKSHELVES = 1 << 9;

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

    /** by default, only show the cover. Other fields are hidden. */
    public static final int DEFAULT = SHOW_COVER_0;

    private int bits = DEFAULT;

    /**
     * Constructor.
     */
    BooklistFieldVisibility() {
        // Load the defaults from the global Visibility preferences
        setShowField(SHOW_COVER_0, DBKey.isUsed(DBKey.COVER_IS_USED[0]));
        setShowField(SHOW_COVER_1, DBKey.isUsed(DBKey.COVER_IS_USED[1]));
        setShowField(SHOW_AUTHOR, DBKey.isUsed(DBKey.FK_AUTHOR));
        setShowField(SHOW_PUBLISHER, DBKey.isUsed(DBKey.FK_PUBLISHER));
        setShowField(SHOW_PUB_DATE, DBKey.isUsed(DBKey.DATE_BOOK_PUBLICATION));
        setShowField(SHOW_ISBN, DBKey.isUsed(DBKey.KEY_ISBN));
        setShowField(SHOW_FORMAT, DBKey.isUsed(DBKey.BOOK_FORMAT));
        setShowField(SHOW_LOCATION, DBKey.isUsed(DBKey.LOCATION));
        setShowField(SHOW_RATING, DBKey.isUsed(DBKey.RATING));
        setShowField(SHOW_BOOKSHELVES, DBKey.isUsed(DBKey.FK_BOOKSHELF));
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
    String getSummaryText(@NonNull final Context context) {
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
     * @param dbKey to check - one of the {@link DBKey} constants.
     *
     * @return {@code true} if in use
     */
    @NonNull
    public Optional<Boolean> isShowField(@NonNull final String dbKey) {
        if (DBKey.COVER_IS_USED[0].equals(dbKey)) {
            return Optional.of(isShowField(SHOW_COVER_0));
        } else if (DBKey.COVER_IS_USED[1].equals(dbKey)) {
            return Optional.of(isShowField(SHOW_COVER_1));
        }
        switch (dbKey) {
            case DBKey.FK_AUTHOR:
                return Optional.of(isShowField(SHOW_AUTHOR));
            case DBKey.FK_BOOKSHELF:
                return Optional.of(isShowField(SHOW_BOOKSHELVES));
            case DBKey.FK_PUBLISHER:
                return Optional.of(isShowField(SHOW_PUBLISHER));

            case DBKey.BOOK_FORMAT:
                return Optional.of(isShowField(SHOW_FORMAT));
            case DBKey.KEY_ISBN:
                return Optional.of(isShowField(SHOW_ISBN));
            case DBKey.LOCATION:
                return Optional.of(isShowField(SHOW_LOCATION));
            case DBKey.DATE_BOOK_PUBLICATION:
                return Optional.of(isShowField(SHOW_PUB_DATE));
            case DBKey.RATING:
                return Optional.of(isShowField(SHOW_RATING));
            default:
                return Optional.empty();
        }
    }

    private boolean isShowField(@Option final int bit) {
        return (bits & bit) != 0;
    }

    public void setShowField(@NonNull final String dbKey,
                             final boolean show) {
        if (DBKey.COVER_IS_USED[0].equals(dbKey)) {
            setShowField(SHOW_COVER_0, show);
        } else if (DBKey.COVER_IS_USED[1].equals(dbKey)) {
            setShowField(SHOW_COVER_1, show);
        }
        //noinspection SwitchStatementWithoutDefaultBranch
        switch (dbKey) {
            case DBKey.FK_AUTHOR:
                setShowField(SHOW_AUTHOR, show);
                break;
            case DBKey.FK_BOOKSHELF:
                setShowField(SHOW_BOOKSHELVES, show);
                break;
            case DBKey.FK_PUBLISHER:
                setShowField(SHOW_PUBLISHER, show);
                break;

            case DBKey.BOOK_FORMAT:
                setShowField(SHOW_FORMAT, show);
                break;
            case DBKey.KEY_ISBN:
                setShowField(SHOW_ISBN, show);
                break;
            case DBKey.LOCATION:
                setShowField(SHOW_LOCATION, show);
                break;
            case DBKey.DATE_BOOK_PUBLICATION:
                setShowField(SHOW_PUB_DATE, show);
                break;
            case DBKey.RATING:
                setShowField(SHOW_RATING, show);
                break;
        }
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
        return "BooklistFieldVisibility{"
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
        final BooklistFieldVisibility that = (BooklistFieldVisibility) o;
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
