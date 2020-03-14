/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.options;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveImportTask;

public class ImportHelper
        extends Options {

    /**
     * 0: all books
     * 1: only new books and books with more recent update_date fields should be imported.
     */
    public static final int IMPORT_ONLY_NEW_OR_UPDATED = 1 << 16;

    /** {@link Parcelable}. */
    public static final Creator<ImportHelper> CREATOR = new Creator<ImportHelper>() {
        @Override
        public ImportHelper createFromParcel(@NonNull final Parcel source) {
            return new ImportHelper(source);
        }

        @Override
        public ImportHelper[] newArray(final int size) {
            return new ImportHelper[size];
        }
    };
    /** Options value to indicate all things should be exported. */
    public static final int ALL = BOOKS | COVERS | STYLES | PREFERENCES;
    /**
     * all defined flags.
     */
    private static final int MASK = ALL | IMPORT_ONLY_NEW_OR_UPDATED;

    @NonNull
    private final ImportResults mResults;

    /**
     * Constructor.
     *
     * @param options to import
     * @param uri     to read from
     */
    public ImportHelper(final int options,
                        @Nullable final Uri uri) {
        super(options, uri);
        mResults = new ImportResults();
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportHelper(@NonNull final Parcel in) {
        super(in);
        //noinspection ConstantConditions
        mResults = in.readParcelable(getClass().getClassLoader());
    }

    @NonNull
    public ImportResults getResults() {
        return mResults;
    }

    /**
     * Will be called by {@link ArchiveImportTask}.
     */
    public void validate() {
        if ((getOptions() & MASK) == 0) {
            throw new IllegalStateException("options not set");
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(mResults, flags);
    }
    @Override
    @NonNull
    public String toString() {
        return "ImportHelper{"
               + super.toString()
               + ", mResults=" + mResults
               + '}';
    }
}
