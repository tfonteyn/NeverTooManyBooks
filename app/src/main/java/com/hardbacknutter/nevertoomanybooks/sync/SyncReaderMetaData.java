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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class SyncReaderMetaData {

    private static final String INFO_NUMBER_OF_BOOKS = "NumBooks";
    private static final String INFO_NUMBER_OF_COVERS = "NumCovers";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle mInfo;

    /**
     * Constructor used while reading from an Archive.
     * <p>
     * The bundle is passed in to allow an Archive reader to construct it
     * at runtime + to allow testing.
     */
    public SyncReaderMetaData(@NonNull final Bundle from) {
        mInfo = from;
    }

    /**
     * Constructor used while writing to an Archive.
     *
     * @param context Current context
     * @param version of the archive structure
     * @param data    to add to the header bundle
     */
    @NonNull
    public static SyncReaderMetaData create(@NonNull final Context context,
                                            final int version,
                                            @NonNull final SyncWriterResults data) {
        final Bundle bundle = new Bundle();
        if (data.getBookCount() > 0) {
            bundle.putInt(INFO_NUMBER_OF_BOOKS, data.getBookCount());
        }
        if (data.getCoverCount() > 0) {
            bundle.putInt(INFO_NUMBER_OF_COVERS, data.getCoverCount());
        }
        return new SyncReaderMetaData(bundle);
    }

    /**
     * Get the raw bundle, this is used for reading out the info block to the backup archive.
     *
     * @return the bundle with all settings.
     */
    @NonNull
    public Bundle getBundle() {
        return mInfo;
    }

    /**
     * Check if the archive has a known number of books.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any books though.
     *
     * @return {@code true} if the number of books is <strong>known</strong>
     */
    public boolean hasBookCount() {
        return mInfo.containsKey(INFO_NUMBER_OF_BOOKS)
               && mInfo.getInt(INFO_NUMBER_OF_BOOKS) > 0;
    }

    public int getBookCount() {
        return mInfo.getInt(INFO_NUMBER_OF_BOOKS);
    }

    public void setBookCount(final int count) {
        mInfo.putInt(INFO_NUMBER_OF_BOOKS, count);
    }

    /**
     * Check if the archive has a known number of covers.
     * Will return {@code false} if there is no number (or if the number is 0).
     * This does not mean there might not be any covers though.
     *
     * @return {@code true} if the number of books is <strong>known</strong>
     */
    public boolean hasCoverCount() {
        return mInfo.containsKey(INFO_NUMBER_OF_COVERS)
               && mInfo.getInt(INFO_NUMBER_OF_COVERS) > 0;
    }

    public int getCoverCount() {
        return mInfo.getInt(INFO_NUMBER_OF_COVERS);
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncReaderMetaData{"
               + "mInfo=" + mInfo
               + '}';
    }
}
