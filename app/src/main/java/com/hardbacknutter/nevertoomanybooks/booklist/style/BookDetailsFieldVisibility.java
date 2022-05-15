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

import android.content.SharedPreferences;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-details screen.
 *
 * Globally hidden fields stay hidden,
 * but globally visible fields can be hidden on a per-style base.
 */
public class BookDetailsFieldVisibility {

    public static final String PK_VISIBILITY = "style.details.show.fields";

    public static final int DETAILS_SHOW_COVER_0 = 1;
    public static final int DETAILS_SHOW_COVER_1 = 1 << 1;

    public static final int BITMASK_ALL = DETAILS_SHOW_COVER_0
                                          | DETAILS_SHOW_COVER_1;

    private static final int[] COVER_BITS = {DETAILS_SHOW_COVER_0,
                                            DETAILS_SHOW_COVER_1};

    /** by default, show both covers. */
    public static final int DEFAULT = DETAILS_SHOW_COVER_0
                                      | DETAILS_SHOW_COVER_1;

    private int bits;

    /**
     * Constructor.
     */
    BookDetailsFieldVisibility(@NonNull final SharedPreferences global) {
        bits = global.getInt(PK_VISIBILITY, DEFAULT);

        for (int cIdx = 0; cIdx < COVER_BITS.length; cIdx++) {
            setShowCover(cIdx,  DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx]));
        }
    }

    /**
     * Convenience method to check if a cover (front/back) should be
     * show on the <strong>details</strong> screen.
     *
     * @param cIdx 0..n image index
     *
     * @return {@code true} if in use
     */
    public boolean isShowCover(@IntRange(from = 0, to = 1) final int cIdx) {
        return (bits & COVER_BITS[cIdx]) != 0;
    }

    /**
     * Convenience method to set if a cover (front/back) should be
     * show on the <strong>details</strong> screen.
     *
     * @param cIdx 0..n image index
     * @param show value to set
     */
    public void setShowCover(final int cIdx,
                             final boolean show) {
        if (show) {
            bits |= COVER_BITS[cIdx];
        } else {
            bits &= ~COVER_BITS[cIdx];
        }
    }

    /**
     * Check if the given field should be displayed.
     *
     * @param bit to check
     *
     * @return {@code true} if in use
     */
    public boolean isShowField(@BooklistBookFieldVisibility.Option final int bit) {
        return (bits & bit) != 0;
    }

    public void setShowField(@BooklistBookFieldVisibility.Option final int bit,
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
        return "BookDetailsFieldVisibility{"
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
        final BookDetailsFieldVisibility that = (BookDetailsFieldVisibility) o;
        return bits == that.bits;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bits);
    }

    @IntDef(flag = true, value = {DETAILS_SHOW_COVER_0,
                                  DETAILS_SHOW_COVER_1,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Option {

    }
}
