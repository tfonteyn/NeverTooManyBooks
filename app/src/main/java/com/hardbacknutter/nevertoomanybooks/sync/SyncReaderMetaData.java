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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Bundle;

import androidx.annotation.NonNull;

// overkill for now... we could just use a raw Bundle. But let's keep it future-extendable.
public class SyncReaderMetaData {

    private static final String INFO_NUMBER_OF_BOOKS = "NumBooks";
    private static final String INFO_NUMBER_OF_COVERS = "NumCovers";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mData;

    /**
     * Constructor.
     */
    public SyncReaderMetaData(@NonNull final Bundle args) {
        mData = args;
    }

    /**
     * Get all available meta data.
     *
     * @return the bundle with all data.
     */
    @NonNull
    public Bundle getData() {
        return mData;
    }

    /**
     * Check if the archive has a known number of books.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any books though.
     *
     * @return {@code true} if the number is <strong>known</strong>
     */
    public boolean hasBookCount() {
        return mData.containsKey(INFO_NUMBER_OF_BOOKS)
               && mData.getInt(INFO_NUMBER_OF_BOOKS) > 0;
    }

    public int getBookCount() {
        return mData.getInt(INFO_NUMBER_OF_BOOKS);
    }

    public void setBookCount(final int count) {
        mData.putInt(INFO_NUMBER_OF_BOOKS, count);
    }

    /**
     * Check if the archive has a known number of covers.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any covers though.
     *
     * @return {@code true} if the number is <strong>known</strong>
     */
    public boolean hasCoverCount() {
        return mData.containsKey(INFO_NUMBER_OF_COVERS)
               && mData.getInt(INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mData.getInt(INFO_NUMBER_OF_COVERS);
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncReaderMetaData{"
               + "mData=" + mData
               + '}';
    }
}
