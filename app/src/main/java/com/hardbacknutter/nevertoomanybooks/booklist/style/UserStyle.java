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
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Except for the clone constructor (which is 'protected') all constructors are private.
 * Use the factory methods instead for clarity.
 */
public class UserStyle
        extends BaseStyle
        implements WritableStyle {

    @Nullable
    private String name;

    /**
     * Constructor to <strong>load from the database</strong>.
     *
     * @param rowData with data
     */
    private UserStyle(@NonNull final DataHolder rowData) {
        super(rowData);
        name = rowData.getString(DBKey.STYLE_NAME);
    }

    /**
     * Copy constructor.
     *
     * @param uuid for the new style
     * @param id   for the new style
     */
    protected UserStyle(@NonNull final String uuid,
                        final long id) {
        super(uuid, id);
    }

    /**
     * Constructor. Load the style data from the database.
     *
     * @param rowData data
     *
     * @return the loaded Style
     */
    @NonNull
    public static Style createFromDatabase(@NonNull final DataHolder rowData) {
        return new UserStyle(rowData);
    }

    /**
     * Constructor - create a template style inheriting from {@link GlobalStyle}.
     * GROUP OPTIONS ARE NOT COPIED FROM THE GLOBAL-STYLE!
     *
     * @param uuid for the new style
     * @param styleDefaults the defaults to use
     *
     * @return the loaded Style
     */
    @NonNull
    public static Style createFromImport(@NonNull final String uuid,
                                         @NonNull final Style styleDefaults) {
        final UserStyle userStyle = new UserStyle(uuid, 0);
        userStyle.copyNonGroupSettings(styleDefaults);
        return userStyle;
    }

    @Override
    @NonNull
    public StyleType getType() {
        return StyleType.User;
    }

    /**
     * Get the user-displayable name for this style.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return Objects.requireNonNull(name, "name");
    }

    /**
     * Set the user-displayable name for this style.
     *
     * @param name for this style
     */
    public void setName(@NonNull final String name) {
        this.name = name;
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getName();
    }
}
