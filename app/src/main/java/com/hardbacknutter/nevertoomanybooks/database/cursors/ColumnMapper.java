/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
package com.hardbacknutter.nevertoomanybooks.database.cursors;

import android.database.Cursor;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * Given a Cursor, this class constructs a map with the column indexes as used with that Cursor.
 * This is a caching variation of the {@link Cursor#getColumnIndex(String)} method.
 * The latter does a search for the column each time.
 * <p>
 * Avoids the need to repeat writing code to get column indexes for named columns.
 * <p>
 * If a given domain is not present in the Cursor, a {@link ColumnNotPresentException}
 * will be thrown at the time of fetching the value.
 * <p>
 * Tip: when using a ColumnMapper as a parameter to a constructor, e.g.
 * {@link com.hardbacknutter.nevertoomanybooks.entities.Bookshelf#Bookshelf(long, ColumnMapper)}
 * always pass the id additionally/separately. This gives the calling code a change to use
 * for example the foreign key id.
 */
public class ColumnMapper {

    /** the cache with the column ID's. */
    private final Map<String, Integer> mColumnIndexes = new HashMap<>();
    /** the mapped cursor. */
    private final Cursor mCursor;

    /**
     * Constructor.
     * <p>
     * Cache all column indexes for this cursors column.
     *
     * @param cursor to read from
     */
    public ColumnMapper(@NonNull final Cursor cursor)
            throws IllegalArgumentException {
        mCursor = cursor;

        for (String columnName : mCursor.getColumnNames()) {
            mColumnIndexes.put(columnName, mCursor.getColumnIndex(columnName));
        }
    }

    /**
     * @param domainName the domain to get
     *
     * @return {@code true} if this mapper contains the specified domain.
     */
    public boolean contains(@NonNull final String domainName) {
        return mColumnIndexes.containsKey(domainName);
    }

    /**
     * @param domainName the name of the domain to get
     *
     * @return the string value of the column.
     * A {@code null} value will be returned as an empty String.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @NonNull
    public String getString(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
        if (mCursor.isNull(index)) {
            return "";
        }
        return mCursor.getString(index);
    }

    /**
     * @param domainName to get
     *
     * @return the boolean value of the column ({@code null} comes back as false).
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public boolean getBoolean(@NonNull final String domainName)
            throws ColumnNotPresentException {
        return getInt(domainName) == 1;
    }

    /**
     * @param domainName to get
     *
     * @return the int value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public int getInt(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getInt(index);
    }

    /**
     * @param domainName to get
     *
     * @return the long value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public long getLong(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getLong(index);
    }

    /**
     * @param domainName to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public double getDouble(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(index)) {
//            return null; // 0
//        }
        return mCursor.getDouble(index);
    }

    /**
     * @param domainName to get
     *
     * @return the byte[] value of the column.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @SuppressWarnings("unused")
    @Nullable
    public byte[] getBlob(@NonNull final String domainName)
            throws ColumnNotPresentException {

        Integer index = mColumnIndexes.get(domainName);
        if ((index == null) || (index == -1)) {
            throw new ColumnNotPresentException(domainName);
        }
        return mCursor.getBlob(index);
    }

    /**
     * @return a bundle with all the columns present ({@code null} values are dropped).
     */
    @SuppressWarnings("unused")
    @NonNull
    public Bundle getAll() {
        Bundle bundle = new Bundle();

        for (Map.Entry<String, Integer> col : mColumnIndexes.entrySet()) {
            if (!col.getValue().equals(-1)) {
                switch (mCursor.getType(col.getValue())) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        bundle.putInt(col.getKey(), mCursor.getInt(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_STRING:
                        bundle.putString(col.getKey(), mCursor.getString(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_FLOAT:
                        bundle.putFloat(col.getKey(), mCursor.getFloat(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_BLOB:
                        bundle.putByteArray(col.getKey(), mCursor.getBlob(col.getValue()));
                        break;

                    case Cursor.FIELD_TYPE_NULL:
                        // field value null; skip
                        break;

                    default:
                        Logger.warnWithStackTrace(this, "Unknown type", "key=" + col.getKey());
                        break;
                }
            }
        }

        return bundle;
    }
}
