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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;

public enum SyncAction
        implements Parcelable {
    /** Ignore/skip the field. */
    Skip(0),
    /** Update the field only if the current value is blank (null/empty). */
    CopyIfBlank(1),
    /** List fields (incl. text fields): append any new data. */
    Append(2),
    /** Force (over)write the field with the new data. */
    Overwrite(3);

    /** {@link Parcelable}. */
    public static final Creator<SyncAction> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SyncAction createFromParcel(@NonNull final Parcel in) {
            return values()[in.readInt()];
        }

        @Override
        @NonNull
        public SyncAction[] newArray(final int size) {
            return new SyncAction[size];
        }
    };

    private final int id;

    SyncAction(final int id) {
        this.id = id;
    }

    /**
     * Lookup by id.
     * <p>
     * Import/Export and database usage only.
     * <p>
     * Returns {@link #Skip} for any invalid id.
     *
     * @param id to lookup
     *
     * @return type
     */
    @NonNull
    public static SyncAction byId(final int id) {
        return Arrays.stream(values()).filter(v -> v.id == id).findFirst().orElse(Skip);
    }

    @NonNull
    SyncAction nextState(final boolean allowAppend) {
        switch (this) {
            case Skip:
                return CopyIfBlank;

            case CopyIfBlank:
                if (allowAppend) {
                    return Append;
                } else {
                    return Overwrite;
                }

            case Append:
                return Overwrite;

            case Overwrite:
                return Skip;
        }
        return Skip;
    }

    /**
     * Get the internal id.
     * <p>
     * Import/Export and database usage only.
     *
     * @return id
     */
    public int getId() {
        return id;
    }

    /**
     * Get a short description of this type.
     *
     * @param context Current context
     *
     * @return the label
     */
    @NonNull
    String getLabel(@NonNull final Context context) {
        return context.getResources().getStringArray(R.array.lbl_sync_action)[id];
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
