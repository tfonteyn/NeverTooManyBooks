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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Encapsulate the Book fields which can be shown on the Book-details screen.
 * <p>
 * The defaults obey the global user preference.
 */
public class BookDetailsFieldVisibility {

    public static final String PK_VISIBILITY = "style.details.show.fields";

    public static final int SHOW_COVER_0 = 1;
    public static final int SHOW_COVER_1 = 1 << 1;

    public static final int BITMASK_ALL = SHOW_COVER_0
                                          | SHOW_COVER_1;

    /** by default, show both covers. */
    public static final int DEFAULT = SHOW_COVER_0
                                      | SHOW_COVER_1;

    private int bits = DEFAULT;

    /**
     * Constructor.
     */
    BookDetailsFieldVisibility() {
        // Load the defaults from the global Visibility preferences
        setShowField(SHOW_COVER_0, DBKey.isUsed(DBKey.COVER_IS_USED[0]));
        setShowField(SHOW_COVER_1, DBKey.isUsed(DBKey.COVER_IS_USED[1]));

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

        return Optional.empty();
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

    public void setShowField(@NonNull final String dbKey,
                             final boolean show) {
        if (DBKey.COVER_IS_USED[0].equals(dbKey)) {
            setShowField(SHOW_COVER_0, show);
        } else if (DBKey.COVER_IS_USED[1].equals(dbKey)) {
            setShowField(SHOW_COVER_1, show);
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

    @IntDef(flag = true, value = {SHOW_COVER_0,
                                  SHOW_COVER_1,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Option {

    }
}
