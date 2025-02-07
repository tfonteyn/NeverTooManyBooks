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

package com.hardbacknutter.nevertoomanybooks.citations;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.R;

public enum CitationType {
    Default(0),
    BibTeX(1),
    MLA(2),
    RIS(3);

    private final int id;

    CitationType(final int id) {
        this.id = id;
    }

    /**
     * Lookup by id.
     * <p>
     * Import/Export and database usage only.
     *
     * @param id to lookup
     *
     * @return type; or {@link #Default} for any invalid id.
     */
    public static CitationType byId(final int id) {
        return Arrays.stream(values())
                     .filter(type -> type.id == id)
                     .findFirst()
                     .orElse(Default);
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
    public String getLabel(@NonNull final Context context) {
        return context.getResources().getStringArray(R.array.lbl_style_citation_type)[id];
    }
}
