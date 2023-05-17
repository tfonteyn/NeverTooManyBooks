/*
 * @Copyright 2018-2023 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

public abstract class FieldVisibility {

    /**
     * All keys which support visibility configuration.
     * The position in the list represents their bit-number in the {@link #bits} value.
     * <p>
     * <strong>NEVER CHANGE THE ORDER. NEW ENTRIES MUST BE ADDED AT THE END.</strong>
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
            DBKey.TITLE_ORIGINAL_LANG
    );

    /** Simple mapping for {@link #DB_KEYS} to the label to show the user. */
    private static final List<Integer> LABELS = List.of(
            R.string.lbl_cover_front,
            R.string.lbl_cover_back,
            R.string.lbl_author,
            R.string.lbl_bookshelves,

            R.string.lbl_series,
            R.string.lbl_publisher,
            R.string.lbl_table_of_content,
            R.string.lbl_lending,

            R.string.lbl_author_type,
            R.string.lbl_condition,
            R.string.lbl_dust_cover,
            R.string.lbl_isbn,

            R.string.lbl_date_published,
            R.string.lbl_color,
            R.string.lbl_description,
            R.string.lbl_edition,

            R.string.lbl_date_first_publication,
            R.string.lbl_format,
            R.string.lbl_genre,
            R.string.lbl_language,

            R.string.lbl_location,
            R.string.lbl_pages,
            R.string.lbl_price_listed,
            R.string.lbl_price_paid,

            R.string.lbl_personal_notes,
            R.string.lbl_rating,
            R.string.lbl_signed,
            R.string.lbl_read,

            R.string.lbl_read_start,
            R.string.lbl_read_end,
            R.string.lbl_date_added,
            R.string.lbl_date_last_updated,
            R.string.lbl_original_title
    );

    @NonNull
    private final Set<String> keys;
    private long bits;

    /**
     * Constructor.
     *
     * @param keys     the (sub)set of keys supported for this instance
     * @param defValue the bitmask with the defaults for this instance
     */
    FieldVisibility(@NonNull final Set<String> keys,
                    final long defValue) {
        this.keys = keys;
        bits = defValue;
    }

    /**
     * Get the matching label for the given key.
     *
     * @param context Current context
     * @param dbKey   to fetch
     *
     * @return human readable label
     *
     * @throws IllegalArgumentException if the key is invalid
     */
    @NonNull
    public static String getLabel(@NonNull final Context context,
                                  @NonNull final String dbKey)
            throws IllegalArgumentException {
        final int index = DB_KEYS.indexOf(dbKey);
        if (index == -1) {
            throw new IllegalArgumentException(dbKey);
        } else {
            return context.getString(LABELS.get(index));
        }
    }

    /**
     * Get the matching bit-value for the given key.
     *
     * @param dbKey to fetch
     *
     * @return bit value
     *
     * @throws IllegalArgumentException if the key is invalid
     */
    public static long getBitValue(@NonNull final String dbKey)
            throws IllegalArgumentException {
        final int index = DB_KEYS.indexOf(dbKey);
        if (index == -1) {
            throw new IllegalArgumentException(dbKey);
        } else {
            return 1L << index;
        }
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
    static long getBitValue(@NonNull final Set<String> keys)
            throws IllegalArgumentException {
        return keys.stream()
                   .mapToLong(FieldVisibility::getBitValue)
                   .reduce(0, (a, b) -> a | b);
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
    public Optional<Boolean> isShowField(@NonNull final String dbKey) {
        if (keys.contains(dbKey)) {
            final int index = DB_KEYS.indexOf(dbKey);
            if (index != -1) {
                return Optional.of((bits & (1L << index)) != 0);
            }
        }
        return Optional.empty();
    }

    /**
     * Set/unset the bit-value for the given key.
     * <p>
     * An invalid key will be ignored.
     *
     * @param dbKey to set
     * @param show  flag
     */
    public void setShowField(@NonNull final String dbKey,
                             final boolean show) {
        if (keys.contains(dbKey)) {
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

    /**
     * Get the current configured combined bit-value.
     *
     * @return bitmask
     */
    public long getValue() {
        return bits;
    }

    /**
     * Set the current configured combined bit-value.
     *
     * @param value bitmask
     */
    public void setValue(final long value) {
        bits = value;
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

        for (int i = 0; i < DB_KEYS.size(); i++) {
            final String key = DB_KEYS.get(i);
            if (isShowField(key).isPresent() && isShowField(key).get()) {
                labels.add(context.getString(LABELS.get(i)));
            }
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

    @Override
    @NonNull
    public String toString() {
        return "FieldVisibility{"
               + "bits=0b" + Long.toBinaryString(bits)
               + ", keys=" + keys
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
               && Objects.equals(keys, that.keys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bits, keys);
    }
}
