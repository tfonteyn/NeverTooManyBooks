/*
 * @Copyright 2018-2024 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.search;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public enum Scanning
        implements Parcelable {

    /** Scanner is (set) offline. */
    Off(0),
    /** Scan, search for, and edit the found book. */
    Manual(1),
    /**
     * Scan, search for, and edit the found book.
     * When editing is finished, start a new scan.
     */
    Continuous(2),
    /**
     * Scan and queue the code, until scanning is cancelled.
     * The user can then edit books from the isbn-queue.
     */
    Batch(3);

    /** {@link Parcelable}. */
    public static final Creator<Scanning> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public Scanning createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public Scanning[] newArray(final int size) {
            return new Scanning[size];
        }
    };
    private final int value;

    Scanning(final int value) {
        this.value = value;
    }

    /**
     * Get the user preferred single-scan mode: either {@link #Manual} or {@link #Continuous}.
     *
     * @param context Current context
     *
     * @return scan mode
     */
    @NonNull
    public static Scanning getScannerModeSingle(@NonNull final Context context) {
        final int value = IntListPref.getInt(context, Prefs.PK_SCANNER_MODE_SINGLE,
                                             Manual.value);
        if (value == Continuous.value) {
            return Continuous;
        } else {
            return Manual;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(ordinal());
    }
}
