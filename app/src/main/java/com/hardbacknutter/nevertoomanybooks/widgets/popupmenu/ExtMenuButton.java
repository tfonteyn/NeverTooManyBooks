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
package com.hardbacknutter.nevertoomanybooks.widgets.popupmenu;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.core.utils.IntListPref;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public enum ExtMenuButton
        implements Parcelable {

    /**
     * Always show a button through which the user can access the context/row menu.
     */
    Always(0),
    /**
     * As above and below depending on screen space.
     * The decision is mainly based on phone versus tablet.
     */
    IfRoom(1),
    /**
     * Never show a button. The user will need to use long-click instead.
     */
    None(2);

    /** {@link Parcelable}. */
    public static final Creator<ExtMenuButton> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public ExtMenuButton createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public ExtMenuButton[] newArray(final int size) {
            return new ExtMenuButton[size];
        }
    };

    private final int value;

    ExtMenuButton(final int value) {
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
    public static ExtMenuButton getPreferredMode(@NonNull final Context context) {
        final int value = IntListPref.getInt(context, Prefs.pk_booklist_context_menu,
                                             Always.value);
        switch (value) {
            case 2:
                return None;
            case 1:
                return IfRoom;
            case 0:
            default:
                return Always;
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
