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
import java.util.UUID;

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
     * Constructor to <strong>import from a backup file</strong>.
     *
     * @param uuid          for the style
     * @param styleDefaults to copy the basic settings from (no group info is copied)
     */
    private UserStyle(@NonNull final String uuid,
                      @NonNull final Style styleDefaults) {
        super(uuid, 0);
        copyBasicSettings(styleDefaults);
    }

    /**
     * Copy constructor.
     *
     * @param context Current context
     * @param style   to copy
     *
     * @see #clone(Context)
     */
    protected UserStyle(@NonNull final Context context,
                        @NonNull final Style style) {
        super(UUID.randomUUID().toString(), 0);
        this.name = style.getLabel(context);

        copyBasicSettings(style);
        setGroupList(style.getGroupList());
        copyGroupOptions(style);
    }

    /**
     * Constructor. Load the style data from the database.
     * <p>
     * Dev. note: we just want to us the same semantics as BuiltinStyle needs
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
     * Constructor - create a template specific to import the settings.
     * <ol>
     *     <li>the basic settings will be copied from the given defaults (done here)</li>
     *     <li>the groups are expected to come from the import</li>
     *     <li>the group options must either come from the import,
     *         or during the import set from the defaults.</li>
     * </ol>
     *
     * @param uuid          for the new style
     * @param styleDefaults the defaults to use
     *
     * @return the style without group information
     */
    @NonNull
    public static Style createForImport(@NonNull final String uuid,
                                        @NonNull final Style styleDefaults) {
        return new UserStyle(uuid, styleDefaults);
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
