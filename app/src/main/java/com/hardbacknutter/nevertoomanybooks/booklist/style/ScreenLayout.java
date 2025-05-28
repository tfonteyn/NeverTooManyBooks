/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.NonNull;

// URGENT: COMBINE THIS WITH FieldVisibility.Screen ??
public enum ScreenLayout {
    /** BoB: one book/row; show full details. */
    List(0),
    /** BoB: grid shows either the cover, of the title+author. */
    Grid(1);

    private final int id;

    ScreenLayout(final int id) {
        this.id = id;
    }

    /**
     * Lookup by id.
     * <p>
     * Import/Export and database usage only.
     *
     * @param id to lookup
     *
     * @return type; or {@link #List} for any invalid id.
     */
    @NonNull
    public static ScreenLayout byId(final int id) {
        if (id == 1) {
            return Grid;
        }
        return List;
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
}
