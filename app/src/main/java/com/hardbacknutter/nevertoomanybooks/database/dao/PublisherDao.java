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
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

@SuppressWarnings("UnusedReturnValue")
public interface PublisherDao
        extends EntityBookLinksDao<Publisher> {

    /**
     * Get a unique list of all publisher names.
     *
     * @return The list
     */
    @NonNull
    List<String> getNames();

    /**
     * Get all publishers; mainly for the purpose of exports.
     *
     * @return Cursor over all publishers
     */
    @NonNull
    Cursor fetchAll();

    /**
     * Remove duplicates. We keep the first occurrence.
     *
     * @param context        Current context
     * @param list           List to clean up
     * @param localeSupplier deferred supplier for a {@link Locale}.
     *
     * @return {@code true} if the list was modified.
     */
    boolean pruneList(@NonNull Context context,
                      @NonNull Collection<Publisher> list,
                      @NonNull Function<Publisher, Locale> localeSupplier);
}
