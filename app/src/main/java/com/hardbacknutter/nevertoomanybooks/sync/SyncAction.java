/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;

public enum SyncAction
        implements Parcelable {
    Skip(0, R.string.action_skip),
    CopyIfBlank(1, R.string.option_field_usage_copy_if_blank),
    Append(2, R.string.option_field_usage_append),
    Overwrite(3, R.string.option_field_usage_overwrite);

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

    private final int value;
    @StringRes
    private final int labelResId;

    SyncAction(final int value,
               @StringRes final int labelResId) {
        this.value = value;
        this.labelResId = labelResId;
    }

    @NonNull
    public static SyncAction read(@NonNull final SharedPreferences prefs,
                                  @NonNull final String key,
                                  @NonNull final SyncAction defAction) {
        switch (prefs.getInt(key, defAction.value)) {
            case 3:
                return Overwrite;
            case 2:
                return Append;
            case 1:
                return CopyIfBlank;
            case 0:
            default:
                return Skip;
        }
    }

    public void write(@NonNull final SharedPreferences.Editor ed,
                      @NonNull final String key) {
        ed.putInt(key, value);
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
     * Return the user readable label id.
     *
     * @return string id
     */
    @StringRes
    int getLabelResId() {
        return labelResId;
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
