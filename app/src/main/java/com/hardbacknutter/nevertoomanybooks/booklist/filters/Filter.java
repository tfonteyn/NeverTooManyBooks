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

package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A generic SQL expression filter for use in {@code WHERE} clauses.
 */
@FunctionalInterface
public interface Filter {

    /**
     * A Filter must implement this method and return a valid WHERE clause expression.
     *
     * @param context Current context
     *
     * @return filter SQL expression, or undefined if not active.
     */
    @Nullable
    String getExpression(@NonNull Context context);

    /**
     * Check if a filter is active / should be applied.
     *
     * @param context Current context
     *
     * @return {@code true} if this filter is active.
     */
    default boolean isActive(@NonNull final Context context) {
        final String expression = getExpression(context);
        return expression != null && !expression.isEmpty();
    }
}
