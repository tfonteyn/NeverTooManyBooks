/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;

public interface StyleDao {

    /**
     * Get the id of a {@link ListStyle} with matching UUID.
     *
     * @param uuid UUID of the style to find
     *
     * @return id
     */
    long getStyleIdByUuid(@NonNull String uuid);

    /**
     * Get the user-defined Styles.
     *
     * @param context Current context
     *
     * @return an ordered Map of styles
     */
    @NonNull
    Map<String, UserStyle> getUserStyles(@NonNull Context context);

    /**
     * Get the builtin Styles.
     *
     * @param context Current context
     *
     * @return an ordered Map of styles
     */
    @NonNull
    Map<String, BuiltinStyle> getBuiltinStyles(@NonNull Context context);

    /**
     * Create a new {@link ListStyle}.
     *
     * @param style to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row, or {@code -1} if an error occurred
     */
    long insert(@NonNull ListStyle style);

    /**
     * Update a {@link ListStyle}.
     *
     * @param style to update
     *
     * @return {@code true} for success.
     */
    boolean update(@NonNull ListStyle style);

    /**
     * Delete a {@link ListStyle}.
     * Cleans up {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE} as well.
     *
     * @param style to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull ListStyle style);

    /**
     * Purge Booklist node state data for the given Style.<br>
     * Called when a style is deleted or manually from the Styles management context menu.
     *
     * @param styleId to purge
     */
    void purgeNodeStatesByStyle(long styleId);

}
