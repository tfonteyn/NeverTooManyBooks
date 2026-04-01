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

package com.hardbacknutter.nevertoomanybooks.booklist.grouping;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * This structure is used to define the UI PreferenceCategory and the list of
 * Preference keys as used by the group.
 */
public class GroupSettings {

    @NonNull
    private final String headerKey;
    @NonNull
    private final List<String> keys;

    /**
     * Constructor.
     *
     * @param headerKey the Preference header key used by this group.
     * @param keys      the Preference keys defined by the group
     */
    GroupSettings(@NonNull final String headerKey,
                  @NonNull final String... keys) {
        this.headerKey = headerKey;
        this.keys = Arrays.asList(keys);
    }

    /**
     * Get the Preference header key used by this group.
     *
     * @return name
     */
    @NonNull
    public String getHeaderKey() {
        return headerKey;
    }

    /**
     * Get the Preference keys defined by the group.
     *
     * @return keys
     */
    @NonNull
    public List<String> getKeys() {
        return keys;
    }
}
