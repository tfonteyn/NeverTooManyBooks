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
import androidx.preference.PreferenceManager;

/**
 * Setting which are configurable on "preferences.xml".
 * These are used where a Style is not needed/present
 * and as defaults for styles in general. User defined styles can override these settings.
 */
@SuppressWarnings("WeakerAccess")
public final class GlobalStyle {

    private GlobalStyle() {
    }

    /**
     * Whether the user prefers the Author names displayed by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} if the Given name should be displayed before the Family name
     */
    public static boolean isShowAuthorByGivenName(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(StyleDataStore.PK_SHOW_AUTHOR_NAME_GIVEN_FIRST, false);
    }

    /**
     * Whether the user prefers the Author names sorted by Given names, or by Family name first.
     *
     * @param context Current context
     *
     * @return {@code true} if the Given name should be sorted before then the Family name
     */
    public static boolean isSortAuthorByGivenName(@NonNull final Context context) {
        return PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(StyleDataStore.PK_SORT_AUTHOR_NAME_GIVEN_FIRST, false);
    }
}
