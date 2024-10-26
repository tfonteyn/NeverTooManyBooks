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

package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;

/**
 * This structure is used to define the UI PreferenceCategory and the list of
 * Preference keys as used by the group.
 */
public class GroupPrefs {

    @NonNull
    private final String category;
    @NonNull
    private final String[] keys;

    /**
     * Constructor.
     *
     * @param category the name of the PreferenceCategory used by the group
     * @param keys     the Preference keys defined by the group
     */
    GroupPrefs(@NonNull final String category,
               @NonNull final String... keys) {
        this.category = category;
        this.keys = keys;
    }

    /**
     * Get the name of the PreferenceCategory used by the group.
     *
     * @return name
     */
    @NonNull
    public String getCategory() {
        return category;
    }

    /**
     * Get the Preference keys defined by the group.
     *
     * @return keys
     */
    @NonNull
    public String[] getKeys() {
        return keys;
    }
}
