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

import java.util.Date;
import java.util.Objects;

public class ExportOptions
        extends Options {

    /**
     * Options to indicate new books or books with more recent update_date
     * fields should be exported.
     * <p>
     * 0: all books
     * 1: books added/updated since {@link #dateFrom}.
     * If the latter is {@code null}, then since last backup.
     */
    public static final int EXPORT_SINCE = 1 << 16;

    /**
     * all defined flags.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int MASK = BOOK_CSV
                                   | PREFERENCES
                                   | BOOK_LIST_STYLES
                                   | COVERS
                                   | XML_TABLES
                                   | EXPORT_SINCE;

    public static final Creator<ExportOptions> CREATOR = new Creator<ExportOptions>() {
        @Override
        public ExportOptions createFromParcel(@NonNull final Parcel source) {
            return new ExportOptions(source);
        }

        @Override
        public ExportOptions[] newArray(final int size) {
            return new ExportOptions[size];
        }
    };
    /**
     * Options value to indicate all things should be exported.
     * Note that XML_TABLES is NOT included as it's considered special interest.
     */
    public static final int ALL = BOOK_CSV
                                  | PREFERENCES
                                  | BOOK_LIST_STYLES
                                  | COVERS;
    /** EXPORT_SINCE. */
    @Nullable
    public Date dateFrom;

    /**
     * Constructor.
     *
     * @param what to export
     */
    public ExportOptions(final int what) {
        this.what = what;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportOptions(@NonNull final Parcel in) {
        what = in.readInt();
        // has date?
        if (in.readInt() != 0) {
            dateFrom = new Date(in.readLong());
        }
    }


    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        super.writeToParcel(dest, flags);

        if (dateFrom != null) {
            // has date
            dest.writeInt(1);
            dest.writeLong(dateFrom.getTime());
        } else {
            // no date
            dest.writeInt(0);
        }
    }

    public void validate() {
        // if we want 'since', we *must* have a valid dateFrom
        if ((what & ExportOptions.EXPORT_SINCE) != 0) {
            Objects.requireNonNull(dateFrom, "Export Failed - 'dateFrom' is null");
        } else {
            // sanity check: we don't want 'since', so make sure fromDate is not set.
            dateFrom = null;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportOptions{"
               + ", what=0b" + Integer.toBinaryString(what)
               + ", dateFrom=" + dateFrom
               + '}';
    }
}
