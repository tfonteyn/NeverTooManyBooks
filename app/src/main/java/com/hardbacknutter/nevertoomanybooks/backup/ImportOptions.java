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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ImportOptions
        extends Options {

    /**
     * 0: all books
     * 1: only new books and books with more recent update_date fields should be imported.
     */
    public static final int IMPORT_ONLY_NEW_OR_UPDATED = 1 << 16;

    public static final Creator<ImportOptions> CREATOR = new Creator<ImportOptions>() {
        @Override
        public ImportOptions createFromParcel(@NonNull final Parcel source) {
            return new ImportOptions(source);
        }

        @Override
        public ImportOptions[] newArray(final int size) {
            return new ImportOptions[size];
        }
    };
    /** Options value to indicate all things should be exported. */
    public static final int ALL = BOOK_CSV | COVERS | BOOK_LIST_STYLES | PREFERENCES;
    /**
     * all defined flags.
     */
    static final int MASK = ALL | IMPORT_ONLY_NEW_OR_UPDATED;

    @Nullable
    public Importer.Results results;

    public ImportOptions(final int what) {
        this.what = what;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportOptions(@NonNull final Parcel in) {
        what = in.readInt();
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportOptions{"
               + ", what=0b" + Integer.toBinaryString(what)
               + ", results=" + results
               + '}';
    }
}
