/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.entities.merging;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Locale;

@FunctionalInterface
public interface IdFixer<T> {

    /**
     * Find a {@link T} by using the <strong>name</strong> fields.
     * If found, updates <strong>ONLY</strong> the id with the one found in the database.
     * <p>
     * If the item has child items, then implementations must propagate the call.
     *
     * @param context Current context
     * @param item    to update
     * @param locale  Current Locale
     */
    void fixId(@NonNull Context context,
               @NonNull T item,
               @NonNull Locale locale);
}
