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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public enum RebuildBooklist
        implements Parcelable {

    FromSaved(0),
    Expanded(1),
    Collapsed(2),
    Preferred(3);

    /** {@link Parcelable}. */
    public static final Creator<RebuildBooklist> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public RebuildBooklist createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public RebuildBooklist[] newArray(final int size) {
            return new RebuildBooklist[size];
        }
    };

    private final int value;

    RebuildBooklist(final int value) {
        this.value = value;
    }

    /**
     * Get the current preferred rebuild mode for the list.
     *
     * @param context Current context
     *
     * @return Mode
     */
    @NonNull
    public static RebuildBooklist getPreferredMode(@NonNull final Context context) {
        final int value = IntListPref.getInt(context, Prefs.pk_booklist_rebuild_state,
                                             FromSaved.value);
        switch (value) {
            case 3:
                return Preferred;
            case 2:
                return Collapsed;
            case 1:
                return Expanded;
            case 0:
            default:
                return FromSaved;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(this.ordinal());
    }
}
