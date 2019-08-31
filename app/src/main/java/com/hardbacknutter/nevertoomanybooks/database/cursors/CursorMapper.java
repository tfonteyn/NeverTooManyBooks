/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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

import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

/**
 * A handy wrapper allowing to fetch columns by name.
 * <p>
 * <strong>Note:</strong> converts {@code null} Strings to an empty String.
 * <p>
 * Tip: when using a CursorMapper as a parameter to a constructor, e.g.
 * {@link com.hardbacknutter.nevertoomanybooks.entities.Bookshelf#Bookshelf(long, CursorMapper)}
 * always pass the id additionally/separately. This gives the calling code a change to use
 * for example the foreign key id.
 */
public class CursorMapper {

    /** the mapped cursor. */
    private final Cursor mCursor;

    /**
     * Constructor.
     * <p>
     * Cache all column indexes for this cursors column.
     *
     * @param cursor to read from
     */
    public CursorMapper(@NonNull final Cursor cursor) {
        mCursor = cursor;
    }

    /**
     * @param domainName the domain to get
     *
     * @return {@code true} if this mapper contains the specified domain.
     */
    public boolean contains(@NonNull final String domainName) {
        return mCursor.getColumnIndex(domainName) > -1;
    }

    /**
     * Direct access to the cursor.
     *
     * @param columnIndex to get
     *
     * @return a string from underlying cursor
     */
    @Nullable
    public String getString(final int columnIndex) {
        return mCursor.getString(columnIndex);
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

        int col = mCursor.getColumnIndex(domainName);
        if (col == -1) {
            throw new ColumnNotPresentException(domainName);
        }
        if (mCursor.isNull(col)) {
            return "";
        }
        return mCursor.getString(col);
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

        int col = mCursor.getColumnIndex(domainName);
        if (col == -1) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(col)) {
//            return null; // 0
//        }
        return mCursor.getInt(col);
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

        int col = mCursor.getColumnIndex(domainName);
        if (col == -1) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(col)) {
//            return null; // 0
//        }
        return mCursor.getLong(col);
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

        int col = mCursor.getColumnIndex(domainName);
        if (col == -1) {
            throw new ColumnNotPresentException(domainName);
        }
//        if (mCursor.isNull(col)) {
//            return null; // 0
//        }
        return mCursor.getDouble(col);
    }

    /**
     *
     * See the comments on methods in {@link android.database.CursorWindow}
     * for info on type conversions which explains our use of getLong/getDouble.
     *
     * @return a bundle with all the columns present ({@code null} values are dropped).
     */
    @SuppressWarnings("unused")
    @NonNull
    public Bundle getAll() {
        Bundle bundle = new Bundle();

        for (String columnName : mCursor.getColumnNames()) {
            int col = mCursor.getColumnIndex(columnName);
            if (col != -1) {
                switch (mCursor.getType(col)) {
                    case Cursor.FIELD_TYPE_STRING:
                        bundle.putString(columnName, mCursor.getString(col));
                        break;

                    case Cursor.FIELD_TYPE_INTEGER:
                        // mCursor.getInt is really a (int)mCursor.getLong
                        bundle.putLong(columnName, mCursor.getLong(col));
                        break;

                    case Cursor.FIELD_TYPE_FLOAT:
                        // mCursor.getFloat is really a (float)mCursor.getDouble
                        bundle.putDouble(columnName, mCursor.getDouble(col));
                        break;

                    case Cursor.FIELD_TYPE_BLOB:
                        bundle.putByteArray(columnName, mCursor.getBlob(col));
                        break;

                    case Cursor.FIELD_TYPE_NULL:
                        // field value null; skip
                        break;

                    default:
                        Logger.warnWithStackTrace(this, "columnName=" + columnName,
                                                  "type=" + mCursor.getType(col));
                        break;
                }
            }
        }

        return bundle;
    }
}
