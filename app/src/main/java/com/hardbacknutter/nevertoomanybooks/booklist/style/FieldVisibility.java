/*
 * @Copyright 2018-2024 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;

/**
 * Notes to self...
 * <p>
 * <strong>The GLOBAL visibility</strong>
 * <br/>
 * This is where the user can disable fields which they don't care about and don't
 * want to edit/view ANYWHERE AT ALL.
 * <br/>The setting is stored as a bitmask value in a preference key.
 * <br/>See {@link com.hardbacknutter.nevertoomanybooks.settings.FieldVisibilityPreferenceFragment}
 * <br/>and {@link com.hardbacknutter.nevertoomanybooks.ServiceLocator#isFieldEnabled(String)}.
 * <p>
 * <strong>The STYLE visibility</strong>
 * <br/>
 * There are two sets of field-visibilities:
 * <br/>{@link Screen#List} decides if certain fields are displayed on the book-level in the list.
 * <br/>{@link Screen#Detail} does the same for the (read-only) book-details screen.
 * <br/>For fields which are not configurable on these levels,
 * <strong>The GLOBAL visibility</strong> is used.
 * <br/>These settings are stored as bitmasks on the individual styles in the database Styles table.
 * <br/>See {@link com.hardbacknutter.nevertoomanybooks.settings.styles.StyleDefaultsFragment}
 * <br/>and {@link Style#isShowField(Screen, String)}.
 * <p>
 * <strong>The {@link GlobalStyle}</strong>
 * Provides the defaults for all styles.
 * <br/>Always used for the {@link BuiltinStyle}s.
 * <br/>Used as the defaults at <strong>creation time</strong> for the {@link UserStyle}s.
 */
public class FieldVisibility {

    /**
     * <strong>ALL</strong> keys which support visibility configuration.
     * The position in the list represents their bit-number in the {@link #bits} value.
     * <p>
     * <strong>NEVER CHANGE THE ORDER. NEW ENTRIES MUST BE ADDED AT THE END.</strong>
     *
     * NEWTHINGS: BookLevelField: add new field
     */
    private static final List<String> DB_KEYS = List.of(
            // bit 0..3
            DBKey.COVER[0],
            DBKey.COVER[1],
            DBKey.FK_AUTHOR,
            DBKey.FK_BOOKSHELF,

            // bit 4..7
            DBKey.FK_SERIES,
            DBKey.FK_PUBLISHER,
            DBKey.FK_TOC_ENTRY,
            // represents "lending functionality enabled"
            DBKey.LOANEE_NAME,

            // bit 8..11
            DBKey.AUTHOR_TYPE__BITMASK,
            DBKey.BOOK_CONDITION,
            DBKey.BOOK_CONDITION_COVER,
            DBKey.BOOK_ISBN,

            // bit 12..15
            DBKey.BOOK_PUBLICATION__DATE,
            DBKey.COLOR,
            DBKey.DESCRIPTION,
            DBKey.EDITION__BITMASK,

            // bit 16..19
            DBKey.FIRST_PUBLICATION__DATE,
            DBKey.FORMAT,
            DBKey.GENRE,
            DBKey.LANGUAGE,

            // bit 20..23
            DBKey.LOCATION,
            DBKey.PAGE_COUNT,
            DBKey.PRICE_LISTED,
            DBKey.PRICE_PAID,

            // bit 24..27
            DBKey.PERSONAL_NOTES,
            DBKey.RATING,
            DBKey.SIGNED__BOOL,
            DBKey.READ__BOOL,

            // bit 28..31
            DBKey.READ_START__DATE,
            DBKey.READ_END__DATE,
            DBKey.DATE_ADDED__UTC,
            DBKey.DATE_LAST_UPDATED__UTC,

            // bit 32..35
            DBKey.TITLE_ORIGINAL_LANG,
            // represents "show the real author if 'this' is a pen-name"
            DBKey.AUTHOR_REAL_AUTHOR,
            // This is the detailed progress only.
            // The read/unread status is always displayed.
            DBKey.READ_PROGRESS
    );

    @NonNull
    private final Set<String> dbKeys;

    private long bits;

    /**
     * Constructor.
     * <p>
     * Global visibility: use all fields which by default are all visible.
     */
    public FieldVisibility() {
        dbKeys = Set.copyOf(DB_KEYS);
        bits = Long.MAX_VALUE;
    }

    /**
     * Copy constructor.
     *
     * @param fieldVisibility object to copy
     */
    public FieldVisibility(@NonNull final FieldVisibility fieldVisibility) {
        dbKeys = Set.copyOf(fieldVisibility.dbKeys);
        bits = fieldVisibility.bits;
    }

    /**
     * Constructor.
     * <p>
     * Style conditional visibility: use the given subset of keys.
     *
     * @param dbKeys the (sub)set of keys supported for this instance
     * @param bits   the bitmask with the visible fields
     */
    FieldVisibility(@NonNull final Set<String> dbKeys,
                    final long bits) {
        this.dbKeys = dbKeys;
        this.bits = bits;
    }

    /**
     * Get the combined bit-value for the given set of keys.
     *
     * @param keys to fetch
     *
     * @return bitmask
     *
     * @throws IllegalArgumentException if any key is invalid
     */
    public static long getBitValue(@NonNull final Set<String> keys)
            throws IllegalArgumentException {
        return keys.stream()
                   .mapToLong(dbKey -> {
                       final int index = DB_KEYS.indexOf(dbKey);
                       if (index == -1) {
                           throw new IllegalArgumentException(dbKey);
                       } else {
                           return 1L << index;
                       }
                   })
                   .reduce(0, (a, b) -> a | b);
    }

    /**
     * Get the current configured combined bit-value.
     *
     * @return bitmask
     */
    public long getBitValue() {
        return bits;
    }

    /**
     * Set the current configured combined bit-value.
     *
     * @param value bitmask
     */
    public void setBitValue(final long value) {
        bits = value;
    }


    /**
     * Get a {@code Set} of the {@link DBKey}s supported by this instance.
     *
     * @param all set to {@code true} to get all keys regardless of visibility;
     *            Use {@code false} to get only the currently visible fields (keys).
     *
     * @return a new unmodifiable Set
     */
    @NonNull
    public Set<String> getKeys(final boolean all) {
        if (all) {
            return Set.copyOf(dbKeys);
        } else {
            return dbKeys.stream()
                         .filter(key -> isVisible(key).orElseThrow())
                         .collect(Collectors.toSet());
        }
    }

    /**
     * Check if the given field should be displayed.
     * <p>
     * An invalid key will return {@code Optional.empty()}
     *
     * @param dbKey to check - one of the {@link DBKey} constants.
     *
     * @return Optional
     */
    @NonNull
    public Optional<Boolean> isVisible(@NonNull final String dbKey) {
        if (dbKeys.contains(dbKey)) {
            final int index = DB_KEYS.indexOf(dbKey);
            if (index != -1) {
                return Optional.of((bits & (1L << index)) != 0);
            }
        }
        return Optional.empty();
    }

    /**
     * Set/unset the bit-value for the given {@link DBKey}.
     * <p>
     * An invalid key will be ignored.
     *
     * @param dbKey to set - one of the {@link DBKey} constants.
     * @param show  flag
     */
    @SuppressWarnings("WeakerAccess")
    public void setVisible(@NonNull final String dbKey,
                           final boolean show) {
        if (dbKeys.contains(dbKey)) {
            final int index = DB_KEYS.indexOf(dbKey);
            if (index >= 0) {
                final long bit = 1L << index;
                if (show) {
                    bits |= bit;
                } else {
                    bits &= ~bit;
                }
            }
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "FieldVisibility{"
               + "bits=0b" + Long.toBinaryString(bits)
               + ", dbKeys=" + dbKeys
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
        final FieldVisibility that = (FieldVisibility) o;
        return bits == that.bits
               && Objects.equals(dbKeys, that.dbKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bits, dbKeys);
    }

    /**
     * Which visibility options to use from the user preferences/style.
     */
    public enum Screen {
        /**
         * Based on Style, for use in a list/adapter.
         * The bitmask value is stored for each Style in the styles database table.
         */
        List,
        /**
         * Based on Style, for use on a details screen, dialog, ...
         * The bitmask value is stored for each Style in the styles database table.
         */
        Detail
    }
}
