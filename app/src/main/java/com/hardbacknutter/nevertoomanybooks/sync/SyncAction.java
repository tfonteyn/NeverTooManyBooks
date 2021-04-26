/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;

public enum SyncAction {
    // NEVER change the order, we store the ordinal in SharedPreferences.
    Skip(R.string.action_skip),
    CopyIfBlank(R.string.lbl_field_usage_copy_if_blank),
    Append(R.string.lbl_field_usage_append),
    Overwrite(R.string.lbl_field_usage_overwrite);

    private final int mLabelId;

    SyncAction(@StringRes final int labelId) {
        mLabelId = labelId;
    }

    public static SyncAction read(@NonNull final SharedPreferences global,
                                  @NonNull final String key,
                                  @NonNull final SyncAction defValue) {
        final int ordinal = global.getInt(key, -1);
        if (ordinal == -1) {
            return defValue;
        } else {
            return values()[ordinal];
        }
    }

    public void write(@NonNull final SharedPreferences.Editor ed,
                      @NonNull final String key) {
        ed.putInt(key, ordinal());
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
    int getLabelId() {
        return mLabelId;
    }
}
