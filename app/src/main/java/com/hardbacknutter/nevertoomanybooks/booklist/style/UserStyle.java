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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.json.coders.StyleCoder;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * Except for the clone constructor (which is 'protected') all constructors are private.
 * Use the factory methods instead for clarity.
 */
public class UserStyle
        extends BaseStyle {

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
        super(uuid, 0);
    }

    /**
     * Constructor for styles <strong>loaded from database</strong>.
     *
     * @param rowData with data
     */
    private UserStyle(@NonNull final DataHolder rowData) {
        super(rowData.getString(DBKey.STYLE_UUID), rowData.getLong(DBKey.PK_ID));

        name = rowData.getString(DBKey.STYLE_NAME);
        setPreferred(rowData.getBoolean(DBKey.STYLE_IS_PREFERRED));
        setMenuPosition(rowData.getInt(DBKey.STYLE_MENU_POSITION));

        // set the groups first !
        List<Integer> groupIds;
        try {
            groupIds = Arrays.stream(rowData.getString(DBKey.STYLE_GROUPS).split(","))
                             .map(Integer::parseInt)
                             .collect(Collectors.toList());
        } catch (@NonNull final NumberFormatException ignore) {
            // we should never get here... flw... try to recover.
            groupIds = List.of(BooklistGroup.AUTHOR);
        }
        setGroupIds(groupIds);

        setPrimaryAuthorType(rowData.getInt(DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE));

        for (final Style.UnderEach item : Style.UnderEach.values()) {
            setShowBooks(item, rowData.getBoolean(item.getDbKey()));
        }

        setExpansionLevel(rowData.getInt(DBKey.STYLE_EXP_LEVEL));
        setGroupRowUsesPreferredHeight(rowData.getBoolean(DBKey.STYLE_ROW_USES_PREF_HEIGHT));

        setSortAuthorByGivenName(rowData.getBoolean(DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME));
        setShowAuthorByGivenName(rowData.getBoolean(DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME));

        setTextScale(rowData.getInt(DBKey.STYLE_TEXT_SCALE));
        setCoverScale(rowData.getInt(DBKey.STYLE_COVER_SCALE));

        setHeaderFieldVisibilityValue(rowData.getInt(DBKey.STYLE_LIST_HEADER));

        getFieldVisibility(Screen.List)
                .setValue(rowData.getLong(DBKey.STYLE_BOOK_LEVEL_FIELDS_VISIBILITY));
        getFieldVisibility(Screen.Detail)
                .setValue(rowData.getLong(DBKey.STYLE_DETAILS_SHOW_FIELDS));

        setBookLevelFieldsOrderBy(
                StyleCoder.decodeBookLevelFieldsOrderBy(
                        rowData.getString(DBKey.STYLE_BOOK_LEVEL_FIELDS_ORDER_BY)));
    }

    /**
     * Copy constructor. Used for cloning.
     * <p>
     * The id and uuid are passed in to allow testing,
     * see {@link #clone(Context)}.
     *
     * @param context Current context
     * @param id      for the new style
     * @param uuid    for the new style
     * @param style   to clone
     */
    protected UserStyle(@NonNull final Context context,
                        final long id,
                        @NonNull final String uuid,
                        @NonNull final BaseStyle style) {
        super(uuid, id, style);
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
    public static UserStyle createFromDatabase(@NonNull final DataHolder rowData) {
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
    public static UserStyle createFromImport(@NonNull final String uuid) {
        return new UserStyle(uuid);
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
    public boolean isUserDefined() {
        return true;
    }

    @Override
    @NonNull
    public String getTypeDescription(@NonNull final Context context) {
        return context.getString(R.string.style_type_user_defined);
    }

    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        return getName();
    }
}
