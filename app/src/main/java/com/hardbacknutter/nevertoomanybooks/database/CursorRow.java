/*
 * @Copyright 2018-2025 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.database.Cursor;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.core.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.core.parsers.RealNumberParser;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * A handy wrapper allowing to fetch columns by name.
 * <p>
 * <strong>Note:</strong> converts {@code null} Strings to an empty String
 * and {@code null} numbers to {@code 0}.
 * <p>
 * Tip: when using a CursorRow as a parameter to a constructor, e.g.
 * {@link com.hardbacknutter.nevertoomanybooks.entities.Bookshelf#Bookshelf(long, DataHolder)}
 * always pass the id additionally/separately. This gives the calling code a change to use
 * for example the foreign key id.
 */
public class CursorRow
        implements DataHolder {

    /** the mapped cursor. */
    @NonNull
    private final Cursor cursor;

    private final Map<String, Integer> columnIndexCache = new HashMap<>();

    /**
     * Constructor.
     *
     * @param cursor to read from
     */
    public CursorRow(@NonNull final Cursor cursor) {
        this.cursor = cursor;

        final String[] names = cursor.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            columnIndexCache.put(names[i], i);
        }
    }

    @Override
    @NonNull
    public Set<String> keySet() {
        return Set.copyOf(columnIndexCache.keySet());
    }

    /**
     * Returns {@code true} if the given key is contained in the mapping
     * of this DataHolder.
     *
     * @param key the domain to get
     *
     * @return {@code true} if this cursor contains the specified domain.
     */
    @Override
    public boolean contains(@NonNull final String key) {
        return columnIndexCache.get(key) != null;
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key,
                            @Nullable final String defValue)
            throws ColumnNotPresentException {

        final Integer col = columnIndexCache.get(key);
        if (col == null) {
            throw new ColumnNotPresentException(key);
        }
        if (cursor.isNull(col)) {
            return defValue;
        }
        return cursor.getString(col);
    }

    /**
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public boolean getBoolean(@NonNull final String key)
            throws ColumnNotPresentException {
        return getInt(key) == 1;
    }

    /**
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public int getInt(@NonNull final String key)
            throws ColumnNotPresentException {

        final Integer col = columnIndexCache.get(key);
        if (col == null) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getInt(col);
    }

    /**
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public long getLong(@NonNull final String key)
            throws ColumnNotPresentException {

        final Integer col = columnIndexCache.get(key);
        if (col == null) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getLong(col);
    }

    /**
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    @NonNull
    public BigDecimal getBigDecimal(@NonNull final String key,
                                    @NonNull final RealNumberParser parser)
            throws NumberFormatException {

        final Integer col = columnIndexCache.get(key);
        if (col == null) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        final String s = cursor.getString(col);
        return new BigDecimal(s);
    }

    /**
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public double getDouble(@NonNull final String key,
                            @NonNull final RealNumberParser unused)
            throws NumberFormatException {

        final Integer col = columnIndexCache.get(key);
        if (col == null) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getDouble(col);
    }

    /**
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public float getFloat(@NonNull final String key,
                          @NonNull final RealNumberParser unused)
            throws NumberFormatException {

        final Integer col = columnIndexCache.get(key);
        if (col == null) {
            throw new ColumnNotPresentException(key);
        }
        // if (cursor.isNull(col)) {
        //     return 0;
        // }
        return cursor.getFloat(col);
    }

    //    /**
    //     * @throws ColumnNotPresentException if the column was not present.
    //     */
    //    public byte[] getBlob(@NonNull final String key)
    //            throws ColumnNotPresentException {
    //
    //        final Integer col = columnIndexCache.get(key);
    //        if (col == null) {
    //            throw new ColumnNotPresentException(key);
    //        }
    //        // if (cursor.isNull(col)) {
    //        //     return null;
    //        // }
    //        return cursor.getBlob(col);
    //    }

    // Provide consistence with the other DataHolder methods in this class
    // which will throw.
    @Override
    @NonNull
    public <T extends Parcelable> ArrayList<T> getParcelableArrayList(@NonNull final String key)
            throws ColumnNotPresentException {
        throw new ColumnNotPresentException(key);
    }

    @Override
    @NonNull
    public String toString() {
        @SuppressWarnings("DataFlowIssue")
        final String obj = keySet()
                .stream()
                .map(key -> key + "=" + cursor.getString(columnIndexCache.get(key)))
                .collect(Collectors.joining("; "));

        return "CursorRow{" + obj + "}";
    }

}
