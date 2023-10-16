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

import java.util.UUID;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

@SuppressWarnings("WeakerAccess")
public final class GlobalStyle
        extends BaseStyle {

    /**
     * Constructor used during app installation/upgrade.
     */
    public GlobalStyle() {
        super(UUID.randomUUID().toString(), Integer.MIN_VALUE);
    }

    /**
     * Constructor to <strong>load from database</strong>.
     *
     * @param rowData with data
     */
    private GlobalStyle(@NonNull final DataHolder rowData) {
        super(rowData);
    }

    /**
     * Constructor - load the style from the database.
     *
     * @param rowData data
     *
     * @return the loaded Style
     */
    @NonNull
    public static Style createFromDatabase(@NonNull final DataHolder rowData) {
        return new GlobalStyle(rowData);
    }

    @NonNull
    @Override
    public StyleType getType() {
        return StyleType.Global;
    }

    @NonNull
    @Override
    public String getLabel(@NonNull final Context context) {
        return context.getString(R.string.style_type_defaults);
    }
}
