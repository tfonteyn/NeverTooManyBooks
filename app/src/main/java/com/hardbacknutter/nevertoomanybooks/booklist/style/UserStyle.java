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

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
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
     * Constructor for <strong>importing</strong> styles.
     *
     * @param uuid UUID of the style
     *
     * @see StyleCoder
     */
    private UserStyle(@NonNull final String uuid) {
        super(requireUuid(uuid), 0,
              ServiceLocator.getInstance().getStyles().getGlobalStyle());
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param rowData with data
     */
    private UserStyle(@NonNull final DataHolder rowData) {
        super(rowData);
        name = rowData.getString(DBKey.STYLE_NAME);
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in as they need to override
     * the originals values, see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param id      for the new style
     * @param uuid    for the new style
     * @param style   to clone
     */
    protected UserStyle(@NonNull final Context context,
                        final long id,
                        @NonNull final String uuid,
                        @NonNull final Style style) {
        super(requireUuid(uuid), id, style);
        name = style.getLabel(context);
    }

    /**
     * Constructor - load a style from the database.
     *
     * @param rowData data
     *
     * @return the loaded UserStyle
     */
    @NonNull
    public static Style createFromDatabase(@NonNull final DataHolder rowData) {
        return new UserStyle(rowData);
    }

    /**
     * Constructor - load a style from an import (backup file).
     *
     * @param uuid data
     *
     * @return the loaded UserStyle
     */
    @NonNull
    public static Style createFromImport(@NonNull final String uuid) {
        return new UserStyle(uuid);
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
