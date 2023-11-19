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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.core.database.DaoWriteException;

public interface StyleDao {

    /**
     * Get the id of a {@link Style} with matching UUID.
     *
     * @param uuid UUID of the style to find
     *
     * @return id or {@code 0} if not found
     */
    @IntRange(from = 0)
    long getStyleIdByUuid(@NonNull String uuid);

    /**
     * Get the user-defined Styles.
     *
     * @return an ordered Map of styles
     */
    @NonNull
    Map<String, Style> getUserStyles();

    /**
     * Get the builtin Styles.
     *
     * @return an ordered Map of styles
     */
    @NonNull
    Map<String, Style> getBuiltinStyles();

    /**
     * Get the global/defaults Style.
     *
     * @return the instance
     */
    @NonNull
    Style getGlobalStyle();

    /**
     * Create a new {@link UserStyle}.
     *
     * @param context Current context
     * @param style   to insert. Will be updated with the id.
     *
     * @return the row id of the newly inserted row
     *
     * @throws DaoWriteException on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull Context context,
                @NonNull Style style)
            throws DaoWriteException;

    /**
     * Update the given {@link Style}.
     *
     * @param context Current context
     * @param style   to update
     *
     * @throws DaoWriteException on failure
     */
    void update(@NonNull Context context,
                @NonNull Style style)
            throws DaoWriteException;

    /**
     * Delete the given {@link Style}.
     *
     * @param style to delete
     *
     * @return {@code true} if a row was deleted
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean delete(@NonNull Style style);

    /**
     * Purge book list node state data for the given {@link Style}.
     * <p>
     * Called when a {@link Style} is deleted or manually from the
     * {@link Style} management context menu.
     *
     * @param style to purge
     *
     * @throws DaoWriteException on failure
     */
    void purgeNodeStates(@NonNull Style style)
            throws DaoWriteException;

}
