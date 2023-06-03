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
package com.hardbacknutter.nevertoomanybooks.booklist.style;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;

// Direct usage of this class BYPASSES the style settings!
// Normal usage is to ask the style, which can fallback to the global setting automatically.
//
public final class GlobalFieldVisibility
        extends FieldVisibility {


    private GlobalFieldVisibility() {
    }


    /**
     * Is the field in use; i.e. is it enabled in the user-preferences.
     *
     * @param context Current context
     * @param dbdKey  {@link DBKey}.KEY_x to lookup
     *
     * @return {@code true} if the user wants to use this field.
     */
    public static boolean isUsed(@NonNull final Context context,
                                 @NonNull final String dbdKey) {
        return ServiceLocator.getInstance().getFieldVisibility()
                             .isShowField(dbdKey)
                             .orElse(false);
    }
}
