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
package com.hardbacknutter.nevertoomanybooks.io;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Optional;

public class BasicMetaData {

    static final String INFO_NUMBER_OF_BOOKS = "NumBooks";
    static final String INFO_NUMBER_OF_COVERS = "NumCovers";

    /** Bundle retrieved from the archive for this instance. */
    @NonNull
    private final Bundle data;

    /**
     * Constructor.
     *
     * @param data The bundle with the information previously read.
     */
    public BasicMetaData(@NonNull final Bundle data) {
        this.data = data;
    }

    /**
     * Get the raw bundle for reading/writing.
     *
     * @return the bundle with all data.
     */
    @NonNull
    public Bundle getData() {
        return data;
    }

    /**
     * Get the number of books.
     *
     * @return the number of books if known
     */
    @NonNull
    public Optional<Integer> getBookCount() {
        if (data.containsKey(INFO_NUMBER_OF_BOOKS)) {
            final int count = data.getInt(INFO_NUMBER_OF_BOOKS);
            if (count > 0) {
                return Optional.of(count);
            }
        }
        return Optional.empty();
    }

    /**
     * Set the number of books.
     *
     * @param count to set
     */
    public void setBookCount(final int count) {
        data.putInt(INFO_NUMBER_OF_BOOKS, count);
    }

    /**
     * Get the number of covers.
     *
     * @return the number of covers if known
     */
    @NonNull
    public Optional<Integer> getCoverCount() {
        if (data.containsKey(INFO_NUMBER_OF_COVERS)) {
            final int count = data.getInt(INFO_NUMBER_OF_COVERS);
            if (count > 0) {
                return Optional.of(count);
            }
        }
        return Optional.empty();
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncReaderMetaData{"
               + "data=" + data
               + '}';
    }
}
