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

package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;

public enum StyleType {
    /**
     * User defined.
     */
    User(0, R.string.style_type_user_defined),
    /**
     * A predefined style.
     */
    Builtin(1, R.string.style_type_builtin),
    /**
     * The global style, i.e. the defaults.
     */
    Global(2, R.string.lbl_defaults);

    private final int id;
    @StringRes
    private final int typeResId;

    StyleType(final int id,
              @StringRes final int typeResId) {
        this.id = id;
        this.typeResId = typeResId;
    }

    @NonNull
    public static StyleType byId(final int id) {
        switch (id) {
            case 0:
                return User;
            case 1:
                return Builtin;
            case 2:
                return Global;
            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }
    }

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
    public String getLabel(@NonNull final Context context) {
        return context.getString(typeResId);
    }
}
