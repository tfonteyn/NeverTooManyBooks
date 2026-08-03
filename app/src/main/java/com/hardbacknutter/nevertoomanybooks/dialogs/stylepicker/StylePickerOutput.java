/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.stylepicker;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.dialogs.LauncherOutput;

public class StylePickerOutput
        implements LauncherOutput {

    @NonNull
    private final String styleUuid;

    /**
     * Constructor.
     *
     * @param styleUuid the selected style
     */
    StylePickerOutput(@NonNull final String styleUuid) {
        this.styleUuid = styleUuid;
    }

    @Nullable
    static String fromBundle(@NonNull final Bundle args) {
        return args.getString(DBKey.FK_STYLE);
    }

    @NonNull
    @Override
    public Bundle toBundle() {
        final Bundle args = new Bundle(1);
        args.putString(DBKey.FK_STYLE, styleUuid);
        return args;
    }

    @Override
    @NonNull
    public String toString() {
        return "StylePickerOutput{"
               + "styleUuid='" + styleUuid + '\''
               + '}';
    }
}
