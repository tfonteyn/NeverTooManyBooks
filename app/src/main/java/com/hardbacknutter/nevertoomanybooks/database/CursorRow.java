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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * A handy wrapper allowing to fetch columns by name.
 * <p>
 * <strong>Note:</strong> converts {@code null} Strings to an empty String.
 * <p>
 * Tip: when using a CursorRow as a parameter to a constructor, e.g.
 * {@link com.hardbacknutter.nevertoomanybooks.entities.Bookshelf#Bookshelf(long, DataHolder)}
 * always pass the id additionally/separately. This gives the calling code a change to use
 * for example the foreign key id.
 */
public class CursorRow
        implements DataHolder {

    /** Log tag. */
    private static final String TAG = "CursorRow";

    /** the mapped cursor. */
    private final Cursor mCursor;

    /**
     * Constructor.
     *
     * @param cursor to read from
     */
    public CursorRow(@NonNull final Cursor cursor) {
        mCursor = cursor;
    }

    @NonNull
    @Override
    public Set<String> keySet() {
        return new HashSet<>(Arrays.asList(mCursor.getColumnNames()));
    }

    /**
     * @param key the domain to get
     *
     * @return {@code true} if this cursor contains the specified domain.
     */
    public boolean contains(@NonNull final String key) {
        return mCursor.getColumnIndex(key) > -1;
    }

    /**
     * @param key the name of the domain to get
     *
     * @return the string value of the column.
     * A {@code null} value will be returned as an empty String.
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @NonNull
    public String getString(@NonNull final String key)
            throws ColumnNotPresentException {

        int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        if (mCursor.isNull(col)) {
            return "";
        }
        return mCursor.getString(col);
    }

    /**
     * @param key to get
     *
     * @return the boolean value of the column ({@code null} comes back as false).
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public boolean getBoolean(@NonNull final String key)
            throws ColumnNotPresentException {
        return getInt(key) == 1;
    }

    /**
     * @param key to get
     *
     * @return the int value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public int getInt(@NonNull final String key)
            throws ColumnNotPresentException {

        int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return null; // 0
        // }
        return mCursor.getInt(col);
    }

    /**
     * @param key to get
     *
     * @return the long value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public long getLong(@NonNull final String key)
            throws ColumnNotPresentException {

        int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return null; // 0
        // }
        return mCursor.getLong(col);
    }

    /**
     * @param key to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public double getDouble(@NonNull final String key)
            throws ColumnNotPresentException {

        int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return null; // 0
        // }
        return mCursor.getDouble(col);
    }

    /**
     * @param key to get
     *
     * @return the byte array (blob) of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    public byte[] getBlob(@NonNull final String key)
            throws ColumnNotPresentException {

        int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return null;
        // }
        return mCursor.getBlob(col);
    }

    /**
     * See the comments on methods in {@link android.database.CursorWindow}
     * for info on type conversions which explains our use of getLong/getDouble.
     *
     * @param context Current context
     *
     * @return a bundle with all the columns present ({@code null} values are dropped).
     */
    @SuppressWarnings("unused")
    @NonNull
    public Bundle getAll(@NonNull final Context context) {
        final Bundle result = new Bundle();

        for (String columnName : mCursor.getColumnNames()) {
            int col = mCursor.getColumnIndex(columnName);
            if (col != -1) {
                switch (mCursor.getType(col)) {
                    case Cursor.FIELD_TYPE_STRING:
                        result.putString(columnName, mCursor.getString(col));
                        break;

                    case Cursor.FIELD_TYPE_INTEGER:
                        // mCursor.getInt is really a (int)mCursor.getLong
                        result.putLong(columnName, mCursor.getLong(col));
                        break;

                    case Cursor.FIELD_TYPE_FLOAT:
                        // mCursor.getFloat is really a (float)mCursor.getDouble
                        result.putDouble(columnName, mCursor.getDouble(col));
                        break;

                    case Cursor.FIELD_TYPE_BLOB:
                        result.putByteArray(columnName, mCursor.getBlob(col));
                        break;

                    case Cursor.FIELD_TYPE_NULL:
                        // field value null; skip
                        break;

                    default:
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "columnName=" + columnName
                                       + "|type=" + mCursor.getType(col));
                        }
                        break;
                }
            }
        }

        return result;
    }
}
