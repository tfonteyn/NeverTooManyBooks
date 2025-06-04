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

package com.hardbacknutter.nevertoomanybooks.booklist.filters;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Optional;

/**
 * A generic SQL expression filter for use in {@code WHERE} clauses.
 */
@FunctionalInterface
public interface Filter {

    /**
     * A Filter must implement this method and return a valid WHERE clause expression.
     *
     * @return filter SQL expression, or undefined if {@link #isActive()} returns {@code false}.
     */
    @Nullable
    String getExpression();

    /**
     * If the expression requires a JOIN, this method should provide
     * the expression to join with.
     * <ol>
     * <li>The name of the FIRST join table.
     *     <br/>This is used as a key to eliminate duplicates from the BoB grouping.</li>
     * <li>An SQL JOIN expression; must start with {@code TBL_BOOKS}</li>
     * </ol>
     *
     * URGENT: getJoinExpression works but the implementation is rather awkward, needs refactoring
     *
     * @return table name + expression
     */
    @NonNull
    default Optional<Pair<String, String>> getJoinExpression() {
        return Optional.empty();
    }

    /**
     * Check if a filter is active / should be applied.
     *
     * @return {@code true} if this filter is active.
     */
    default boolean isActive() {
        final String expression = getExpression();
        return expression != null && !expression.isEmpty();
    }
}
