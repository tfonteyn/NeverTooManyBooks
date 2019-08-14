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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.App;

public class ImportOptions
        implements Parcelable {

    /*
     * options as to *what* should be exported.
     */
    public static final int BOOK_CSV = 1;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int XML_TABLES = 1 << 4;
    //public static final int IMPORT_5 = 1 << 5;
    //public static final int IMPORT_6 = 1 << 6;
    //public static final int IMPORT_7 = 1 << 7;
    // pointless to implement. Just here for mirroring export flags
    //public static final int DATABASE = 1 << 8;

    /** Options value to indicate all things should be exported. */
    public static final int ALL = BOOK_CSV | COVERS | BOOK_LIST_STYLES | PREFERENCES;
    public static final int NOTHING = 0;

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
    /**
     * all defined flags.
     */
    static final int MASK = ALL | IMPORT_ONLY_NEW_OR_UPDATED;
    /**
     * Bitmask.
     */
    public int what;
    /**
     * File to import from.
     */
    @Nullable
    public File file;

    @Nullable
    public Importer.Results results;

    public ImportOptions() {
    }

    public ImportOptions(@NonNull final File file) {
        this.file = file;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ImportOptions(@NonNull final Parcel in) {
        what = in.readInt();
        if (in.readInt() != 0) {
            file = new File(in.readString());
        }
    }

    public SharedPreferences getPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(App.getAppContext());
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(what);

        if (file != null) {
            // has file
            dest.writeInt(1);
            dest.writeString(file.getPath());
        } else {
            // no file
            dest.writeInt(0);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportOptions{"
               + "file=`" + file + '`'
               + ", what=0%" + Integer.toBinaryString(what)
               + ", results=" + results
               + '}';
    }

    public void validate() {
    }
}
